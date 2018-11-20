package wust.webApp.views

import cats.effect.IO
import concurrent.duration._
import emojijs.EmojiConvertor
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
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder, SyncEmitterBuilder}
import wust.css.Styles
import wust.webApp.BrowserDetect
import wust.webApp.outwatchHelpers._
import rx._

import scala.scalajs.js

object SemanticUiElements {
  def uiToggle(labelText:String, initialChecked: Boolean = false): CustomEmitterBuilder[Boolean, VDomModifier] = EmitterBuilder.ofModifier[Boolean]{sink =>
    div(
      cls := "ui toggle checkbox",
      input(tpe := "checkbox", onChange.checked --> sink, defaultChecked := initialChecked),
      label(labelText)
    )}


  def uiModal(header: VDomModifier, description: VDomModifier, actions: Option[VDomModifier] = None): VNode = {
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

}
