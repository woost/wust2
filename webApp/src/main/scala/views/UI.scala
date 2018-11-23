package wust.webApp.views

import cats.effect.IO

import concurrent.duration._
import emojijs.EmojiConvertor
import fomanticui.{DropdownEntry, DropdownOptions, PopupOptions, ToastOptions, ModalOptions}
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
import wust.webApp.BrowserDetect
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

  case class ModalConfig(header: VDomModifier, description: VDomModifier, close: Observable[Unit] = Observable.empty, actions: Option[VDomModifier] = None, modalModifier: VDomModifier = VDomModifier.empty)
  def modal(config: Observable[ModalConfig]): VDomModifier = div(
      keyed,
      cls := "ui modal",
      config.map {
        case config => VDomModifier(
          emitter(config.close).useLatest(onDomMount.asJquery).foreach(_.modal("hide")),
          config.modalModifier,
          onDomMount.asJquery.foreach { e =>
            // we set detachable to false, so semantic ui does not move the element into the body.
            // such a dom manipulation does not work well with virtual dom, because the virtual dom
            // state and the real dom state would diverge and any succeeding patch could lead to an error.
            e.modal(new ModalOptions { detachable = false })
            e.modal("show")
          },
          i(cls := "close icon"),
          div(
            cls := "header",
            config.header
          ),
          div(
            cls := "content",
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
  )

  def tooltip: AttributeBuilder[String, VDomModifier] = str => VDomModifier(data.tooltip := str, zIndex := ZIndex.tooltip)
  def tooltip(position: String): AttributeBuilder[String, VDomModifier] = str => VDomModifier(tooltip := str, data.position := position)

  def popup(options: PopupOptions): VDomModifier = VDomModifier(onDomMount.asJquery.foreach(_.popup(options)), zIndex := ZIndex.tooltip)
  def popup: AttributeBuilder[String, VDomModifier] = str => popup(new PopupOptions { content = str; hideOnScroll = true; exclusive = true; })
  def popup(position: String): AttributeBuilder[String, VDomModifier] = str => {
    val _position = position
    popup(new PopupOptions { content = str; position = _position; hideOnScroll = true; exclusive = true; })
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
  def toast(msg: String, title: js.UndefOr[String] = js.undefined, click: () => Unit = () => (), level: ToastLevel = ToastLevel.Info): Unit = {
    val _title = title
    import jquery.JQuery._
    `$`(dom.window.document.body).toast(new ToastOptions {
      `class` = level.value
      onClick = click: js.Function0[Unit]
      position = "bottom right"
      title = _title
      message = msg
      displayTime = 5000
    })
  }
}
