package wust.webApp.views

import wust.facades.googleanalytics.GoogleAnalytics
import wust.facades.jsSha256.Sha256
import monix.eval.Task
import org.scalajs.dom
import org.scalajs.dom.FormData
import org.scalajs.dom.raw.XMLHttpRequest
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._
import wust.api.{ApiEvent, AuthUser, FileUploadConfiguration}
import wust.graph._
import wust.ids._
import wust.webApp.Client
import wust.webApp.jsdom.FileReaderOps
import wust.webApp.state.{GlobalState, UploadingFile}
import wust.webApp.DebugOnly

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import wust.facades.canvasImageUploader._

object AWS {
  private def rotateImageByExifOrientation(file:dom.File):Future[dom.Blob] = {
    val uploader = new CanvasImageUploader(new CanvasImageUploaderOptions {
      maxSize = 10000
      jpegQuality = 0.85
    })

    val canvas = dom.document.createElement("canvas")
    val p = Promise[dom.Blob]
    uploader.readImageToCanvas(file, canvas, { () =>
      uploader.saveCanvasToImageData(canvas)
      p.success(uploader.getImageData())
    })
    p.future
  }


  final case class UploadableFile(file: dom.File, dataUrl: String, uploadKey: Task[Option[String]])

  def upload(originalFile: dom.File): Either[String, UploadableFile] = { // TODO: return either and propagate error message to form error instead of toast
    GlobalState.user.now match {
      case _: AuthUser.Real => ()
      case _ =>
        return Left(s"You need to register an account before you can upload anything.")
    }

    if(originalFile.size > FileUploadConfiguration.maxUploadBytesPerFile) {
      GoogleAnalytics.sendEvent("AWS-upload", "file-size-limit", label = "MB", value = (originalFile.size / 1024 / 1024).toInt)
      return Left(s"The file '${originalFile.name}' is bigger than the allowed limit of ${FileUploadConfiguration.maxUploadBytesPerFile / 1024 / 1024} MB.")
    }



    val uploadedKey = Task.deferFuture {
      val resultFile:Future[dom.Blob] = if(originalFile.`type` == "image/jpeg") rotateImageByExifOrientation(originalFile) else Future.successful(originalFile)
      resultFile.flatMap {file =>
        DebugOnly{dom.console.log(dom.URL.createObjectURL(file))}
        val config = for {
          fileContent <- FileReaderOps.readAsText(file) // TODO: is that even correct for the content of binary files?
          fileContentDigest = Sha256.sha256(fileContent)
          config <- Client.api.fileUploadConfiguration(fileContentDigest, file.size.toInt, originalFile.name, file.`type`)
        } yield config

        val promise = Promise[Option[String]]
        config.onComplete {
          case Success(config: FileUploadConfiguration.UploadToken) =>
            val xhr = new XMLHttpRequest()
            xhr.open("POST", config.baseUrl, true)

            xhr.onload = { _ =>
              if (xhr.status == 200 || xhr.status == 201 || xhr.status == 204) {
                promise success Some(config.key)
                UI.toast("File was Successfully uploaded", level = UI.ToastLevel.Success)
              } else {
                promise success None
                UI.toast("Failed to upload file", level = UI.ToastLevel.Error)
              }
            }
            xhr.onerror = { e =>
              promise success None
              UI.toast("Error while uploading file", level = UI.ToastLevel.Error)
            }

            val formData = new FormData()
            formData.append("key", s"${config.key}")
            formData.append("x-amz-credential", config.credential)
            formData.append("x-amz-algorithm", config.algorithm)
            formData.append("cache-control", config.cacheControl)
            formData.append("content-type", file.`type`)
            formData.append("content-disposition", config.contentDisposition)
            formData.append("acl", config.acl)
            formData.append("policy", config.policyBase64)
            formData.append("x-amz-signature", config.signature)
            config.sessionToken.foreach(sessionToken => formData.append("x-amz-security-token", sessionToken))
            formData.append("x-amz-date", config.date)
            formData.append("file", file)

            xhr.send(formData)

          case Success(FileUploadConfiguration.KeyExists(key)) =>
            UI.toast("File was Successfully uploaded", level = UI.ToastLevel.Success)
            promise success Some(key)
            GoogleAnalytics.sendEvent("AWS-upload", "success", label = "MB", value = (file.size / 1024 / 1024).toInt)
          case Success(FileUploadConfiguration.QuotaExceeded) =>
            promise success None
            UI.toast(s"Sorry, you have exceeded your file-upload quota. You only have ${FileUploadConfiguration.maxUploadBytesPerUser / 1024 / 1024} MB. Click here to check your uploaded files in your user settings.", click = () => GlobalState.urlConfig.update(_.focus(View.UserSettings)))
            GoogleAnalytics.sendEvent("AWS-upload", "total-size-limit")
          case Success(FileUploadConfiguration.ServiceUnavailable) =>
            promise success None
            UI.toast("Sorry, the file-upload service is currently unavailable. Please try again later!")
            GoogleAnalytics.sendEvent("AWS-upload", "aws-unavailable")
          case Success(FileUploadConfiguration.Rejected(reason)) =>
            promise success None
            UI.toast(reason)
          case Failure(t)                       =>
            promise success None
            scribe.warn("Cannot get file upload configuration", t)
            UI.toast("Sorry, the file-upload service is currently unreachable. Please try again later!")
            GoogleAnalytics.sendEvent("AWS-upload", "backend-unavailable")
        }

        promise.future
      }
    }

    val url = dom.URL.createObjectURL(originalFile)
    Right(UploadableFile(file = originalFile, dataUrl = url, uploadKey = uploadedKey))

  }

  def uploadFileAndCreateNode(uploadFile: AWS.UploadableFile, extraChanges: NodeId => GraphChanges = _ => GraphChanges.empty): Future[Unit] = {

    val nodeId = NodeId.fresh
    val (initialNodeData, observableNodeData) = uploadFileAndCreateNodeDataFull( uploadFile, Some(nodeId))
    val initialNode = Node.Content(nodeId, initialNodeData, NodeRole.Neutral)

    def toGraphChanges(node: Node) = GraphChanges.addNode(node).merge(extraChanges(node.id))

    val initialChanges = toGraphChanges(initialNode)
    val observableChanges = observableNodeData.map(data => toGraphChanges(initialNode.copy(data = data)))

    GlobalState.eventProcessor.localEvents.onNext(ApiEvent.NewGraphChanges.forPrivate(GlobalState.user.now.toNode, initialChanges.withAuthor(GlobalState.user.now.id)))
    observableChanges.runToFuture.flatMap { e => GlobalState.submitChanges(e).map(_ => ()) }
  }

  def uploadFileAndCreateNodeData(uploadFile: AWS.UploadableFile): Task[NodeData.File] = {
    uploadFileAndCreateNodeDataFull( uploadFile, None)._2
  }

  def uploadFileAndCreateNodeDataFull(uploadFile: AWS.UploadableFile, nodeId: Option[NodeId] = None): (NodeData.File, Task[NodeData.File]) = {

    val rawFileNodeData = NodeData.File(key = "", fileName = uploadFile.file.name, contentType = uploadFile.file.`type`) // TODO: empty string for signaling pending fileupload

    rawFileNodeData -> Task.deferFuture {
      //TODO: there is probably a better way to implement this...
      val observableChanges = Promise[NodeData.File]
      var uploadTask: Task[Unit] = null
      uploadTask = Task.defer{
        nodeId.foreach(nodeId => GlobalState.uploadingFiles.update(_ ++ Map(nodeId -> UploadingFile.Waiting(uploadFile.dataUrl))))

        uploadFile.uploadKey.map {
          case Some(key) =>
            nodeId.foreach(nodeId => GlobalState.uploadingFiles.update(_ - nodeId))
            observableChanges.trySuccess(rawFileNodeData.copy(key = key))
            ()
          case None      =>
            nodeId.foreach(nodeId => GlobalState.uploadingFiles.update(_ ++ Map(nodeId -> UploadingFile.Error(uploadFile.dataUrl, uploadTask))))
            ()
        }
      }

      uploadTask.runAsyncAndForget

      observableChanges.future
    }
  }
}
