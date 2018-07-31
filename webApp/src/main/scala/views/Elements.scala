package wust.webApp.views

import monix.reactive.Observer
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder}
import wust.webApp.outwatchHelpers._

import scala.scalajs.js

object Elements {
  // Enter-behavior which is consistent across mobile and desktop:
  // - textarea: enter emits keyCode for Enter
  // - input: Enter triggers submit

  def scrollToBottom(elem: dom.Element): Unit = {
    //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
    try {
      elem.scrollTop = elem.scrollHeight
    } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
  }

  val onEnter: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown
      .filter( e => e.keyCode == KeyCode.Enter && !e.shiftKey)
      .preventDefault

  val onEscape: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown
      .filter( _.keyCode == KeyCode.Escape )
      .preventDefault

  val onGlobalEscape = 
    CustomEmitterBuilder { sink: Observer[dom.KeyboardEvent] =>
      VDomModifier(
        managed(sink <-- events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape))
      )
    }

  def decodeFromAttr[T: io.circe.Decoder](elem: dom.html.Element, attrName:String):Option[T] = {
    import io.circe.parser.decode
    for {
      elem <- elem.asInstanceOf[js.UndefOr[dom.html.Element]].toOption
      attr <- Option(elem.attributes.getNamedItem(attrName))
      target <- decode[T](attr.value).toOption
    } yield target
  }

  def removeDomElement(elem:dom.Element):Unit = {
    elem.parentNode.removeChild(elem)
  }

  def defer(code: => Unit):Unit = {
    dom.window.setTimeout(() => code, timeout = 0)
  }


  def valueWithEnter: CustomEmitterBuilder[String, Modifier] = CustomEmitterBuilder {
    (sink: Observer[String]) =>
      for {
        userInput <- Handler.create[String]
        clearHandler = userInput.map(_ => "")
        modifiers <- Seq(
          value <-- clearHandler,
          onEnter.value.filter(_.nonEmpty) --> userInput,
          managed(sink <-- userInput)
        )
      } yield modifiers
  }
}
