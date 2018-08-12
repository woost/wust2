package wust.webApp.views

import monix.reactive.{Observer, Observable}
import cats.implicits._
import org.scalajs.dom
import org.scalajs.dom.console
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
      decoded <- decode[T](attr.value).toOption
    } yield decoded
  }

  def readPropertyFromElement[T](elem: dom.html.Element, propName:String):Option[T] = {
    for {
      elem <- elem.asInstanceOf[js.UndefOr[dom.html.Element]].toOption
      valueProvider <- elem.asInstanceOf[js.Dynamic].selectDynamic(propName).asInstanceOf[js.UndefOr[() => T]].toOption
    } yield valueProvider()
  }

  def writePropertyIntoElement(elem: dom.html.Element, propName:String, value: => Any): Unit = {
    elem.asInstanceOf[js.Dynamic].updateDynamic(propName)((() => value).asInstanceOf[js.Any])
  }

  def removeDomElement(elem:dom.Element):Unit = {
    elem.parentNode.removeChild(elem)
  }

  def defer(code: => Unit):Unit = {
    dom.window.setTimeout(() => code, timeout = 0)
  }


  def valueWithEnter: CustomEmitterBuilder[String, Modifier] = valueWithEnterWithInitial(Observable.empty)
  def valueWithEnterWithInitial(overrideValue: Observable[String]): CustomEmitterBuilder[String, Modifier] = CustomEmitterBuilder {
    (sink: Observer[String]) =>
      for {
        userInput <- Handler.create[String]
        writeValue = Observable.merge(userInput.map(_ => ""), overrideValue)
        modifiers <- Seq(
          value <-- writeValue,
          onEnter.value.filter(_.nonEmpty) --> userInput,
          managed(sink <-- userInput.distinctUntilChanged) // distinct, because Enter can be pressed multiple times before writeValue clears the field
        )
      } yield modifiers
  }
}
