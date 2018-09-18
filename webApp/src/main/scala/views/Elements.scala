package wust.webApp.views

import cats.implicits._
import cats.effect.IO
import fontAwesome.freeSolid
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder}
import wust.css.Styles
import wust.webApp.outwatchHelpers._

import scala.scalajs.js

object Elements {
  // Enter-behavior which is consistent across mobile and desktop:
  // - textarea: enter emits keyCode for Enter
  // - input: Enter triggers submit

  def scrollToBottom(elem: dom.Element): Unit = {
    //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
    try {
      elem.scrollTop = elem.scrollHeight - elem.clientHeight
    } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
  }

  val onEnter: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown
      .filter(e => e.keyCode == KeyCode.Enter && !e.shiftKey)
      .preventDefault

  val onEscape: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown
      .filter(_.keyCode == KeyCode.Escape)
      .preventDefault

  val onGlobalEscape =
    CustomEmitterBuilder { (sink: dom.KeyboardEvent => Unit) =>
      VDomModifier(
        managed(IO(events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape).foreach(sink)))
      )
    }

  val onGlobalClick =
    CustomEmitterBuilder { (sink: dom.MouseEvent => Unit) =>
      VDomModifier(
        managed(IO(events.document.onClick.foreach(sink)))
      )
    }

  def onHammer(events: String):CustomEmitterBuilder[hammerjs.Event, Modifier] = {
    import hammerjs._
    CustomEmitterBuilder { (sink: hammerjs.Event => Unit) =>
      var hammertime: Hammer[Event] = null
      VDomModifier(
        onDomMount.asHtml handleWith { elem =>
          hammertime = new Hammer(elem, new Options { cssProps = new CssProps { userSelect = "auto"}} )
          propagating(hammertime).on(events, { e =>
            e.stopPropagation()
            // if(e.target == elem)
            sink(e)
          })
        },
        onDomUnmount.asHtml handleWith { elem =>
          hammertime.stop()
          hammertime.destroy()
        },
      )
    }
  }

  val onTap: CustomEmitterBuilder[hammerjs.Event, Modifier] = onHammer("tap")
  val onPress: CustomEmitterBuilder[hammerjs.Event, Modifier] = onHammer("press")

  def decodeFromAttr[T: io.circe.Decoder](elem: dom.html.Element, attrName: String): Option[T] = {
    import io.circe.parser.decode
    for {
      elem <- elem.asInstanceOf[js.UndefOr[dom.html.Element]].toOption
      attr <- Option(elem.attributes.getNamedItem(attrName))
      decoded <- decode[T](attr.value).toOption
    } yield decoded
  }

  def readPropertyFromElement[T](elem: dom.html.Element, propName: String): Option[T] = {
    for {
      elem <- elem.asInstanceOf[js.UndefOr[dom.html.Element]].toOption
      valueProvider <- elem.asInstanceOf[js.Dynamic].selectDynamic(propName).asInstanceOf[js.UndefOr[() => T]].toOption
    } yield valueProvider()
  }

  def writePropertyIntoElement(elem: dom.html.Element, propName: String, value: => Any): Unit = {
    elem.asInstanceOf[js.Dynamic].updateDynamic(propName)((() => value).asInstanceOf[js.Any])
  }

  def removeDomElement(elem: dom.Element): Unit = {
    elem.parentNode.removeChild(elem)
  }

  def defer(code: => Unit): Unit = {
//    dom.window.setTimeout(() => code, timeout = 0)
    immediate.immediate(() => code)
  }


  def valueWithEnter: CustomEmitterBuilder[String, Modifier] = valueWithEnterWithInitial(Observable.empty)
  def valueWithEnterWithInitial(overrideValue: Observable[String]): CustomEmitterBuilder[String, Modifier] = CustomEmitterBuilder {
    (sink: String => Unit) =>
      for {
        userInput <- Handler.create[String]
        writeValue = Observable.merge(userInput.map(_ => ""), overrideValue)
        modifiers <- Seq(
          value <-- writeValue,
          //TODO WTF WHY DOES THAT NOT WORK?
//          onEnter.value.filter(_.nonEmpty) --> userInput,
          onEnter.stopPropagation.map(_.currentTarget.asInstanceOf[dom.html.Input].value).filter(_.nonEmpty) --> userInput,
          managed(IO(userInput.distinctUntilChanged.foreach(sink))) // distinct, because Enter can be pressed multiple times before writeValue clears the field
        )
      } yield modifiers
  }

  def closeButton: VNode = div(
    div(cls := "fa-fw", freeSolid.faTimes),
    padding := "10px",
    Styles.flexStatic,
    cursor.pointer,
  )

}
