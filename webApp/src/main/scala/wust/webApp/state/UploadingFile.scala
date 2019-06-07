package wust.webApp.state

import monix.eval.Task

sealed trait UploadingFile
object UploadingFile {
  case class Waiting(dataUrl: String) extends UploadingFile
  case class Error(dataUrl: String, retry: Task[Unit]) extends UploadingFile
}

