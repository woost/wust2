package wust.webApp.views

import cats.implicits._
import cats.effect.IO
import fontAwesome.freeSolid
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.{KeyboardEvent, MouseEvent}
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder, SyncEmitterBuilder}
import wust.css.Styles
import wust.webApp.outwatchHelpers._
import wust.webApp.BrowserDetect

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

  val onGlobalEscape: CustomEmitterBuilder[KeyboardEvent, VDomModifier] =
    EmitterBuilder.ofModifier { (sink: dom.KeyboardEvent => Unit) =>
      if (BrowserDetect.isMobile) VDomModifier.empty
      else managed(IO(events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape).foreach(sink)))
    }

  val onGlobalClick: CustomEmitterBuilder[MouseEvent, VDomModifier] =
    EmitterBuilder.ofModifier { (sink: dom.MouseEvent => Unit) =>
      managed(IO(events.document.onClick.foreach(sink)))
    }

  val onClickOrLongPress: CustomEmitterBuilder[Boolean, VDomModifier] =
    EmitterBuilder.ofModifier { (sink: Boolean => Unit) =>
      // https://stackoverflow.com/a/27413909
      val duration = 251
      val distanceToleranceSq = 5*5

      var longpress = false
      var presstimer = -1
      var startx:Double = -1
      var starty:Double = -1
      var currentx:Double = -1
      var currenty:Double = -1

      def cancel(): Unit = {
        if (presstimer != -1) {
          clearTimeout(presstimer)
          presstimer = -1
        }
      }

      def click(e:dom.MouseEvent): Unit = {
        if (presstimer != -1) {
          clearTimeout(presstimer);
          presstimer = -1
        }

        if (!longpress) {
          sink(false) // click
        }
      }

      def start(e:dom.TouchEvent): Unit = {
        longpress = false
        startx = e.touches(0).clientX
        starty = e.touches(0).clientY
        currentx = startx
        currenty = starty

        presstimer = setTimeout({ () =>
          val dx = currentx - startx
          val dy = currenty - starty
          val distanceSq = dx*dx + dy*dy
          if(distanceSq <= distanceToleranceSq) {
            sink(true) // long click
          }
          longpress = true // prevents click
        }, duration)
      }

      @inline def updateCurrentPosition(e:dom.TouchEvent): Unit = {
        currentx = e.touches(0).clientX
        currenty = e.touches(0).clientY
      }

      VDomModifier(
        //TODO: SDT: add touch handlers
        onClick handleWith { click _ },
        eventProp("touchmove") handleWith { updateCurrentPosition _ },
        eventProp("touchstart") handleWith { start _ },
        eventProp("touchend") handleWith {cancel()},
        eventProp("touchleave") handleWith {cancel()},
        eventProp("touchcancel") handleWith {cancel()},
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

  @inline def defer(code: => Unit): Unit = {
//    dom.window.setTimeout(() => code, timeout = 0)
    immediate.immediate(() => code)
  }


  def valueWithEnter: CustomEmitterBuilder[String, VDomModifier] = valueWithEnterWithInitial(Observable.empty)
  def valueWithEnterWithInitial(overrideValue: Observable[String]): CustomEmitterBuilder[String, VDomModifier] = EmitterBuilder.ofModifier {
    (sink: String => Unit) =>
      val userInput = Handler.created[String]
      val writeValue = Observable.merge(userInput.map(_ => ""), overrideValue)
      VDomModifier(
          value <-- writeValue,
        //TODO WTF WHY DOES THAT NOT WORK?
        //          onEnter.value.filter(_.nonEmpty) --> userInput,
        onEnter.stopPropagation.map(_.currentTarget.asInstanceOf[dom.html.Input].value).filter(_.nonEmpty) --> userInput,
        managed(IO(userInput.foreach(sink)))
      )
    }

  def closeButton: VNode = div(
    div(cls := "fa-fw", freeSolid.faTimes),
    padding := "10px",
    Styles.flexStatic,
    cursor.pointer,
  )

}
