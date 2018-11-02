package wust.webApp.views

import cats.effect.IO
import emojijs.EmojiConvertor
import fontAwesome.freeSolid
import marked.Marked
import monix.execution.Cancelable
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import org.scalajs.dom.{KeyboardEvent, MouseEvent}
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder, SyncEmitterBuilder}
import wust.css.Styles
import wust.webApp.BrowserDetect
import wust.webApp.outwatchHelpers._

import scala.scalajs.js

// This file contains utilities that are not woost-related.
// They could be contributed to outwatch and used in other projects

object Elements {

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
    EmitterBuilder.ofModifier[dom.KeyboardEvent] { sink =>
      if (BrowserDetect.isMobile) VDomModifier.empty
      else managed(() => events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape) subscribe sink)
    }

  val onGlobalClick: CustomEmitterBuilder[MouseEvent, VDomModifier] =
    EmitterBuilder.ofModifier[dom.MouseEvent] { sink =>
      managed(() => events.document.onClick subscribe sink)
    }

  val onClickOrLongPress: CustomEmitterBuilder[Boolean, VDomModifier] =
    EmitterBuilder.ofModifier[Boolean] { sink =>
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
          sink.onNext(false) // click
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
            sink.onNext(true) // long click
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
        onClick foreach { click _ },
        eventProp("touchmove") foreach { updateCurrentPosition _ },
        eventProp("touchstart") foreach { start _ },
        eventProp("touchend") foreach {cancel()},
        eventProp("touchleave") foreach {cancel()},
        eventProp("touchcancel") foreach {cancel()},
      )
    }

  def onHammer(events: String):CustomEmitterBuilder[hammerjs.Event, VDomModifier] = {
    import hammerjs._
    EmitterBuilder.ofModifier[hammerjs.Event] { sink =>
      managedElement.asHtml { elem =>
        elem.asInstanceOf[js.Dynamic].hammer = js.undefined
        var hammertime = new Hammer[Event](elem, new Options { cssProps = new CssProps { userSelect = "auto"}} )
        propagating(hammertime).on(events, { e =>
          e.stopPropagation()
          // if(e.target == elem)
          sink.onNext(e)
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

  // https://github.com/zzarcon/default-passive-events#is-there-a-possibility-to-bring-default-addeventlistener-method-back-for-chosen-elementsglobally-eg-for-time-of-running-some-of-the-code
  val withoutDefaultPassiveEvents = onDomMount.foreach { elem =>
    elem
      .asInstanceOf[js.Dynamic].addEventListener
      ._original.asInstanceOf[js.UndefOr[js.Dynamic]]
      .foreach { orig =>
        elem.asInstanceOf[js.Dynamic].updateDynamic("addEventListener")(orig)
    }
  }


  def valueWithEnter: CustomEmitterBuilder[String, VDomModifier] = valueWithEnterWithInitial(Observable.empty)
  def valueWithEnterWithInitial(overrideValue: Observable[String]): CustomEmitterBuilder[String, VDomModifier] = EmitterBuilder.ofModifier[String] {
    sink =>
      val userInput = Handler.unsafe[String]
      val clearInput = Handler.unsafe[Unit].mapObservable(_ => "")
      val writeValue = Observable(clearInput, overrideValue).merge
      VDomModifier(
          value <-- writeValue,
        onEnter.stopPropagation.value.filter(_.nonEmpty) foreach { value =>
          // We clear input field before userInput is triggered
          clearInput.onNext(())
          userInput.onNext(value)
        },
        managed(() => userInput subscribe sink)
      )
    }

  def closeButton: VNode = div(
    div(cls := "fa-fw", freeSolid.faTimes),
    padding := "10px",
    Styles.flexStatic,
    cursor.pointer,
  )


  def markdownVNode(str: String) = div(div(prop("innerHTML") := markdownString(str))) // intentionally double wrapped. Because innerHtml does not compose with other modifiers
  def markdownString(str: String): String = EmojiConvertor.replace_unified(EmojiConvertor.replace_colons(Marked(EmojiConvertor.replace_emoticons_with_colons(str))))

  def escapeHtml(content: String): String = {
    // assure html in text is escaped by creating a text node, appending it to an element and reading the escaped innerHTML.
    val text = dom.window.document.createTextNode(content)
    val wrap = dom.window.document.createElement("div")
    wrap.appendChild(text)
    wrap.innerHTML
  }
}
