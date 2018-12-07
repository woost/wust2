package wust.webApp.views

import cats.effect.IO

import concurrent.duration._
import emojijs.EmojiConvertor
import fomanticui.{DropdownEntry, DropdownOptions, ModalOptions, PopupOptions, ToastClassNameOptions, ToastOptions}
import fontAwesome.freeSolid
import marked.Marked
import monix.execution.Cancelable
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import org.scalajs.dom.{KeyboardEvent, MouseEvent}
import outwatch.dom.{helpers, _}
import outwatch.dom.dsl._
import outwatch.dom.helpers._
import wust.css.{Styles, ZIndex}
import wust.webApp.{BrowserDetect, Ownable}
import wust.webApp.outwatchHelpers._
import rx._

import scala.scalajs.js.JSConverters._
import scala.scalajs.js
import scala.collection.breakOut

object UI {
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

  case class ModalConfig(header: VDomModifier, description: VDomModifier, close: Observable[Unit] = Observable.empty, actions: Option[VDomModifier] = None, modalModifier: VDomModifier = VDomModifier.empty, contentModifier: VDomModifier = VDomModifier.empty)
  def modal(config: Observable[Ownable[ModalConfig]]): VDomModifier = div.static(keyValue)(div( //intentianally wrap in order to have a static node around the moving modal that semnatic ui moves into the body
    cls := "ui modal",
    config.map { configRx =>
      withManualOwner { implicit ctx =>
        val config = configRx.get(ctx)
        VDomModifier(
          emitter(config.close).useLatest(onDomMount.asJquery).foreach(_.modal("hide")),
          config.modalModifier,
          onDomMount.asJquery.foreach { e =>
            e.modal("show")
          },
          i(cls := "close icon"),
          div(
            cls := "header",
            config.header
          ),
          div(
            cls := "content",
            config.contentModifier,
            div(
              cls := "ui medium",
              div(
                cls := "description",
                config.description
              )
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
      }
    }
  ))

  def tooltip: AttributeBuilder[String, VDomModifier] = str => VDomModifier(data.tooltip := str, zIndex := ZIndex.tooltip)
  def tooltip(position: String): AttributeBuilder[String, VDomModifier] = str => VDomModifier(tooltip := str, data.position := position)

  def popup(options: PopupOptions): VDomModifier = VDomModifier(onDomMount.asJquery.foreach(_.popup(options)), zIndex := ZIndex.tooltip)
  def popup: AttributeBuilder[String, VDomModifier] = str => popup(new PopupOptions { content = str; hideOnScroll = true; exclusive = true; })
  def popup(position: String): AttributeBuilder[String, VDomModifier] = str => {
    val _position = position
    popup(new PopupOptions { content = str; position = _position; hideOnScroll = true; exclusive = true; })
  }
  def popupHtml: AttributeBuilder[BasicVNode, VDomModifier] = node => popup(new PopupOptions { html = node.render.outerHTML; hideOnScroll = true; exclusive = true; })
  def popupHtml(position: String): AttributeBuilder[BasicVNode, VDomModifier] = node => {
    val _position = position
    popup(new PopupOptions { html = node.render.outerHTML; position = _position; hideOnScroll = true; exclusive = true; })
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
}
