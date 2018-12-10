package jquery

import org.scalajs.dom.raw.HTMLElement
import fomanticui.JQuerySelectionWithFomanticUI

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

@js.native
@JSGlobalScope
object JQuery extends js.Object {
  def `$`(elem:HTMLElement):JQuerySelection = js.native

  def alert(message: String): Unit = js.native
}

@js.native
trait JQuerySelection extends js.Object with JQuerySelectionWithFomanticUI {
  def text():String = js.native
}

