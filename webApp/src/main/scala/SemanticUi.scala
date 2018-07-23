package semanticUi

import org.scalajs.dom.NodeList
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import scala.scalajs.js.`|`
import scala.scalajs.js.annotation._

import org.scalajs.dom
import org.scalajs.dom.raw.{NodeList}
import org.scalajs.dom.html

@js.native
@JSGlobalScope
object JQuery extends js.Object {
  def `$`(elem:HTMLElement):JQuerySelectionWithDropdown = js.native

  def alert(message: String): Unit = js.native
}

@js.native
trait JQuerySelectionWithDropdown extends js.Object {
  def dropdown():Unit = js.native
}


