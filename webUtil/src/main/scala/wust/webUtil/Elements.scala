package wust.webUtil

import cats.effect.IO
import fontAwesome.{IconLookup, Params, Transform, fontawesome, freeSolid}
import wust.facades.fomanticui.AutoResizeConfig
import wust.facades.dateFns.DateFns
import wust.facades.hammerjs
import wust.facades.immediate.immediate
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import org.scalajs.dom.{KeyboardEvent, MouseEvent}
import outwatch.ProHandler
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder, PropBuilder, SyncEmitterBuilder}
import rx._
import wust.facades.emojijs.EmojiConvertor
import wust.facades.marked.Marked
import wust.facades.dompurify.DOMPurify
import wust.webUtil.outwatchHelpers._
import wust.ids.EpochMilli
import wust.util._

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import wust.facades.dompurify.DomPurifyConfig

// This file contains utilities that are not woost-related.
// They could be contributed to outwatch and used in other projects

object Elements {
  final case class UnsafeHTML(html: String) extends AnyVal
  val innerHTML: PropBuilder[UnsafeHTML] = prop[UnsafeHTML]("innerHTML", _.html)

  def scrollToBottom(elem: dom.Element): Unit = {
    //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
    try {
      elem.scrollTop = elem.scrollHeight - elem.clientHeight
    } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
  }

  final class ScrollBottomHandler(initialScrollToBottom:Boolean = true) {
    val scrollableElem: Var[Option[HTMLElement]] = Var(None)
    val isScrolledToBottom = Var[Boolean](initialScrollToBottom)

    val scrollToBottomInAnimationFrame: () => Unit = requestSingleAnimationFrame {
      scrollableElem.now.foreach { elem =>
        scrollToBottom(elem)
      }
    }

    def isScrolledToBottomNow: Boolean = scrollableElem.now.fold(true){ elem =>
      elem.scrollHeight - elem.clientHeight <= elem.scrollTop + 11
    } // at bottom + 10 px tolerance

    def modifier(implicit ctx: Ctx.Owner) = VDomModifier(
      onScroll foreach {
        isScrolledToBottom() = isScrolledToBottomNow
      },
      onDomUpdate foreach {
        if (isScrolledToBottom.now) scrollToBottomInAnimationFrame()
      },
      onDomMount.asHtml foreach { elem =>
        scrollableElem() = Some(elem)
        if(initialScrollToBottom) scrollToBottomInAnimationFrame()
      },
    )
  }

  def onClickPreventDefaultExceptCtrl(action: => Unit):VDomModifier = {
    // on middle click / ctrl+click opens new tab with `href`
    // https://jsfiddle.net/53njtdg6/1/
    onClick.foreach { e:dom.MouseEvent =>
      if(!e.ctrlKey) {
        e.preventDefault()
        action
      }
    }
  }


  val onEnter: SyncEmitterBuilder[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(e => e.keyCode == KeyCode.Enter && !e.shiftKey)
      .preventDefault

  val onCtrlEnter: SyncEmitterBuilder[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(e => e.keyCode == KeyCode.Enter && e.ctrlKey && !e.shiftKey)
      .preventDefault

  val onEscape: SyncEmitterBuilder[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(_.keyCode == KeyCode.Escape)
      .preventDefault

  val onGlobalEscape: EmitterBuilder[KeyboardEvent, VDomModifier] =
    if (BrowserDetect.isMobile) EmitterBuilder.empty else EmitterBuilder.fromObservable(events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape))

  val onGlobalClick: EmitterBuilder[MouseEvent, VDomModifier] =
    EmitterBuilder.fromObservable(events.document.onClick)

  val onGlobalMouseDown: EmitterBuilder[MouseEvent, VDomModifier] =
    EmitterBuilder.fromObservable(events.document.onMouseDown)

  val onClickOrLongPress: CustomEmitterBuilder[Boolean, VDomModifier] =
    EmitterBuilder.ofModifier[Boolean] { sink => IO {
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
    }}

  def topBanner(desktopText: => Option[VNode], mobileText: => Option[VNode]): VDomModifier = {
    val text = if(BrowserDetect.isPhone) mobileText orElse desktopText else desktopText
    text match {
      case Some(bannerText) =>
        VDomModifier(
          cls := "topBanner",
          bannerText,
        )
      case _                =>
        VDomModifier.empty
    }
  }

  def onHammer(events: String):CustomEmitterBuilder[hammerjs.Event, VDomModifier] = {
    import wust.facades.hammerjs.{CssProps, Hammer, Options, propagating}
    EmitterBuilder.ofModifier[hammerjs.Event] { sink =>
      managedElement.asHtml { elem =>
        elem.asInstanceOf[js.Dynamic].hammer = js.undefined
        var hammertime = new Hammer[hammerjs.Event](elem, new Options { cssProps = new CssProps { userSelect = "auto"}} )
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

  val copiableToClipboard: VDomModifier = {
    import wust.facades.clipboardjs.ClipboardJS

    VDomModifier(
      managedElement.asHtml { elem =>
        val clip = new ClipboardJS(elem)
        Cancelable(() => clip.destroy())
      }
    )
  }

  val onTap: CustomEmitterBuilder[hammerjs.Event, VDomModifier] = onHammer("tap")
  val onPress: CustomEmitterBuilder[hammerjs.Event, VDomModifier] = onHammer("press")
  val onSwipeRight: CustomEmitterBuilder[hammerjs.Event, VDomModifier] = onHammer("swiperight")
  val onSwipeLeft: CustomEmitterBuilder[hammerjs.Event, VDomModifier] = onHammer("swipeleft")

  def readPropertyFromElement[T](elem: dom.html.Element, propName: String): js.UndefOr[T] = {
    for {
      elem <- elem.asInstanceOf[js.UndefOr[dom.html.Element]]
      valueProvider <- elem.asInstanceOf[js.Dynamic].selectDynamic(propName).asInstanceOf[js.UndefOr[() => T]]
    } yield valueProvider()
  }

  def writePropertyIntoElement(elem: dom.html.Element, propName: String, value: => Any): Unit = {
    elem.asInstanceOf[js.Dynamic].updateDynamic(propName)((() => value).asInstanceOf[js.Any])
  }

  @inline def defer(code: => Unit): Unit = {
//    dom.window.setTimeout(() => code, timeout = 0)
    immediate(() => code)
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

  final class ValueWithEnter(overrideValue: Observable[String] = Observable.empty, clearValue: Boolean = true, eventHandler: SyncEmitterBuilder[dom.KeyboardEvent, VDomModifier] = onEnter) {
    private var elem:HTMLInputElement = _

    private val userInput = Handler.unsafe[String]
    private val clearInput = if (clearValue) Handler.unsafe[Unit].mapObservable(_ => "") else ProHandler(Observer.empty, Observable.empty)
    private val writeValue = Observable(clearInput, overrideValue).merge

    def trigger(): Unit = {
      // We clear input field before userInput is triggered
      val value = elem.value
      clearInput.onNext(()).foreach { _ =>
        userInput.onNext(value)
      }
    }

    val emitterBuilder: CustomEmitterBuilder[String, VDomModifier] = EmitterBuilder.ofModifier[String] { sink =>
      VDomModifier(
        onDomMount.asHtml.foreach { textAreaElem =>
          elem = textAreaElem.asInstanceOf[HTMLInputElement]
        },
        value <-- writeValue,
        eventHandler.stopPropagation foreach { trigger() },
        emitter(userInput) --> sink
      )
    }
  }

  val integerInputMod = VDomModifier(
      tpe := "number",
      step := "1",
      min := Int.MinValue.toString,
      max := Int.MaxValue.toString,
      placeholder := "Integer Number",
    )

  val decimalInputMod = VDomModifier(
    tpe := "number",
    step := "any",
    min := Double.MinValue.toString,
    max := Double.MaxValue.toString,
    placeholder := "Decimal Number",
  )

  val dateInputMod = VDomModifier(
    tpe := "date",
  )

  val timeInputMod = VDomModifier(
    tpe := "time",
  )

  val durationInputMod = VDomModifier(
    tpe := "text",
    placeholder := "1y 1mo 1w 1d 2h 3m 4s ...",
  )

  val textInputMod = VDomModifier(
    tpe := "text",
    placeholder := "Text",
  )

  val fileInputMod = VDomModifier(
    tpe := "file"
  )

  def dateString(epochMilli: EpochMilli): String = {
    val createdDate = new js.Date(epochMilli)
    if(DateFns.differenceInCalendarDays(new js.Date, createdDate) > 0)
      DateFns.format(new js.Date(epochMilli), "Pp") // localized date and time
    else
      DateFns.format(new js.Date(epochMilli), "p") // localized only time
  }

  def creationDate(created: EpochMilli): VDomModifier = {
    (created != EpochMilli.min).ifTrue[VDomModifier](
      div(
        cls := "chatmsg-date",
        flexGrow := 0.0,
        flexShrink := 0.0,
        dateString(created),
      )
    )
  }

  def valueWithEnter: CustomEmitterBuilder[String, VDomModifier] = valueWithEnter(true)
  def valueWithCtrlEnter: CustomEmitterBuilder[String, VDomModifier] = valueWithCtrlEnter(true)
  def valueWithEnter(clearValue: Boolean, filterEvent: () => Boolean = () => true): CustomEmitterBuilder[String, VDomModifier] = (new ValueWithEnter(clearValue = clearValue, eventHandler = onEnter.filter(_ => filterEvent()))).emitterBuilder
  def valueWithCtrlEnter(clearValue: Boolean, filterEvent: () => Boolean = () => true): CustomEmitterBuilder[String, VDomModifier] = (new ValueWithEnter(clearValue = clearValue, eventHandler = onCtrlEnter.filter(_ => filterEvent()))).emitterBuilder
  def valueWithEnterWithInitial(overrideValue: Observable[String], clearValue: Boolean = true, filterEvent: () => Boolean = () => true): CustomEmitterBuilder[String, VDomModifier] = {
    new ValueWithEnter(
      overrideValue = overrideValue,
      clearValue = clearValue,
      eventHandler = onEnter.filter(_ => filterEvent())
    ).emitterBuilder 
  }

  def valueWithCtrlEnterWithInitial(overrideValue: Observable[String], clearValue: Boolean = true, filterEvent: () => Boolean = () => true): CustomEmitterBuilder[String, VDomModifier] = {
    new ValueWithEnter(
      overrideValue = overrideValue,
      clearValue = clearValue,
      eventHandler = onCtrlEnter.filter(_ => filterEvent())
    ).emitterBuilder
  }

  def escapeHtml(content: String): String = {
    // assure html in text is escaped by creating a text node, appending it to an element and reading the escaped innerHTML.
    val text = dom.window.document.createTextNode(content)
    val wrap = dom.window.document.createElement("div")
    wrap.appendChild(text)
    wrap.innerHTML
  }

  def onClickN(desiredClicks: Int) = EmitterBuilder.ofModifier[Unit] { sink =>
    import scala.concurrent.duration._

    IO {
      var clickCounter = 0

      VDomModifier(
        onClick.stopPropagation.foreach {
          clickCounter += 1
          if (clickCounter == desiredClicks) {
            sink.onNext(())
            clickCounter = 0
          }
        },
        onClick.debounce(500 millis).foreach { clickCounter = 0 }
      )
    }
  }

  val safeRelForTargetBlank = "noopener noreferrer"

  // https://www.jitbit.com/alexblog/256-targetblank---the-most-underestimated-vulnerability-ever/
  val safeTargetBlank = VDomModifier(
    rel := safeRelForTargetBlank,
    target := "_blank"
  )

  def markdownString(str: String): String = {
    if(str.trim.isEmpty) "<p></p>" // add least produce an empty paragraph to preserve line-height
    else {
      EmojiConvertor.replace_colons(
        DOMPurify.sanitize(
          Marked(EmojiConvertor.replace_emoticons_with_colons(str)),
        )
      )
    }
  }
  def closeButton: VNode = div(
    div(cls := "fa-fw", freeSolid.faTimes),
    padding := "10px",
    flexGrow := 0.0,
    flexShrink := 0.0,
    cursor.pointer,
  )
  def iconWithIndicator(icon: IconLookup, indicator: IconLookup, color: String): VNode = fontawesome.layered(
    fontawesome.icon(icon),
    fontawesome.icon(
      indicator,
      new Params {
        transform = new Transform {size = 13.0; x = 7.0; y = -7.0; }
        styles = scalajs.js.Dictionary[String]("color" -> color)
      }
    )
  )
  def icon(icon: VDomModifier) = i(
    cls := "icon fa-fw",
    icon
  )

  def confirm(message:String)(code: => Unit):Unit = {
    if(dom.window.confirm(message))
      code
  }

  def autoresizeTextareaMod: VDomModifier = autoresizeTextareaMod()
  def autoresizeTextareaMod(maxHeight: Option[Int] = None, onResize: Option[() => Unit] = None): VDomModifier = {
    val _maxHeight = maxHeight.map(_.toDouble).orUndefined
    val _onResize = onResize.map[js.ThisFunction1[dom.html.TextArea, Double, Unit]](f => (_: dom.html.TextArea, _: Double) => f()).orUndefined

    VDomModifier(
      managedElement.asJquery { e =>
        val subscription = e.autoResize(new AutoResizeConfig { maxHeight = _maxHeight; onresizeheight = _onResize })
        Cancelable(() => subscription.reset())
      },
      resize := "none",
      height := "0px"
    )
  }
}
