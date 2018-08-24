package semanticUi

import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobalScope
object JQuery extends js.Object {
  def `$`(elem:HTMLElement):JQuerySelection = js.native

  def alert(message: String): Unit = js.native
}

@js.native
trait JQuerySelection extends js.Object with JQuerySelectionWithDropdown with JQuerySelectionWithModal {
}
@js.native
trait JQuerySelectionWithModal extends js.Object {
  def modal(behavior: String*): Unit = js.native
  def modal(options: ModalOptions): JQuerySelectionWithModal = js.native
}

@js.native
trait JQuerySelectionWithDropdown extends js.Object {
  def dropdown():Unit = js.native
}


