package wust.core.aws

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
