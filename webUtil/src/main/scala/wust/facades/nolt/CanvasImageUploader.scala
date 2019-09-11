package wust.facades.canvasImageUploader

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom

@js.native
@JSImport("canvas-image-uploader", JSImport.Namespace)
class CanvasImageUploader(options: CanvasImageUploaderOptions) extends js.Object {
  def readImageToCanvas(file:dom.File, canvas:dom.Element, callback: js.Function0[Unit]):Unit = js.native
  def saveCanvasToImageData(canvas:dom.Element):Unit = js.native
  def getImageData():dom.Blob = js.native
}

trait CanvasImageUploaderOptions extends js.Object {
  var maxSize: js.UndefOr[Int] = js.undefined
  var jpegQuality: js.UndefOr[Double] = js.undefined
}

