package wust.webUtil

import typings.chartDotJs.chartDotJsMod.ChartConfiguration
import fontAwesome.{IconLookup, Params, Transform, fontawesome, freeSolid}
import wust.facades.dateFns.DateFns
import wust.facades.hammerjs
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import org.scalajs.dom.{KeyboardEvent, MouseEvent}
import rx._
import wust.facades.emojijs.EmojiConvertor
import wust.facades.marked.Marked
import wust.facades.dompurify.DOMPurify
import wust.webUtil.outwatchHelpers._
import wust.ids.EpochMilli
import wust.util._

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import outwatch.reactive.handler._
import outwatch.dom.helpers.{EmitterBuilder, PropBuilder}

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


  val onEnter: EmitterBuilder.Sync[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(e => e.keyCode == KeyCode.Enter && !e.shiftKey)
      .preventDefault

  val onCtrlEnter: EmitterBuilder.Sync[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(e => e.keyCode == KeyCode.Enter && e.ctrlKey && !e.shiftKey)
      .preventDefault

  val onEscape: EmitterBuilder.Sync[dom.KeyboardEvent, VDomModifier] =
    onKeyDown
      .filter(_.keyCode == KeyCode.Escape)
      .preventDefault

  val onGlobalEscape: EmitterBuilder[KeyboardEvent, VDomModifier] =
    if (BrowserDetect.isMobile) EmitterBuilder.empty else EmitterBuilder.fromSource(events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape))

  val onGlobalClick: helpers.EmitterBuilderExecution[MouseEvent, VDomModifier, helpers.EmitterBuilder.Execution] =
    EmitterBuilder.fromSource(events.document.onClick)

  val onGlobalMouseDown: EmitterBuilder[MouseEvent, VDomModifier] =
    EmitterBuilder.fromSource(events.document.onMouseDown)

  val onClickOrLongPress: EmitterBuilder.Sync[Boolean, VDomModifier] =
    EmitterBuilder[Boolean, VDomModifier] { sink => VDomModifier.delay {
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

  def onHammer(events: String):EmitterBuilder.Sync[hammerjs.Event, VDomModifier] = {
    import wust.facades.hammerjs.{CssProps, Hammer, Options, propagating}
    EmitterBuilder[hammerjs.Event, VDomModifier] { sink =>
      managedElement.asHtml { elem =>
        elem.asInstanceOf[js.Dynamic].hammer = js.undefined
        var hammertime = new Hammer[hammerjs.Event](elem, new Options { cssProps = new CssProps { userSelect = "auto"}} )
        propagating(hammertime).on(events, { e =>
          e.stopPropagation()
          // if(e.target == elem)
          sink.onNext(e)
        })

        cancelable { () =>
          hammertime.stop()
          hammertime.destroy()
          elem.asInstanceOf[js.Dynamic].hammer = js.undefined
        }
      }
    }
  }

  def copiableToClipboard(text: String): VDomModifier = {
    import wust.facades.clipboardjs.ClipboardJS

    VDomModifier(
      dataAttr("clipboard-text") := text,
      managedElement.asHtml { elem =>
        val clip = new ClipboardJS(elem)
        cancelable(() => clip.destroy())
      }
    )
  }

  val onTap: EmitterBuilder.Sync[hammerjs.Event, VDomModifier] = onHammer("tap")
  val onPress: EmitterBuilder.Sync[hammerjs.Event, VDomModifier] = onHammer("press")
  val onSwipeRight: EmitterBuilder.Sync[hammerjs.Event, VDomModifier] = onHammer("swiperight")
  val onSwipeLeft: EmitterBuilder.Sync[hammerjs.Event, VDomModifier] = onHammer("swipeleft")

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
    // dom.window.asInstanceOf[js.Dynamic].setImmediate(() => code)
    dom.window.setTimeout(() => code, timeout = 0)
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

  final class ValueWithEnter(overrideValue: SourceStream[String] = SourceStream.empty, clearValue: Boolean = true, eventHandler: EmitterBuilder.Sync[dom.KeyboardEvent, VDomModifier] = onEnter) {
    private var elem:HTMLInputElement = _

    private val userInput = Handler.unsafe[String]
    private val clearInput = if (clearValue) Handler.unsafe[Unit] else ProHandler(SinkObserver.empty, SourceStream.empty)
    private val writeValue = SourceStream.merge(clearInput.map(_ => ""), overrideValue)

    def trigger(): Unit = {
      // We clear input field before userInput is triggered
      val value = elem.value
      clearInput.onNext(())
      userInput.onNext(value)
    }

    val emitterBuilder: EmitterBuilder.Sync[String, VDomModifier] = EmitterBuilder[String, VDomModifier] { sink =>
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

  def valueWithEnter: EmitterBuilder.Sync[String, VDomModifier] = valueWithEnter(true)
  def valueWithCtrlEnter: EmitterBuilder.Sync[String, VDomModifier] = valueWithCtrlEnter(true)
  def valueWithEnter(clearValue: Boolean, filterEvent: () => Boolean = () => true): EmitterBuilder.Sync[String, VDomModifier] = (new ValueWithEnter(clearValue = clearValue, eventHandler = onEnter.filter(_ => filterEvent()))).emitterBuilder
  def valueWithCtrlEnter(clearValue: Boolean, filterEvent: () => Boolean = () => true): EmitterBuilder.Sync[String, VDomModifier] = (new ValueWithEnter(clearValue = clearValue, eventHandler = onCtrlEnter.filter(_ => filterEvent()))).emitterBuilder
  def valueWithEnterWithInitial(overrideValue: SourceStream[String], clearValue: Boolean = true, filterEvent: () => Boolean = () => true): EmitterBuilder.Sync[String, VDomModifier] = {
    new ValueWithEnter(
      overrideValue = overrideValue,
      clearValue = clearValue,
      eventHandler = onEnter.filter(_ => filterEvent())
    ).emitterBuilder
  }

  def valueWithCtrlEnterWithInitial(overrideValue: SourceStream[String], clearValue: Boolean = true, filterEvent: () => Boolean = () => true): EmitterBuilder.Sync[String, VDomModifier] = {
    new ValueWithEnter(
      overrideValue = overrideValue,
      clearValue = clearValue,
      eventHandler = onCtrlEnter.filter(_ => filterEvent())
    ).emitterBuilder
  }

  final class TextAreaAutoResizer(callback: Int => Unit = (_:Int) => ()) {
    // https://stackoverflow.com/questions/454202/creating-a-textarea-with-auto-resize/25621277#25621277
    var elem:HTMLElement = _
    var lastScrollHeight: Int = 0

    val trigger: () => Unit = requestSingleAnimationFrame {
      if(lastScrollHeight != elem.scrollHeight) {
        elem.style.height = "auto" // fixes the behaviour of scrollHeight
        lastScrollHeight = elem.scrollHeight
        val newHeight = lastScrollHeight + 4 // 4 avoids a scrollbar
        elem.style.height = newHeight + "px"
        callback(newHeight)
      }
    }

    val modifiers = VDomModifier(
      onDomMount.asHtml --> inNextAnimationFrame[dom.html.Element] { textAreaElem =>
        elem = textAreaElem
        elem.style.height = "auto" // fixes the behaviour of scrollHeight
        lastScrollHeight = elem.scrollHeight
        val newHeight = lastScrollHeight + 4 // 4 avoids a scrollbar
        elem.style.height = newHeight + "px"
        callback(newHeight)
      },
      onInput.foreach { trigger() },
      onKeyDown.filter(e => e.keyCode == KeyCode.Enter).foreach{ trigger() },
    )
  }


  def escapeHtml(content: String): String = {
    // assure html in text is escaped by creating a text node, appending it to an element and reading the escaped innerHTML.
    val text = dom.window.document.createTextNode(content)
    val wrap = dom.window.document.createElement("div")
    wrap.appendChild(text)
    wrap.innerHTML
  }

  def onClickN(desiredClicks: Int) = EmitterBuilder[Unit, VDomModifier] { sink =>
    import scala.concurrent.duration._

    VDomModifier.delay {
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

  def onClickDefault:EmitterBuilder.Sync[com.raquo.domtypes.jsdom.defs.events.TypedTargetMouseEvent[org.scalajs.dom.Element],outwatch.dom.VDomModifier] = onClick.stopPropagation.mapResult(mod => VDomModifier(mod, cursor.pointer))
  val safeRelForTargetBlank = "noopener noreferrer"

  // https://www.jitbit.com/alexblog/256-targetblank---the-most-underestimated-vulnerability-ever/
  val safeTargetBlank = VDomModifier(
    rel := safeRelForTargetBlank,
    target := "_blank"
  )

  def markdownString(str: String): String = {
    if(str.trim.isEmpty) "<p></p>" // add least produce an empty paragraph to preserve line-height
    else {
      EmojiConvertor.replace_colons_safe(
        DOMPurify.sanitize(
          Marked(EmojiConvertor.replace_emoticons_with_colons_safe(str)),
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

  // create a canvas with a chart using chart.js
  //TODO how to update data efficiently
  // Example:
  // Elements.chartCanvas {
  //   import typings.chartDotJs.chartDotJsMod._
  //
  //   ChartConfiguration {
  //     `type` = "bar"
  //     data = new ChartData {
  //       labels = js.Array[String | js.Array[String]]("Red", "Blue", "Yellow", "Green", "Purple", "Orange")
  //       datasets = js.Array(new ChartDataSets {
  //         label = "# of Votes"
  //         data = js.Array[js.UndefOr[ChartPoint | Double | Null]](12, 19, 3, 5, 2, 3)
  //         backgroundColor = js.Array(
  //           "rgba(255, 99, 132, 0.2)",
  //           "rgba(54, 162, 235, 0.2)",
  //           "rgba(255, 206, 86, 0.2)",
  //           "rgba(75, 192, 192, 0.2)",
  //           "rgba(153, 102, 255, 0.2)",
  //           "rgba(255, 159, 64, 0.2)"
  //         )
  //         borderColor = js.Array(
  //           "rgba(255, 99, 132, 1)",
  //           "rgba(54, 162, 235, 1)",
  //           "rgba(255, 206, 86, 1)",
  //           "rgba(75, 192, 192, 1)",
  //           "rgba(153, 102, 255, 1)",
  //           "rgba(255, 159, 64, 1)"
  //         )
  //         borderWidth = 1
  //       })
  //     }
  //     options = new ChartOptions {
  //       scales = new ChartScales {
  //         yAxes = js.Array(new ChartYAxe {
  //           ticks = new TickOptions {
  //             beginAtZero = true
  //           }
  //         })
  //       }
  //     }
  //   }
  // },
  def chartCanvas(configuration: ChartConfiguration): VNode = {
    canvas(
      managedElement { elem =>
        val context = elem.asInstanceOf[dom.html.Canvas].getContext("2d").asInstanceOf[typings.std.CanvasRenderingContext2D]
        val chart = new typings.chartDotJs.chartDotJsMod.^(context, configuration)

        cancelable(() => chart.destroy())
      }
    )
  }

  // https://stackoverflow.com/questions/28983016/how-to-paste-rich-text-from-clipboard-to-html-textarea-element
  val onPasteHtmlOrTextIntoValue: VDomModifier = {
    // parse html from clipboard if available (useful to pasting rich text). If clipboardData not available, or
    // html data is not available, we fallback to the normal paste event. Otherwise prevent default and fill
    // the value of this element.
    // we have a heuristic which kind of text we want to interpret as html. For one-line texts, we ignore html formatting,
    // becasue otherwise we get weird html when pasting links. Only multi-line texts are interpreted as html if html available.
    onPaste.foreach { event =>
      if (event.clipboardData != js.undefined) {
          val htmlText = event.clipboardData.getData("text/html")
          if (htmlText != js.undefined && htmlText != null && htmlText.linesIterator.length > 1) {
            event.preventDefault()
            event.currentTarget.asInstanceOf[dom.html.Input].value = htmlText
          }
      }
    }
  }
}
