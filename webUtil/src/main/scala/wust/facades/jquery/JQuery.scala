package wust.facades.jquery

import wust.facades.fomanticui.JQuerySelectionWithFomanticUI
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope
import scala.scalajs.js.annotation.JSBracketAccess
import scala.scalajs.js.annotation.JSName
import org.scalajs.dom

@js.native
@JSGlobalScope
object JQuery extends js.Object {
  def `$`(elem: HTMLElement): JQuerySelection = js.native
  def `$`(elem: String): JQuerySelection = js.native
  @JSName("$")
  def dollar: js.Dynamic = js.native

  def alert(message: String): Unit = js.native
}

@js.native
trait JQuerySelection extends js.Object with JQuerySelectionWithFomanticUI {
  def text(): String = js.native

  @JSBracketAccess
  def apply(index: Int): dom.Element = js.native
}
