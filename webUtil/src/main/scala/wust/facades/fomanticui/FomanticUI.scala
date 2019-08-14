package wust.facades.fomanticui

import wust.facades.jquery.JQuerySelection
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.|

@js.native // fomantic ui is imported globally
trait JQuerySelectionWithFomanticUI extends js.Object {
  def dropdown(arg: String = ???): Unit = js.native
  def dropdown(options: DropdownOptions): JQuerySelectionWithFomanticUI = js.native

  def modal(behavior: String): Unit = js.native
  def modal(options: ModalOptions): JQuerySelectionWithFomanticUI = js.native

  def search(arg: SearchOptions): Unit = js.native
  def search(arg: String, args: Any*): js.Any = js.native

  def progress(): Unit = js.native
  def progress(options: ProgressOptions): Unit = js.native
  def progress(behavior: String): Unit = js.native
  def progress(behavior: String, argumentOne: js.Any): Unit = js.native
  def progress(behavior: String, argumentOne: js.Any, argumentTwo: js.Any): Unit = js.native

  def toast(options: ToastOptions): Unit = js.native

  def popup(options: PopupOptions): JQuerySelectionWithFomanticUI = js.native
  def popup(args: String*): Unit = js.native

  def accordion(args: String*): Unit = js.native
  def accordion(args: AccordeonOptions): JQuerySelectionWithFomanticUI = js.native

  def sidebar(args: String*): Unit = js.native
  def sidebar(options: SidebarOptions): JQuerySelectionWithFomanticUI = js.native

  def tablesort(): Unit = js.native

  def on(eventName: String, f: js.Function): Unit = js.native

  def data(name: String): js.Any = js.native
}

trait TableSortInstance extends js.Object {
  var $table: JQuerySelection // The <table> being sorted.
  var index: Int // The column index of tablesort.$th (or null).
  var direction: String // The direction of the current sort, either 'asc' or 'desc' (or null if unsorted).
  def settings: js.Any // Settings for this instance (see below).

  def sort(th: JQuerySelection, direction: String): Unit
  def destroy(): Unit
}

trait DropdownEntry extends js.Object {
  var name: js.UndefOr[String] = js.undefined
  var value: js.UndefOr[String] = js.undefined
  var selected: js.UndefOr[Boolean] = js.undefined
}

trait AccordeonOptions extends js.Object {
  var collapsible: js.UndefOr[Boolean] = js.undefined
  var exclusive: js.UndefOr[Boolean] = js.undefined
  var duration: js.UndefOr[Int] = js.undefined
}

object AccordeonOptions {
  def apply(collapsible: Boolean, exclusive: Boolean, duration: Int): AccordeonOptions = {
    js.Dynamic.literal(
      collapsible = collapsible,
      exclusive = exclusive,
      duration = duration
    ).asInstanceOf[AccordeonOptions]
  }
}

trait DropdownOptions extends js.Object {
  var onShow: js.UndefOr[js.Function0[Boolean]] = js.undefined
  var onHide: js.UndefOr[js.Function0[Boolean]] = js.undefined

  var onChange: js.UndefOr[js.Function3[js.UndefOr[String], js.UndefOr[String], JQuerySelection, Unit]] = js.undefined
  var action: js.UndefOr[String] = js.undefined
  var values: js.UndefOr[js.Array[DropdownEntry]] = js.undefined

  var direction: js.UndefOr[String] = js.undefined
}

trait PopupOptions extends js.Object {
  var on: js.UndefOr[String] = js.undefined
  var target: js.UndefOr[String | JQuerySelection] = js.undefined
  var inline: js.UndefOr[Boolean] = js.undefined
  var hoverable: js.UndefOr[Boolean] = js.undefined
  var position: js.UndefOr[String] = js.undefined
  var variation: js.UndefOr[String] = js.undefined
  var content: js.UndefOr[String] = js.undefined
  var title: js.UndefOr[String] = js.undefined
  var html: js.UndefOr[String | dom.Element] = js.undefined
  var scrollContext: js.UndefOr[String] = js.undefined
  var exclusive: js.UndefOr[Boolean] = js.undefined
  var hideOnScroll: js.UndefOr[Boolean] = js.undefined
}

trait ToastOptions extends js.Object {
  var title: js.UndefOr[String] = js.undefined
  var message: js.UndefOr[String] = js.undefined
  var position: js.UndefOr[String] = js.undefined
  var `class`: js.UndefOr[String] = js.undefined
  var className: js.UndefOr[ToastClassNameOptions] = js.undefined
  var displayTime: js.UndefOr[Double | Int] = js.undefined
  var showIcon: js.UndefOr[Boolean | String] = js.undefined
  var closeIcon: js.UndefOr[Boolean] = js.undefined
  var showProgress: js.UndefOr[Boolean | String] = js.undefined
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

trait ToastClassNameOptions extends js.Object {
  var toast: js.UndefOr[String] = js.undefined
  var title: js.UndefOr[String] = js.undefined
  var progress: js.UndefOr[String] = js.undefined
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
  var detachable: js.UndefOr[Boolean] = js.undefined
  var blurring: js.UndefOr[Boolean] = js.undefined
  var dimmerSettings: js.UndefOr[DimmerOptions] = js.undefined
  var onHide: js.UndefOr[js.Function0[Boolean]] = js.undefined
  var onDeny: js.UndefOr[js.Function0[Boolean]] = js.undefined
  var onApprove: js.UndefOr[js.Function0[Boolean]] = js.undefined
}

trait SearchOptions extends js.Object {
  var `type`: js.UndefOr[String] = js.undefined
  var source: js.UndefOr[js.Array[SearchSourceEntry]] = js.undefined
  var cache: js.UndefOr[Boolean] = js.undefined
  var searchOnFocus: js.UndefOr[Boolean] = js.undefined
  var showNoResults: js.UndefOr[Boolean] = js.undefined
  var selectFirstResult: js.UndefOr[Boolean] = js.undefined
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

trait SearchResults extends js.Object {
  def results: js.Array[SearchSourceEntry]
}

trait ProgressOptions extends js.Object {
  var showActivity: js.UndefOr[Boolean] = js.undefined
}

trait SidebarOptions extends js.Object {
  var context: js.UndefOr[String | JQuerySelection] = js.undefined
  var exclusive: js.UndefOr[Boolean] = js.undefined
  var closable: js.UndefOr[Boolean] = js.undefined
  var dimPage: js.UndefOr[Boolean] = js.undefined
  var scrollLock: js.UndefOr[Boolean] = js.undefined
  var returnScroll: js.UndefOr[Boolean] = js.undefined
  var delaySetup: js.UndefOr[Boolean] = js.undefined
  var transition: js.UndefOr[String] = js.undefined
  var mobileTransition: js.UndefOr[String] = js.undefined
  var selector: js.UndefOr[SidebarSelectorOption] = js.undefined
}

trait SidebarSelectorOption extends js.Object {
  var fixed: js.UndefOr[String] = js.undefined
  var omitted: js.UndefOr[String] = js.undefined
  var pusher: js.UndefOr[String] = js.undefined
  var sidebar: js.UndefOr[String] = js.undefined
}
