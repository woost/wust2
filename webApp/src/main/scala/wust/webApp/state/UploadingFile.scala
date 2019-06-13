package wust.webApp.state

import monix.eval.Task

sealed trait UploadingFile
object UploadingFile {
  final case class Waiting(dataUrl: String) extends UploadingFile
  final case class Error(dataUrl: String, retry: Task[Unit]) extends UploadingFile
}

