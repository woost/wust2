package wust.core.aws

import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Base64

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import monix.eval.Task
import wust.api.{FileUploadConfiguration, StaticFileUrl}
import wust.backend.config.{AwsConfig, ServerConfig}
import wust.ids.UserId

import scala.collection.mutable

class S3FileUploader(awsConfig: AwsConfig, serverConfig: ServerConfig) {
  import FileUploadConfiguration._

  private val awsCredentials = new BasicAWSCredentials(awsConfig.accessKey, awsConfig.secretKey)
  private val s3Client: AmazonS3Client = new AmazonS3Client(awsCredentials)

  private def s3GetUrl(bucketName: String) = s"https://s3.${awsConfig.region}.amazonaws.com/$bucketName"
  private def s3PostUrl(bucketName: String) = s"https://$bucketName.s3-${awsConfig.region}.amazonaws.com/"

  private def requireSaneKey(key: String): Unit = {
    require(key.matches("^[A-Fa-f0-9]+$"), "invalid file key") // our clients send the sha-256 of the content in hex. will be prefixed by hash of user-id
  }

  def getFileDownloadBaseUrl: Task[StaticFileUrl] = Task.pure {
    StaticFileUrl(s3GetUrl(awsConfig.uploadBucketName))
  }

  def getAllObjectSummariesForUser(userId: UserId): Task[Seq[S3ObjectSummary]] = {
    val keyPrefix = getKeyPrefixForUser(userId)
    getAllObjectSummaries(keyPrefix)
  }

  def deleteFileUpload(userId: UserId, fileKey: String): Task[Boolean] = {
    requireSaneKey(fileKey)

    val keyPrefix = getKeyPrefixForUser(userId)
    val key = keyPrefix + "/" + fileKey
    deleteKeyInS3Bucket(key).map(_ => true)
  }

  def getFileUploadConfiguration(userId: UserId, fileKey: String, fileName: String, fileSize: Int, fileContentType: String): Task[FileUploadConfiguration] = {
    // SANITIZE user input, we use these strings in the json policy document for the signed post request to aws.
    // !!!!!really NEVER ever allow quotes or any possible encoding of breaking out of quotes in json!!!!!
    requireSaneKey(fileKey)
    require(fileContentType.matches("^[\\w\\.+\\-]+/[\\w\\.+\\-]+$"), "invalid file content type") // allow only potential content types: "<word>/<word>"
    require(fileSize >= 0, "File size must be greater than or equal to zero")
    require(fileSize <= maxUploadBytesPerFile, "File size must be less than or equal to max upload bytes per file")

    // filenames do not have any restriction, we allow the most common ones. this is just about the name of the file when clicking download.
    // the url contains a hash, so browsers cannot infer a nice filename themselves. but we can help with a content-disposition header.
    // if we do not like the filename, we do not want to fail as then unforeseen filenames cannot be uploaded. but we cannot trust this filename string.
    // so we fall-back to providing no filename in the content-disposition header. file will then be named like the resource in the url (sha256 of content).
    // we still enforce attachment, which will make the browser download the content instead of opening it in a tab.
    // TODO: is this to defensive? what is a safe sanitizer for not-breaking out of json values?
    val fileContentDisposition = if (fileName.matches("^[\\w\\.+\\-\\s()\\[\\]]+$")) s"""attachment; filename="$fileName"""" else "attachment"

    val keyPrefix = getKeyPrefixForUser(userId)
    val key = keyPrefix + "/" + fileKey

    //TODO: better maybe just store a list of uploads in our db or in aws db with s3-events
    getAllObjectSummaries(keyPrefix).map { objects =>

      var alreadyUploadedBytes: Long = 0
      var keyAlreadyExists: Boolean = false
      //TODO only get as many summaries as needed
      objects.foreach { obj =>
        if(obj.getKey == key) keyAlreadyExists = true

        alreadyUploadedBytes += obj.getSize
      }

      if (keyAlreadyExists) FileUploadConfiguration.KeyExists(key) // TODO: maybe include type/filename in key
      else {
        val freeUploadBytes = maxUploadBytesPerUser - alreadyUploadedBytes

        // TODO: this can be attacked to upload more data than allowed. just issue a lot of presigned urls via api calls
        // then you have two minute time frame to upload maxUploadBytesPerFile * numberOfIssuedPresignUrls
        // or we cached issued tokens in the last two minutes
        if (freeUploadBytes < fileSize) FileUploadConfiguration.QuotaExceeded
        else getPostConfiguration(key, fileSize = fileSize, fileContentType = fileContentType, fileContentDisposition = fileContentDisposition, validSeconds = 2 * 60) // 2 minutes
      }
    }
  }

  // https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTForms.html
  // https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-UsingHTTPPOST.html
  // https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTConstructPolicy.html
  private def getPostConfiguration(key: String, fileSize: Int, fileContentType: String, fileContentDisposition: String, validSeconds: Int): FileUploadConfiguration = {
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val expiryTime = now.plusSeconds(validSeconds)
    val amzDateString = DateTimeFormatter.ofPattern("yyyyMMdd").format(now)
    val amzDateTimeString = DateTimeFormatter.ofPattern("yyyyMMdd'T'000000'Z'").format(now)
    val amzCredential = s"${awsConfig.accessKey}/$amzDateString/${awsConfig.region}/s3/aws4_request"
    val amzAlgorithm = "AWS4-HMAC-SHA256"
    val fileCacheControl = s"max-age=$cacheMaxAgeSeconds"

    // this policy document defines what kind of post request we allow to s3.
    // we restrict the bucket to our upload bucket.
    // we allow only an upload to one specified key (one s3-object).
    // we enforce a content-type, browser know how to display it -- will be stored by s3 as a response header when getting that object from s3
    // we enforce a content-disposition, browsers should download the file as filename -- will be stored by s3 as a response header when getting that object from s3
    // we only allow an upload for the specified file size (+-1) -- aws checks size
    val acl = "private"
    val policy_document =
      s"""
         |{
         |  "expiration": "$expiryTime",
         |  "conditions": [
         |    {"acl": "$acl"},
         |    {"bucket": "${awsConfig.uploadBucketName}"},
         |    ["eq", "$$key", "$key"],
         |    {"x-amz-date": "$amzDateTimeString"},
         |    {"x-amz-algorithm": "$amzAlgorithm"},
         |    {"x-amz-credential": "$amzCredential"},
         |    {"cache-control": "$fileCacheControl"},
         |    {"content-type": "$fileContentType"},
         |    {"content-disposition": '$fileContentDisposition' },
         |    ["content-length-range", ${fileSize - 1}, ${fileSize + 1}]
         |  ]
         |}
      """.stripMargin

    val policy = Base64.getEncoder.encodeToString(policy_document.getBytes("UTF-8"))


    val signingKey = AwsSignature.getSignatureKey(key = awsConfig.secretKey, dateStamp = amzDateString, regionName = awsConfig.region, serviceName = "s3")
    val signedPolicy = AwsSignature.HmacSHA256(data = policy, key = signingKey)
    val signature = javax.xml.bind.DatatypeConverter.printHexBinary(signedPolicy).toLowerCase

    FileUploadConfiguration.UploadToken(baseUrl = s3PostUrl(awsConfig.uploadBucketName), credential = amzCredential, policyBase64 = policy, signature = signature, validSeconds = validSeconds, acl = acl, key = key, algorithm = amzAlgorithm, date = amzDateTimeString, contentDisposition = fileContentDisposition, cacheControl = fileCacheControl)
  }

  private def getAllObjectSummaries(keyPrefix: String): Task[Seq[S3ObjectSummary]] = Task {
    val request = new ListObjectsV2Request()
      .withBucketName(awsConfig.uploadBucketName)
      .withPrefix(keyPrefix)

    val allSummaries = new mutable.ArrayBuffer[S3ObjectSummary]
    var result: ListObjectsV2Result = null
    do {
      if(result != null)
        request.setContinuationToken(result.getContinuationToken)

      result = s3Client.listObjectsV2(request)
      result.getObjectSummaries.forEach { summary =>
        allSummaries += summary
      }
    } while(result.isTruncated && result.getContinuationToken != null)

    allSummaries
  }

  def deleteKeyInS3Bucket(key: String): Task[Unit] = Task {
    val request = new DeleteObjectRequest(awsConfig.uploadBucketName, key)
    s3Client.deleteObject(request)
  }

  private def getKeyPrefixForUser(userId: UserId): String = {
    // hash and url encode the userid to get a unique key prefix for each user that can we check for storagesize
    Base64.getUrlEncoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(userId.toCuidString.getBytes("UTF-8")))
  }
}

// below is code for the aws-sdk-java version 2! We cannot use it yet, because it requires an incompatible netty version which conflicts with the one required by postgres-aync.

//import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentials, AwsCredentialsProvider, DefaultCredentialsProvider}
//import software.amazon.awssdk.auth.signer.AwsS3V4Signer
//import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams
//import software.amazon.awssdk.http.SdkHttpFullRequest
//import software.amazon.awssdk.http.SdkHttpMethod
//import software.amazon.awssdk.regions.Region
//import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
//import java.security.MessageDigest
//import java.time.Instant
//
//import monix.eval.Task
//import software.amazon.awssdk.services.lexruntime.model.PostContentRequest
//import software.amazon.awssdk.services.s3.S3Client
//import wust.api.{FileUploadConfiguration, LimitedFileUrl, StaticFileUrl}
//
//import scala.concurrent.duration._
//import wust.backend.config.{AwsConfig, ServerConfig}
//import wust.ids.UserId
//
//class S3FileUploader(awsConfig: AwsConfig, serverConfig: ServerConfig) {
//
//  private val awsCredentials = AwsBasicCredentials.create(awsConfig.accessKey, awsConfig.secretKey)
//  private val s3Client: S3Client = S3Client.create()
//  private val s3HostName = s"s3.${awsConfig.region}.amazonaws.com"
//  private val maxUploadBytesPerFile = 5 * 1024 * 1024 // 5 mb
//  private val maxUploadBytesPerUser = 100 * 1024 * 1024 // 100 mb
//
//  def getFileDownloadBaseUrl: Task[StaticFileUrl] = Task.pure {
//    StaticFileUrl(s"https://$s3HostName/${awsConfig.uploadBucketName}/")
//  }
//
//  def getFileUploadConfiguration(userId: UserId): Task[FileUploadConfiguration] = Task {
//    val keyPrefix = getS3FolderForUser(userId)
//
//    getPostConfiguration(keyPrefix = keyPrefix, validSeconds = 5 * 60) // 5 minutes
//  }
//
//  def getFileUploadUrl(userId: UserId, keyPart: String): Task[Option[LimitedFileUrl]] = Task {
//
//    val keyPrefix = getS3FolderForUser(userId)
//    val fileKey = s"$keyPrefix/$keyPart"
//
//    //TODO: better maybe just store a list of uploads?
//    val request = ListObjectsV2Request.builder()
//            .bucket(awsConfig.uploadBucketName)
//            .prefix(keyPrefix)
//            .build()
//
//    val alreadyUploadedBytes = s3Client.listObjectsV2Paginator(request).stream().mapToLong { response =>
//      val objects = response.contents()
//      var bytes = 0
//      objects.forEach { obj =>
//        if (obj.key() != fileKey) // if it is equal, we want to override that file and do not count it for the size
//          bytes += obj.size()
//      }
//
//      bytes
//    }.sum()
//
//    // TODO: this can be attacked to upload more data than allowed. just issue a lot of presigned urls via api calls
//    // then you have five minute time frame to upload maxUploadBytesPerFile * numberOfIssuedPresignUrls
//    val freeUploadBytes = maxUploadBytesPerUser - alreadyUploadedBytes
//    if (freeUploadBytes <= maxUploadBytesPerFile) None
//    else Some(getPresignedUrl(fileKey, SdkHttpMethod.PUT, validSeconds = 5 * 60)) // 5 minutes
//  }
//
//  private def getS3FolderForUser(userId: UserId): String = {
//    MessageDigest.getInstance("SHA-256").digest(userId.toCuidString.getBytes("UTF-8")).toString
//  }
//
//  // Taken from: https://aws.amazon.com/articles/browser-uploads-to-s3-using-html-post-forms/
//  private def getPostConfiguration(keyPrefix: String, validSeconds: Int): FileUploadConfiguration = {
//    import sun.misc.BASE64Encoder
//    import javax.crypto.Mac
//    import javax.crypto.spec.SecretKeySpec
//
//    //FIXME: correct
//    val policy_document =
//      s"""
//        |{"expiration": "2019-01-01T00:00:00Z",
//        |  "conditions": [
//        |    {"bucket": "${awsConfig.uploadBucketName}"},
//        |    ["starts-with", "$$key", "$keyPrefix/"],
//        |    {"acl": "private"},
//        |    {"success_action_redirect": "https://${serverConfig.host}/"},
//        |    ["starts-with", "$$Content-Type", ""],
//        |    ["content-length-range", 0, $maxUploadBytesPerFile]
//        |  ]
//        |}
//      """.stripMargin
//
//    val policy = new BASE64Encoder().encode(
//      policy_document.getBytes("UTF-8")).replaceAll("\n","").replaceAll("\r","")
//
//    val hmac = Mac.getInstance("HmacSHA1")
//    hmac.init(new SecretKeySpec(
//      awsConfig.secretKey.getBytes("UTF-8"), "HmacSHA1"))
//    val signature = new BASE64Encoder().encode(
//      hmac.doFinal(policy.getBytes("UTF-8")))
//      .replaceAll("\n", "")
//
//
//    FileUploadConfiguration(baseUrl = s3HostName, awsAccessKey = awsConfig.accessKey, policyBase64 = policy, signature = signature)
//  }
//
//  private def getPresignedUrl(key: String, method: SdkHttpMethod, validSeconds: Int): LimitedFileUrl = {
//    val params = Aws4PresignerParams.builder()
//            .expirationTime(Instant.now.plusSeconds(validSeconds))
//            .awsCredentials(awsCredentials)
//            .signingName("s3")
//            .signingRegion(Region.of(awsConfig.region))
//            .build()
//
//    val request = SdkHttpFullRequest.builder()
//            .encodedPath(key)
//            .host(s3HostName)
//            .method(method)
//            .protocol("https")
////      .appendHeader("content-length", "$contentLength")
//            .build()
//
//    val result: SdkHttpFullRequest = AwsS3V4Signer.create().presign(request, params)
//
//    LimitedFileUrl(result.getUri.toString, validSeconds = validSeconds)
//  }
//}

// https://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html
object AwsSignature {

  import javax.crypto.Mac
  import javax.crypto.spec.SecretKeySpec

  def HmacSHA256(data: String, key: Array[Byte]): Array[Byte] = {
    val algorithm = "HmacSHA256"
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data.getBytes("UTF8"))
  }

  def getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): Array[Byte] = {
    val kSecret = ("AWS4" + key).getBytes("UTF8")
    val kDate = HmacSHA256(dateStamp, kSecret)
    val kRegion = HmacSHA256(regionName, kDate)
    val kService = HmacSHA256(serviceName, kRegion)
    val kSigning = HmacSHA256("aws4_request", kService)
    kSigning
  }
}
