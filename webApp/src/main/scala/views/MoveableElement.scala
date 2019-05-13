package wust.webApp.views

import fontAwesome.freeSolid
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.webApp.Ownable
import wust.webApp.outwatchHelpers._
import scala.concurrent.duration._

import scala.scalajs.js

object MoveableElement {
  sealed trait Position
  case class LeftPosition(left: Double, top: Double) extends Position
  case class RightPosition(right: Double, bottom: Double) extends Position

  case class Window(title: VDomModifier, toggle: Var[Boolean], initialPosition: Position, initialHeight: Int, initialWidth: Int, resizable: Boolean, bodyModifier: Ownable[VDomModifier])

  def withToggleSwitch(windows: Seq[Window], enabled: Rx[Boolean], resizeEvent: Observable[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {
    val activeWindow = Var(0)
    div(
      enabled.map {
        case true =>
          div(
            zIndex := ZIndex.overlayMiddle,
            styles.extra.transform := "rotate(90deg)",
            styles.extra.transformOrigin := "top right",
            position.absolute,
            bottom := "100px",
            right := "0",
            color.white,
            Styles.flex,

            windows.zipWithIndex.map { case (window, index) =>
              div(
                onClick.stopPropagation.foreach {
                  if (window.toggle.now) Var.set(
                    activeWindow -> index,
                    window.toggle -> !window.toggle.now
                  ) else window.toggle() = !window.toggle.now
                },
                onClick(index) --> activeWindow,
                cursor.pointer,
                padding := "5px",
                border := "0px 1px 1px 1px white solid",
                backgroundColor := CommonStyles.sidebarBgColor,
                window.toggle.map {
                  case true => VDomModifier.empty
                  case false => opacity := 0.6
                },
                borderBottomRightRadius := "5px",
                borderBottomLeftRadius := "5px",
                window.title,
              )
            }
          )
        case false =>
          VDomModifier.empty
      },
      windows.zipWithIndex.map { case (window, index) =>
        apply(window, enabled, resizeEvent, index, activeWindow)
      }
    )
  }

  def apply(window: Window, enabled: Rx[Boolean], resizeEvent: Observable[Unit], index: Int, activeWindow: Var[Int])(implicit ctx: Ctx.Owner): VDomModifier = {
    import window._

    var mouseDownOffset: Option[LeftPosition] = None
    var currentWidth: Option[Double] = None
    var currentHeight: Option[Double] = None
    var currentPosition: LeftPosition = null
    var domElem: dom.html.Element = null
    var domElemBody: dom.html.Element = null

    val show = Rx { toggle() && enabled() }

    def setPosition(): Unit = if (currentPosition != null && domElem != null) {
      val left = Math.max(0, Math.min(domElem.offsetParent.clientWidth - domElem.offsetWidth, currentPosition.left))
      val top = Math.max(0, Math.min(domElem.offsetParent.clientHeight - domElem.offsetHeight, currentPosition.top))
      domElem.style.left = left + "px"
      domElem.style.top = top + "px"
    }

    show.map {
      case true =>
        div.thunkStatic(toggle.hashCode)(VDomModifier(
          cls := "moveable-window",

          Styles.flex,
          flexDirection.column,
          width := s"${initialWidth}px",
          height := s"${initialHeight}px",
          VDomModifier.ifTrue(resizable)(resize := "both"),

          zIndex <-- activeWindow.map { activeWindow =>
            if (activeWindow == index) ZIndex.overlayLow + 1 else ZIndex.overlayLow
          },

          onMouseDown(index) --> activeWindow,

          div(
            Styles.flex,
            justifyContent.spaceBetween,
            alignItems.center,
            backgroundColor := CommonStyles.sidebarBgColor,
            color := "white",
            padding := "2px",

            title,
            div(cls := "fa-fw", freeSolid.faMinus, cursor.pointer, onClick(false) --> toggle),

            onMouseDown.foreach { ev =>
              mouseDownOffset = Some(LeftPosition(left = domElem.offsetLeft - ev.clientX, top = domElem.offsetTop - ev.clientY))
            },
            emitter(events.document.onMouseUp).foreach {
              mouseDownOffset = None
            },
            emitter(events.document.onMouseMove).foreach { ev =>
              mouseDownOffset.foreach { offset =>
                ev.preventDefault()
                currentPosition = LeftPosition(left = ev.clientX + offset.left, top = ev.clientY + offset.top)
                setPosition()
              }
            },
          ),

          div(
            Styles.growFull,
            bodyModifier,
            onDomMount.asHtml.foreach(domElemBody = _),
            currentWidth.map(currentWidth => width := s"${currentWidth}px"),
            currentHeight.map(currentHeight => height := s"${currentHeight}px"),
          ),

          emitter(resizeEvent.delayOnNext(200 millis)).foreach { setPosition() }, // delay a bit, so that any rendering from the resize event as actually done.
          emitter(events.window.onResize).foreach { setPosition() },
          onDomMount.foreach { elem =>
            domElem = elem.asInstanceOf[dom.html.Element]
            if (currentPosition == null) {
              currentPosition = initialPosition match {
                case p: LeftPosition => p
                case p: RightPosition =>
                  val left = domElem.offsetParent.clientWidth - p.right - domElem.offsetWidth
                  val top = domElem.offsetParent.clientHeight - p.bottom - domElem.offsetHeight
                  LeftPosition(left = left, top = top)
              }
            }
            setPosition()
          },
        ))
      case false =>
        if (domElemBody != null) {
          currentHeight = Some(domElemBody.offsetHeight)
          currentWidth = Some(domElemBody.offsetWidth)
        }
        VDomModifier.empty
    }
  }
}
