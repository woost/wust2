package wust.webApp.views

import fomanticui._
import fontAwesome.IconLookup
import monix.reactive.{Observable, subjects}
import monix.reactive.subjects.{PublishSubject, ReplaySubject}
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers._
import rx._
import wust.css.{Styles, ZIndex}
import wust.webApp.outwatchHelpers._
import wust.webApp.{BrowserDetect, Ownable}
import wust.webApp.views.Components._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object UI {
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

  case class ModalConfig(header: VDomModifier, description: VDomModifier, actions: Option[VDomModifier] = None, modalModifier: VDomModifier = VDomModifier.empty, contentModifier: VDomModifier = VDomModifier.empty)
  def modal(config: Observable[Ownable[ModalConfig]], globalClose: Observable[Unit]): VDomModifier = div.static(keyValue)(div( //intentianally wrap in order to have a static node around the moving modal that semnatic ui moves into the body
    cls := "ui modal",
    config.map[VDomModifier] { configRx =>
      configRx.flatMap(config => Ownable { implicit ctx =>
        VDomModifier(
          config.modalModifier,

          emitter(globalClose).useLatest(onDomMount.asJquery).foreach(_.modal("hide")),
          onDomMount.asJquery.foreach { e => e.modal("show") },

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
              marginBottom := "20px"
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
  ))

  object ModalConfig {
    import wust.graph.{Node, Page}
    import wust.sdk.BaseColors
    import wust.sdk.NodeColor._
    import wust.webApp.state.{GlobalState, View}
    import wust.webApp.views.Components.renderNodeData

    def defaultHeader(state: GlobalState, node: Node, modalHeaderText: String, icon: VDomModifier)(implicit ctx: Ctx.Owner): VDomModifier = {
      VDomModifier(
        backgroundColor := BaseColors.pageBg.copy(h = hue(node.id)).toHex,
        div(
          Styles.flex,
          flexDirection.row,
          justifyContent.spaceBetween,
          alignItems.center,
          div(
            Styles.flex,
            flexDirection.column,
            div(
              Styles.flex,
              alignItems.center,
              nodeAvatar(node, size = 20)(marginRight := "5px", Styles.flexStatic),
              renderNodeData(node.data)(cls := "channel-name", fontWeight.normal, marginRight := "15px"),
              paddingBottom := "5px",
            ),
            div(modalHeaderText),
          ),
          div(
            Styles.flex,
            Styles.flexStatic,
            icon,
            cursor.pointer,
            fontSize.xxLarge,
            onClick.stopPropagation.mapTo(state.urlConfig.now.focus(Page(node.id), View.Property)) --> state.urlConfig,
          ),
        ),
      )
    }
  }

  case class SidebarConfig(items: VDomModifier)
  def sidebar(config: Observable[Ownable[SidebarConfig]], globalClose: Observable[Unit]): VDomModifier = div.static(keyValue) { //intentianally wrap in order to have a static node around the moving modal that semnatic ui moves into the body
    val elemHandler = PublishSubject[JQuerySelectionWithFomanticUI]

    VDomModifier(
      cls := "ui sidebar right icon labeled vertical menu mini",
      width := (if (BrowserDetect.isMobile) "90%" else "400px"),

      onDomMount.asJquery.foreach { e =>
        elemHandler.onNext(e)
        e.sidebar(new SidebarOptions {
          transition = "overlay"
          exclusive = true
          context = ".main-viewrender"
        }).sidebar("hide")
      },

      emitter(globalClose).useLatest(onDomMount.asJquery).foreach(_.sidebar("hide")),

      config.map[VDomModifier] { config =>
        config.flatMap[VDomModifier](config => Ownable { implicit ctx =>
          VDomModifier(
            onDomMount.asJquery.foreach(_.sidebar("show")),
            config.items
          )
        })
      }
    )
  }

  // tooltip is CSS only. It does not work when the tooltip itself is not in the rendering area of the element. If tooltip does not work, try popup instead
  def tooltip: AttributeBuilder[String, VDomModifier] = str => VDomModifier.ifNot(BrowserDetect.isMobile)(VDomModifier(data.tooltip := str, zIndex := ZIndex.tooltip))
  def tooltip(position: String): AttributeBuilder[String, VDomModifier] = str => VDomModifier.ifNot(BrowserDetect.isMobile)(VDomModifier(tooltip := str, data.position := position))

  // javascript version of tooltip
  def popup(options: PopupOptions, tooltipZIndex: Int = ZIndex.tooltip): VDomModifier = VDomModifier.ifNot(BrowserDetect.isMobile)(VDomModifier(onDomMount.asJquery.foreach(_.popup(options)), zIndex := tooltipZIndex))
  def popup: AttributeBuilder[String, VDomModifier] = str => popup(new PopupOptions { content = str; hideOnScroll = true; exclusive = true; })
  def popup(position: String): AttributeBuilder[String, VDomModifier] = str => {
    val _position = position
    popup(new PopupOptions { content = str; position = _position; hideOnScroll = true; exclusive = true; hoverable = true; inline = true })
  }
  def popup(tooltipZIndex: Int): AttributeBuilder[String, VDomModifier] = str => popup(new PopupOptions { content = str; hideOnScroll = true; exclusive = true; }, tooltipZIndex)
  def popupHtml: AttributeBuilder[VNode, VDomModifier] = node => popup(new PopupOptions { html = node.render; hideOnScroll = true; exclusive = true; hoverable = true; inline = true })
  def popupHtml(position: String): AttributeBuilder[VNode, VDomModifier] = node => {
    val _position = position
    popup(new PopupOptions { html = node.render; position = _position; hideOnScroll = true; exclusive = true; hoverable = true; inline = true })
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

      input(tpe := "hidden", attr("name") := "gender"),
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

  val horizontalDivider = div(cls := "ui divider")
  def horizontalDivider(text:String) = div(cls := "ui horizontal divider", text)

  def dropdownMenu(items: VDomModifier): VNode = div(
    cls := "ui icon labeled fluid dropdown",
    Elements.withoutDefaultPassiveEvents, // revert default passive events, else dropdown is not working
    onDomMount.asJquery.foreach(_.dropdown("hide")),
    cursor.pointer,
    div(
      cls := "menu",
      items
    )
  )

  def accordion(title: VDomModifier, content: VDomModifier): VNode = div(
    cls :="ui styled fluid accordion",
    div(
      padding := "0px",
      borderTop := "0px",
      cls := "title",
      i(cls := "dropdown icon"),
      title,
    ),
    div(
      cls := "content",
      padding := "0px",
      content
    ),
    onDomMount.asJquery.foreach(_.accordion()),
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
}
