package wust.webApp.views

import cats.implicits._
import cats.effect.IO
import fontAwesome.freeSolid
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder, SyncEmitterBuilder}
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

  val onEnter: SyncEmitterBuilder[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(e => e.keyCode == KeyCode.Enter && !e.shiftKey)
      .preventDefault

  val onEscape: SyncEmitterBuilder[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(_.keyCode == KeyCode.Escape)
      .preventDefault

  val onGlobalEscape =
    EmitterBuilder.ofModifier { (sink: dom.KeyboardEvent => Unit) =>
      VDomModifier(
        managed(IO(events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape).foreach(sink)))
      )
    }

  val onGlobalClick =
    EmitterBuilder.ofModifier { (sink: dom.MouseEvent => Unit) =>
      VDomModifier(
        managed(IO(events.document.onClick.foreach(sink)))
      )
    }

  def onHammer(events: String):CustomEmitterBuilder[hammerjs.Event, VDomModifier] = {
    import hammerjs._
    EmitterBuilder.ofModifier { (sink: hammerjs.Event => Unit) =>
      managedElement.asHtml { elem =>
        elem.asInstanceOf[js.Dynamic].hammer = js.undefined
        var hammertime = new Hammer[Event](elem, new Options { cssProps = new CssProps { userSelect = "auto"}} )
        propagating(hammertime).on(events, { e =>
          e.stopPropagation()
          // if(e.target == elem)
          sink(e)
        })

        Cancelable { () =>
          hammertime.stop()
          hammertime.destroy()
          elem.asInstanceOf[js.Dynamic].hammer = js.undefined
        }
      }
    }
  }

  val onTap: CustomEmitterBuilder[hammerjs.Event, VDomModifier] = onHammer("tap")
  val onPress: CustomEmitterBuilder[hammerjs.Event, VDomModifier] = onHammer("press")

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

  def defer(code: => Unit): Unit = {
//    dom.window.setTimeout(() => code, timeout = 0)
    immediate.immediate(() => code)
  }


  def valueWithEnter: CustomEmitterBuilder[String, VDomModifier] = valueWithEnterWithInitial(Observable.empty)
  def valueWithEnterWithInitial(overrideValue: Observable[String]): CustomEmitterBuilder[String, VDomModifier] = EmitterBuilder.ofModifier {
    (sink: String => Unit) =>
      val userInput = Handler.created[String]
      val writeValue = Observable.merge(userInput.map(_ => ""), overrideValue)
      Seq(
          value <-- writeValue,
        //TODO WTF WHY DOES THAT NOT WORK?
        //          onEnter.value.filter(_.nonEmpty) --> userInput,
        onEnter.stopPropagation.map(_.currentTarget.asInstanceOf[dom.html.Input].value).filter(_.nonEmpty) --> userInput,
          managed(IO(userInput.distinctUntilChanged.foreach(sink))) // distinct, because Enter can be pressed multiple times before writeValue clears the field
        )
    }

  def closeButton: VNode = div(
    div(cls := "fa-fw", freeSolid.faTimes),
    padding := "10px",
    Styles.flexStatic,
    cursor.pointer,
  )

}
