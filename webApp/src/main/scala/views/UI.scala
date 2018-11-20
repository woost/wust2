package wust.webApp.views

import cats.effect.IO

import concurrent.duration._
import emojijs.EmojiConvertor
import fomanticui.PopupOptions
import fontAwesome.freeSolid
import marked.Marked
import monix.execution.Cancelable
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import org.scalajs.dom.{KeyboardEvent, MouseEvent}
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers._
import wust.css.{Styles, ZIndex}
import wust.webApp.BrowserDetect
import wust.webApp.outwatchHelpers._
import rx._

import scala.scalajs.js

object UI {
  def toggle(labelText:String, initialChecked: Boolean = false): CustomEmitterBuilder[Boolean, VDomModifier] = EmitterBuilder.ofModifier[Boolean]{sink =>
    div(
      cls := "ui toggle checkbox",
      input(tpe := "checkbox", onChange.checked --> sink, defaultChecked := initialChecked),
      label(labelText)
    )}


  def modal(header: VDomModifier, description: VDomModifier, actions: Option[VDomModifier] = None): VNode = {
    div(
      keyed,
      cls := "ui basic modal",
      i(cls := "close icon"),
      div(
        cls := "header",
        header
      ),
      div(
        cls := "content",
        div(
          cls := "ui medium",
          div(
            cls := "description",
            description
          )
        ),
        actions.map { actions =>
          div(
            marginLeft := "auto",
            cls := "actions",
            actions
          )
        }
      ),
    )
  }

  def tooltip: AttributeBuilder[String, VDomModifier] = str => VDomModifier(data.tooltip := str, zIndex := ZIndex.tooltip)
  def tooltip(position: String): AttributeBuilder[String, VDomModifier] = str => VDomModifier(tooltip := str, data.position := position)

  def popup(options: PopupOptions): VDomModifier = VDomModifier(onDomMount.asJquery.foreach(_.popup(options)), zIndex := ZIndex.tooltip)
  def popup: AttributeBuilder[String, VDomModifier] = str => popup(new PopupOptions { content = str })
  def popup(position: String): AttributeBuilder[String, VDomModifier] = str => {
    val _position = position
    popup(new PopupOptions { content = str; position = _position })
  }
}
