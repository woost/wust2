package wust.webUtil

import wust.facades.fomanticui.{AccordeonOptions, DropdownEntry, DropdownOptions, PopupOptions, TableSortInstance, ToastClassNameOptions, ToastOptions}
import wust.facades.jquery
import wust.facades.jquery.{JQuery, JQuerySelection}
import monix.execution.Cancelable
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl.{data, _}
import outwatch.dom.helpers._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.util.collection._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import monix.reactive.Observer

object UI {
  private val currentlyEditingSubject = PublishSubject[Boolean]
  def currentlyEditing: Observable[Boolean] = currentlyEditingSubject

  def message(msgType:String = "", header:Option[VDomModifier] = None, content:Option[VDomModifier] = None):VNode = {
    div(
      cls := "ui message", cls := msgType,
      header.map(headerContent => div(cls := "header", headerContent)),
      content
    )
  }

  @inline def checkbox(labelText:VDomModifier, isChecked: Var[Boolean]):VNode = checkboxEmitter(labelText, isChecked) --> isChecked
  @inline def checkboxEmitter(labelText:VDomModifier, isChecked: Boolean): EmitterBuilder[Boolean, VNode] = checkboxEmitter(labelText, Var(isChecked))
  def checkboxEmitter(labelText:VDomModifier, isChecked: Rx[Boolean]): EmitterBuilder[Boolean, VNode] = EmitterBuilder.ofNode[Boolean]{sink =>
    div(
      cls := "ui checkbox",
      input(
        tpe := "checkbox",
        onChange.checked --> sink,
        onClick.stopPropagation --> Observer.empty, // fix safari emitting extra click event onChange
        checked <-- isChecked,
        // defaultChecked := isChecked.now
      ),
      label(labelText)
    )}

  @inline def toggle(labelText:VDomModifier, isChecked: Var[Boolean]):VNode = toggleEmitter(labelText, isChecked) --> isChecked
  @inline def toggleEmitter(labelText:VDomModifier, isChecked: Boolean): EmitterBuilder[Boolean, VNode] = toggleEmitter(labelText, Var(isChecked))
  def toggleEmitter(labelText:VDomModifier, isChecked: Rx[Boolean]): EmitterBuilder[Boolean, VNode] = checkboxEmitter(labelText, isChecked).mapResult(_(cls := "toggle"))

  val tooltip: AttributeBuilder[String, VDomModifier] = str => VDomModifier.ifNot(BrowserDetect.isMobile)(data.tooltip := str, data.variation := "mini basic")
  def tooltip(position: String): AttributeBuilder[String, VDomModifier] = str => VDomModifier.ifNot(BrowserDetect.isMobile)(data.tooltip := str, data.position := position, data.variation := "mini basic")

  // javascript version of tooltip
  def popup(options: PopupOptions): VDomModifier = VDomModifier.ifNot(BrowserDetect.isMobile)(
    managedElement.asJquery { elem =>
      elem.popup(options)
      Cancelable(() => elem.popup("destroy"))
    }
  )
  val popup: AttributeBuilder[String, VDomModifier] = str => popup(new PopupOptions { content = str; hideOnScroll = true; exclusive = true; variation = "mini basic" })
  def popup(position: String): AttributeBuilder[String, VDomModifier] = str => {
    val _position = position
    popup(new PopupOptions { content = str; position = _position; hideOnScroll = true; exclusive = true; variation = "mini basic" })
  }
  val popupHtml: AttributeBuilder[VNode, VDomModifier] = node => popup(new PopupOptions { html = node.render; hideOnScroll = true; exclusive = true; inline = true; variation = "mini basic" })
  def popupHtml(position: String): AttributeBuilder[VNode, VDomModifier] = node => {
    val _position = position
    popup(new PopupOptions { html = node.render; position = _position; hideOnScroll = true; exclusive = true; inline = true; variation = "mini basic" })
  }

  def dropdown(options: DropdownEntry*): EmitterBuilder[String, VDomModifier] = dropdown(VDomModifier.empty, options: _*)
  def dropdown(modifier: VDomModifier, options: DropdownEntry*): EmitterBuilder[String, VDomModifier] = EmitterBuilder.ofModifier { sink =>
    div(
      modifier,
      cls := "ui selection dropdown",
      onDomMount.asJquery.foreach(_.dropdown(new DropdownOptions {
        onChange = { (key, text, selectedElement) =>
          for {
            key <- key
          } sink.onNext(key)
        }: js.Function3[js.UndefOr[String], js.UndefOr[String], jquery.JQuerySelection, Unit]

        values = options.toJSArray
      })),

      i(cls := "dropdown icon"),
      options.find(_.selected.getOrElse(false)).map(e => div(cls := "default text", e.value))
    )
  }

  sealed trait ToastLevel { def value: String }
  object ToastLevel {
    case object Info extends ToastLevel { def value = "info" }
    case object Success extends ToastLevel { def value = "success" }
    case object Warning extends ToastLevel { def value = "warning" }
    case object Error extends ToastLevel { def value = "error" }
  }
  def toast(msg: String, title: js.UndefOr[String] = js.undefined, click: () => Unit = () => (), autoclose: Boolean = true, level: ToastLevel = ToastLevel.Info): Unit = {
    val _title = title
    import wust.facades.jquery.JQuery._
    `$`(dom.window.document.body).toast(new ToastOptions {
      `class` = level.value
      className = new ToastClassNameOptions {
        toast = "ui message"
        title = "ui header"
      }
      onClick = click: js.Function0[Unit]
      position = "bottom right"
      title = _title
      message = msg
      displayTime = if (autoclose) 10000 else 0
    })
  }

  val horizontalDivider = div(cls := "ui horizontal divider")
  def horizontalDivider(text:String) = div(cls := "ui horizontal divider", text)
  val verticalDivider = div(cls := "ui vertical divider")
  def verticalDivider(text:String) = div(cls := "ui vertical divider", text)

  def dropdownMenu(items: VDomModifier, close: Observable[Unit], dropdownModifier: VDomModifier = VDomModifier.empty): VDomModifier = VDomModifier(
    cls := "ui pointing link inline dropdown",
    dropdownModifier,
    Elements.withoutDefaultPassiveEvents, // revert default passive events, else dropdown is not working
    emitter(close).useLatest(onDomMount.asJquery).foreach(_.dropdown("hide")),
    managedElement.asJquery { elem =>
      elem
        .dropdown(new DropdownOptions {
          direction = "downward"
          onHide = { () =>
            currentlyEditingSubject.onNext(false)
            true
          }: js.Function0[Boolean]
          onShow = { () =>
            currentlyEditingSubject.onNext(true)
            true
          }: js.Function0[Boolean]
        })
      Cancelable(() => elem.dropdown("destroy"))
    },
    cursor.pointer,
    div(
      cls := "menu",
      items,
    )
  )
  def accordion(title: VDomModifier, content: VDomModifier): VNode =
    accordion(Seq(AccordionEntry(title, content)))

  final case class AccordionEntry(title: VDomModifier, content: VDomModifier, active:Boolean = true)
  def accordion(content: Seq[AccordionEntry],
                styles: String = "styled fluid",
                collapsible : Boolean = true,
                exclusive : Boolean = true,
                duration : Int = 250,
                ): VNode = div(
    cls := s"ui ${ styles } accordion",
    content.map { entry =>
      VDomModifier(
        div(
          padding := "0px",
          cls := "title" + (if (entry.active) " active" else ""),
          i(cls := "dropdown icon"),
          entry.title,
          ),
        div(
          cls := "content" + (if (entry.active) " active" else ""),
          padding := "0px",
          entry.content
        )
      )
    },
    onDomMount.asJquery.foreach(_.accordion(AccordeonOptions(
                                              collapsible = collapsible,
                                              exclusive = exclusive,
                                              duration = duration))),
  )

  def content(header: VDomModifier, description: VDomModifier = VDomModifier.empty): VNode = {
    div(
      cls := "content",
      div(
        cls := "header",
        header
      ),
      div(
        cls := "description",
        description
      )
    )
  }

  final case class ColumnEntry(sortValue: Any, value: VDomModifier, rowModifier: VDomModifier = VDomModifier.empty)
  object ColumnEntry {
    def apply(value: String): ColumnEntry = ColumnEntry(value, value)
  }
  final case class Column(name: VDomModifier, entries: List[ColumnEntry], sortable: Boolean = true)
  final case class ColumnSort(index: Int, direction: String)
  def sortableTable(columns: Seq[Column], sort: Var[Option[ColumnSort]])(implicit ctx: Ctx.Owner): VNode = {
    val rows = columns.map(_.entries).transpose

    val jqHandler = Handler.unsafe[JQuerySelection]

    table(
      cls := "ui sortable celled collapsing unstackable table",
      margin := "0px", // clear margin we got from fomantic ui

      managedElement { elem =>
        val jq = JQuery.$(elem.asInstanceOf[dom.html.Element])
        jqHandler.onNext(jq)

        jq.tablesort()
        val data = jq.data("tablesort").asInstanceOf[TableSortInstance]

        sort.now.foreach { sort =>
          // get the th elements in this elem
          val tr = elem.children(0).children(0) // table  > thead > tr
          if (sort.index >= 0 && sort.index < tr.children.length) {
            val th = jquery.JQuery.$(tr.children(sort.index).asInstanceOf[dom.html.TableHeaderCell])

            // need to update data object
            data.index = sort.index
            data.direction = sort.direction

            data.sort(th, sort.direction)
          }
        }

        data.$table.on("tablesort:complete", { () =>
          sort() = if (data.direction != null) Some(ColumnSort(data.index, data.direction)) else None
        })

        Cancelable(() => data.destroy())
      },

      thead(
        tr(
          columns.mapWithIndex { (idx, column) =>
            th(column.name, VDomModifier.ifNot(column.sortable)(cls := "no-sort"))
          }
        )
      ),
      tbody(
        rows.map { columnEntries =>
          tr(
            columnEntries.map { columnEntry =>
              VDomModifier(
                columnEntry.rowModifier,
                // we purposely set prop(data-x) instead of data.x attribute. because otherwise tablesort plugin somehow does not see updated sort values.
                td(prop("data-sort-value") := columnEntry.sortValue, columnEntry.value)
              )
            }
          )
        }
      ),
//      tfoot(
//        tr(
//          th(col1),
//          th(col2)
//        )
//      )
    )
  }


  def segment(header: VDomModifier, description: VDomModifier, segmentClass: String = "", segmentsClass: String = "") = div(
    cls := s"ui segments $segmentsClass",
    div(
      cls := s"ui secondary segment $segmentClass",
      padding := "0.5em 0.5em",
      header
    ),
    div(
      cls := s"ui segment $segmentClass",
      padding := "0.5em 0.5em",
      description
    )
  )

}
