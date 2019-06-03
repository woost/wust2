package wust.webApp.views

import wust.sdk.Colors
import fomanticui._
import fontAwesome.IconLookup
import jquery.JQuerySelection
import monix.execution.Cancelable
import monix.reactive.{Observable, subjects}
import monix.reactive.subjects.{PublishSubject, ReplaySubject}
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers._
import rx._
import wust.css.{Styles, ZIndex}
import wust.ids.View
import wust.util.collection._
import wust.webApp.outwatchHelpers._
import wust.webApp.{BrowserDetect, Ownable}
import wust.webApp.views.Components._
import flatland._

import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

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

  def toggle(labelText:String, initialChecked: Boolean = false): CustomEmitterBuilder[Boolean, VDomModifier] = EmitterBuilder.ofModifier[Boolean]{sink =>
    div(
      cls := "ui toggle checkbox",
      input(tpe := "checkbox", onChange.checked --> sink, defaultChecked := initialChecked),
      label(labelText)
    )}

  def toggle(labelText:String, isChecked: Var[Boolean]):VNode =
    div(
      cls := "ui toggle checkbox",
      input(tpe := "checkbox",
        onChange.checked --> isChecked,
        checked <-- isChecked,
        defaultChecked := isChecked.now
      ),
      label(labelText)
    )

  case class ModalConfig(header: VDomModifier, description: VDomModifier, onClose: () => Boolean = () => true, actions: Option[VDomModifier] = None, modalModifier: VDomModifier = VDomModifier.empty, contentModifier: VDomModifier = VDomModifier.empty)
  def modal(config: Observable[Ownable[ModalConfig]], globalClose: Observable[Unit]): VNode = div(
    cls := "ui modal",
    config.map[VDomModifier] { configRx =>
      configRx.flatMap(config => Ownable { implicit ctx =>
        VDomModifier(
          key := scala.util.Random.nextInt, // force new elem on every render. fixes slowly rendering modal in firefox
          config.modalModifier,

          emitter(globalClose.take(1)).useLatest(onDomMount.asJquery).foreach { e =>
            e.modal("hide")
              // TODO: remove this node from the dom whenever it is hidden (make this thing an observable[option[ownable[modalconfig]]]
              // workaround: kill the ctx owner, so we stop updating this node when it is closed.
            ctx.contextualRx.kill()
          },
          managedElement.asJquery { e =>
            e
              .modal(new ModalOptions {
                onHide = config.onClose: js.Function0[Boolean]
              })
              .modal("show")
            Cancelable(() => e.modal("destroy"))
          },

          i(cls := "close icon"),
          div(
            cls := "header modal-header",
            config.header
          ),
          div(
            cls := "content modal-content",
            config.contentModifier,
            div(
              cls := "ui medium modal-inner-content",
              div(
                cls := "description modal-description",
                config.description
              ),
            ),
            config.actions.map { actions =>
              div(
                marginLeft := "auto",
                cls := "actions",
                actions
              )
            }
          )
        )
      })
    }
  )

  object ModalConfig {
    import wust.graph.{Node, Page}
    import wust.sdk.BaseColors
    import wust.sdk.NodeColor
    import wust.sdk.NodeColor._
    import wust.webApp.state.GlobalState
    import wust.webApp.views.Components.renderNodeData

    @inline def defaultHeader(state: GlobalState, node: Node, modalHeader: VDomModifier, icon: VDomModifier)(implicit ctx: Ctx.Owner): VDomModifier = defaultHeader(state, Some(node), modalHeader, icon)
    @inline def defaultHeader(state: GlobalState, modalHeader: VDomModifier, icon: VDomModifier)(implicit ctx: Ctx.Owner): VDomModifier = defaultHeader(state, None, modalHeader, icon)
    def defaultHeader(state: GlobalState, node: Option[Node], modalHeader: VDomModifier, icon: VDomModifier)(implicit ctx: Ctx.Owner): VDomModifier = {
      VDomModifier(
        backgroundColor :=? node.map(node => BaseColors.pageBg.copy(h = NodeColor.hue(node.id)).toHex),
        color.white,
        div(
          Styles.flex,
          flexDirection.row,
          justifyContent.spaceBetween,
          alignItems.center,
          div(
            Styles.flex,
            flexDirection.column,
            node.map { node =>
              div(
                renderAsOneLineText(node)(cls := "channel-name", fontWeight.normal, marginRight := "15px"),
                paddingBottom := "5px",
              )
            },
            div(modalHeader),
          ),
          div(
            Styles.flex,
            Styles.flexStatic,
            icon,
            fontSize.xxLarge,
          ),
        ),
      )
    }
  }

  case class SidebarConfig(items: VDomModifier, sidebarModifier: VDomModifier = VDomModifier.empty)
  def sidebar(config: Observable[Ownable[SidebarConfig]], globalClose: Observable[Unit], targetSelector: Option[String]): VNode = {
    val elemHandler = PublishSubject[JQuerySelectionWithFomanticUI]

    div(
      cls := "ui sidebar right icon labeled borderless vertical menu mini",
//      width := (if (BrowserDetect.isMobile) "90%" else "400px"),
      width := "160px",
      zIndex := ZIndex.uiSidebar,

      config.map[VDomModifier] { config =>
        config.flatMap[VDomModifier](config => Ownable { implicit ctx =>
          VDomModifier(
            emitter(globalClose.take(1)).useLatest(onDomMount.asJquery).foreach { e =>
              e.sidebar("hide")
              // TODO: remove this node from the dom whenever it is hidden (make this thing an observable[option[ownable[sidebarconfig]]]
              // workaround: kill the ctx owner, so we stop updating this node when it is closed.
              ctx.contextualRx.kill()
            },
            managedElement.asJquery { e =>
              elemHandler.onNext(e)
              e.sidebar(new SidebarOptions {
                transition = "overlay"
                mobileTransition = "overlay"
                exclusive = true
                context = targetSelector.orUndefined
              }).sidebar("show")

              Cancelable(() => e.sidebar("destroy"))
            },
            config.items,
            config.sidebarModifier
          )
        })
      }
    )
  }

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
    import jquery.JQuery._
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

  case class AccordionEntry(title: VDomModifier, content: VDomModifier, active:Boolean = true)
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
    onDomMount.asJquery.foreach(_.accordion(fomanticui.AccordeonOptions(
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

  case class ColumnEntry(sortValue: Any, value: VDomModifier, rowModifier: VDomModifier = VDomModifier.empty)
  object ColumnEntry {
    def apply(value: String): ColumnEntry = ColumnEntry(value, value)
  }
  case class Column(name: VDomModifier, entries: List[ColumnEntry], sortable: Boolean = true)
  case class ColumnSort(index: Int, direction: String)
  def sortableTable(columns: Seq[Column], sort: Var[Option[ColumnSort]])(implicit ctx: Ctx.Owner): VNode = {
    val rows = columns.map(_.entries).transpose

    val jqHandler = Handler.unsafe[JQuerySelection]

    table(
      cls := "ui sortable celled collapsing unstackable table",
      margin := "0px", // clear margin we got from fomantic ui

      managedElement { elem =>
        val jq = jquery.JQuery.$(elem.asInstanceOf[dom.html.Element])
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

  def multiCheckbox[T: ClassTag](checkboxes: Array[T], description: T => VDomModifier): EmitterBuilder[Seq[T], VDomModifier] = EmitterBuilder.ofModifier { sink =>
    var checkedState = Array.fill(checkboxes.length)(false)
    val changed = PublishSubject[Unit]

    div(
      Styles.flex,
      flexDirection.column,
      fontSize.larger,
      checkboxes.mapWithIndex { (idx, value) =>
        div(
          marginLeft := "10px",
          marginBottom := "10px",
          label(
            Styles.flex,
              alignItems.center,
            input(
              tpe := "checkbox",
              onInput.checked.foreach { checked =>
                checkedState(idx) = checked
                changed.onNext(())
              },
              dsl.checked <-- changed.map(_ => checkedState(idx))
            ),
            description(value),
            cursor.pointer,
          )
        )
      },
      emitter(changed).map(_ => checkedState.flatMapWithIndex { (idx, checked) => if (checked) Array(checkboxes(idx)) else Array.empty } : Seq[T]) --> sink,
    )
  }

}
