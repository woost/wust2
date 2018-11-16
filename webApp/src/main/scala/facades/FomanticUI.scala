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

  def toast(options: ToastOptions): Unit = js.native
}

trait ToastOptions extends js.Object {
  var title: js.UndefOr[String] = js.undefined
  var message: js.UndefOr[String] = js.undefined
  var position: js.UndefOr[String] = js.undefined
  var `class`: js.UndefOr[String] = js.undefined
  var displayTime: js.UndefOr[Double] = js.undefined
  var showIcon: js.UndefOr[Boolean] = js.undefined
  var closeIcon: js.UndefOr[Boolean] = js.undefined
  var showProgess: js.UndefOr[Boolean] = js.undefined
  var progressUp: js.UndefOr[Boolean] = js.undefined
  var compact: js.UndefOr[Boolean] = js.undefined
  var opacity: js.UndefOr[Double] = js.undefined
  var newestOnTop: js.UndefOr[Boolean] = js.undefined
  var transition: js.UndefOr[ToastTransitionOptions] = js.undefined
  var debug: js.UndefOr[Boolean] = js.undefined

  var onShow: js.UndefOr[js.Function0[Boolean]] = js.undefined
  var onVisible: js.UndefOr[js.Function0[Unit]] = js.undefined
  var onClick: js.UndefOr[js.Function0[Unit]] = js.undefined
  var onHide: js.UndefOr[js.Function0[Boolean]] = js.undefined
  var onHidden: js.UndefOr[js.Function0[Unit]] = js.undefined
  var onRemove: js.UndefOr[js.Function0[Unit]] = js.undefined
}

trait ToastTransitionOptions extends js.Object {
  var showMethod: js.UndefOr[String] = js.undefined
  var showDuration: js.UndefOr[Double] = js.undefined
  var hideMethod: js.UndefOr[String] = js.undefined
  var hideDuration: js.UndefOr[Double] = js.undefined
  var closeEasing: js.UndefOr[String] = js.undefined
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
