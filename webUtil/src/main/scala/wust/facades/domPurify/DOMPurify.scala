package wust.facades.dompurify

import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.`|`
import scala.scalajs.js.annotation._

@js.native
@JSImport("dompurify", JSImport.Default)
object DOMPurify extends js.Object {
  def sanitize(dirty: String): String = js.native
  def sanitize(dirty: String|html.Element, cfg: DomPurifyConfig): String = js.native
  def setConfig(cfg: DomPurifyConfig): Unit = js.native
}

trait DomPurifyConfig extends js.Object {
}
