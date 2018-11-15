package fomanticui

import scala.scalajs.js

@js.native
// fomantic ui is imported globally
trait JQuerySelectionWithFomanticUI extends js.Object {
  def dropdown(arg: String = ???):Unit = js.native

  def modal(behavior: String*): Unit = js.native
  def modal(options: ModalOptions): String = js.native

  def search(arg: SearchOptions):Unit = js.native
  def search(arg: String, arg1: Any = ???, arg2: Any = ???): String = js.native
}

trait DimmerOptions extends js.Object {
  var opacity: js.UndefOr[String] = js.undefined
}

trait ModalOptions extends js.Object {
  var blurring: js.UndefOr[Boolean] = js.undefined
  var dimmerSettings: js.UndefOr[DimmerOptions] = js.undefined
}

trait SearchOptions extends js.Object {
  var `type`: js.UndefOr[String] = js.undefined
  var source: js.UndefOr[js.Array[SearchSourceEntry]] = js.undefined
  var cache: js.UndefOr[Boolean] = js.undefined
  var searchOnFocus: js.UndefOr[Boolean] = js.undefined
  var minCharacters: js.UndefOr[Int] = js.undefined
  var searchFields: js.UndefOr[js.Array[String]] = js.undefined
  var fullTextSearch: js.UndefOr[Boolean] = js.undefined
  var onSelect: js.UndefOr[js.Function2[SearchSourceEntry, js.Array[SearchSourceEntry], Boolean]] = js.undefined
}

trait SearchSourceEntry extends js.Object {
  var title: js.UndefOr[String] = js.undefined
  var description: js.UndefOr[String] = js.undefined
  var category: js.UndefOr[String] = js.undefined

  var data: js.UndefOr[js.Any] = js.undefined
}
