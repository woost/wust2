package wust.webUtil

import wust.facades.fomanticui.{AccordeonOptions, DropdownEntry, DropdownOptions, PopupOptions, TableSortInstance, ToastClassNameOptions, ToastOptions, ProgressOptions}
import wust.facades.jquery
import wust.facades.jquery.{JQuery, JQuerySelection}
import org.scalajs.dom
import rx._
import wust.webUtil.outwatchHelpers._
import wust.util.collection._

import outwatch._
import outwatch.dsl._
import outwatch.helpers._
import outwatch.reactive.handler._
import colibri._
import colibri.ext.rx._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import monix.reactive.Observer
import Elements.escapeHtml
import Elements.onClickDefault
import wust.facades.emojijs.EmojiConvertor


object UI {
  private val currentlyEditingSubject = Subject.publish[Boolean]
  def currentlyEditing: Observable[Boolean] = currentlyEditingSubject

  def message(msgType:String = "", header:Option[VDomModifier] = None, content:Option[VDomModifier] = None):VNode = {
    div(
      cls := "ui message", cls := msgType,
      header.map(headerContent => div(cls := "header", marginBottom := "3px", headerContent)),
      content,
      backgroundColor := "#ffe3be",
      boxShadow := "none",
      color := "#522e00",
    )
  }

  def checkbox(labelText:VDomModifier, isChecked: Var[Boolean]): VDomModifier = checkboxEmitter(labelText, isChecked) --> isChecked
  @inline def checkboxEmitter(labelText:VDomModifier, isChecked: Boolean): EmitterBuilder[Boolean, VNode] = checkboxEmitter(labelText, Var(isChecked))
  def checkboxEmitter(labelText:VDomModifier, isChecked: Rx[Boolean]): EmitterBuilder[Boolean, VNode] = EmitterBuilder.ofNode {sink =>
    val inputId = scala.util.Random.nextInt.toString
    div(
      cls := "ui checkbox",
      input(
        id := inputId,
        tpe := "checkbox",
        onChange.checked --> sink,
        onClick.stopPropagation.discard, // fix safari emitting extra click event onChange
        onMouseDown.stopPropagation.discard, // prevent RightSidebar from closing
        checked <-- isChecked,
        // defaultChecked := isChecked.now
      ),
      label(labelText, forId := inputId, cursor.pointer)
    )}

  def toggle(labelText:VDomModifier, isChecked: Var[Boolean]): VNode = toggleEmitter(labelText, isChecked) --> isChecked
  @inline def toggleEmitter(labelText:VDomModifier, isChecked: Boolean): EmitterBuilder[Boolean, VNode] = toggleEmitter(labelText, Var(isChecked))
  def toggleEmitter(labelText:VDomModifier, isChecked: Rx[Boolean]): EmitterBuilder[Boolean, VNode] = checkboxEmitter(labelText, isChecked).mapResult(_(cls := "toggle"))

  val tooltip: AttributeBuilder[String, VDomModifier] = tooltip()
  def tooltip(placement: String = "top", boundary: String = "scrollParent", permanent: Boolean = false, sticky:Boolean = false): AttributeBuilder[String, VDomModifier] = tippy.tooltip(_placement = placement, _boundary = boundary, permanent = permanent, _sticky = sticky)

  val tooltipHtml: AttributeBuilder[VNode, VDomModifier] = tooltipHtml()
  def tooltipHtml(placement: String = "top", boundary: String = "scrollParent", permanent: Boolean = false, sticky:Boolean = false): AttributeBuilder[VNode, VDomModifier] = tippy.tooltipHtml(_placement = placement, _boundary = boundary, permanent = permanent, _sticky = sticky)

  def dropdown(options: DropdownEntry*): EmitterBuilder[String, VDomModifier] = dropdown(VDomModifier.empty, options: _*)
  def dropdown(modifier: VDomModifier, options: DropdownEntry*): EmitterBuilder[String, VNode] = EmitterBuilder.ofNode { sink =>
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

  def progress(value:Int, total:Int, classes:String = "", label:Option[VDomModifier] = None) = {
    div(
      keyed,
      onDomMount.asJquery.foreach { elem =>
        elem.progress(new ProgressOptions{showActivity = false})
        // TODO: how to destroy?
      },
      onDomUpdate.asJquery.foreach { elem =>
        elem.progress("set progress", value)
        elem.progress("set total", total)
      },
     cls := "ui progress",
     cls := classes,
     attr("data-value") := value,
     attr("data-total") := total,
     div(
       cls := "bar",
       div(cls := "progress")
     ),
     label.map(content => div(cls := "label", content))
    )
  }

  sealed trait ToastLevel { def value: String }
  object ToastLevel {
    case object Info extends ToastLevel { def value = "info" }
    case object Success extends ToastLevel { def value = "success" }
    case object Warning extends ToastLevel { def value = "warning" }
    case object Error extends ToastLevel { def value = "error" }
  }
  def toast(
    msg: String,
    title: js.UndefOr[String] = js.undefined,
    click: () => Unit = () => (),
    autoclose: Boolean = true,
    level: ToastLevel = ToastLevel.Info,
    customIcon:Option[String] = None,
    enableIcon:Boolean = true
  ): Unit = {
    val _title = title
    import wust.facades.jquery.JQuery._
    `$`(dom.window.document.body).toast(new ToastOptions {
      `class` = level.value
      className = new ToastClassNameOptions {
        toast = "ui message"
        title = "ui header"
        progress = "ui attached progress"
      }
      onClick = click: js.Function0[Unit]
      position = "bottom right"
      title = _title
      message = EmojiConvertor.replace_colons_safe(escapeHtml(msg))
      showIcon = if(enableIcon) customIcon match {case Some(faIconName) => faIconName; case None => true} else false
      displayTime = if (autoclose) 5000 else 0
      showProgress = "bottom"
      progressUp = false
    })
  }

  val horizontalDivider = div(cls := "ui horizontal divider")
  def horizontalDivider(text:String) = div(cls := "ui horizontal divider", text)
  val verticalDivider = div(cls := "ui vertical divider")
  def verticalDivider(text:String) = div(cls := "ui vertical divider", text)

  def dropdownMenu(items: VDomModifier, close: Observable[Unit] = Observable.empty, dropdownModifier: VDomModifier = VDomModifier.empty): VDomModifier = VDomModifier(
    cls := "ui pointing link inline dropdown",
    dropdownModifier,
    Elements.withoutDefaultPassiveEvents, // revert default passive events, else dropdown is not working
    emitter(close).useLatestEmitter(onDomMount.asJquery).foreach(_.dropdown("hide")),
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
      cancelable(() => elem.dropdown("destroy"))
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
          onMouseDown.stopPropagation.discard, // avoid toggling right sidebar
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
            val th = jquery.JQuery.$(tr.children(sort.index).asInstanceOf[dom.html.TableCell])

            // need to update data object
            data.index = sort.index
            data.direction = sort.direction

            data.sort(th, sort.direction)
          }
        }

        data.$table.on("tablesort:complete", { () =>
          sort() = if (data.direction != null) Some(ColumnSort(data.index, data.direction)) else None
        })

        cancelable(() => data.destroy())
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


  def segment(header: VDomModifier, description: VDomModifier, headerSegmentClass: String = "top", segmentClass: String = "", segmentsClass: String = "") = div(
    cls := s"ui segments $segmentsClass",
    h5(
      cls := s"ui segment $headerSegmentClass",
      padding := "0.5em 0.5em",
      header
    ),
    div(
      cls := s"ui segment $segmentClass",
      padding := "0.5em 0.5em",
      description
    )
  )

  def segmentWithoutHeader(description: VDomModifier, segmentClass: String = "", segmentsClass: String = "") = div(
    cls := s"ui segments $segmentsClass",
    div(
      cls := s"ui segment $segmentClass",
      padding := "0.5em 0.5em",
      description
    )
  )


  def toggleButton(active: Rx[Boolean])(implicit ctx: Ctx.Owner): VNode = button(
    cls := "ui button",
    active.map(VDomModifier.ifTrue(_)(cls := "active")),
    onClickDefault.asHtml.foreach(_.blur())
  )

}
