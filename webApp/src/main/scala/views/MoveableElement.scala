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

import scala.scalajs.js

object MoveableElement {
  sealed trait Position
  case class LeftPosition(left: Double, top: Double) extends Position
  case class RightPosition(right: Double, bottom: Double) extends Position

  def withToggleSwitch(title: String, toggle: Var[Boolean], enabled: Rx[Boolean], resizeEvent: Observable[Unit], initialPosition: Position, bodyModifier: Ownable[VDomModifier])(implicit ctx: Ctx.Owner): VDomModifier = {
    div(
      enabled.map {
        case true =>
          div(
            title,
            styles.extra.transform := "rotate(90deg)",
            onClick.stopPropagation.foreach { toggle.update(!_) },
            cursor.pointer,

            position.absolute,
            bottom := "100px",
            right := "0",
            backgroundColor := CommonStyles.sidebarBgColor,
            color.white,
            borderBottomRightRadius := "5px",
            borderBottomLeftRadius := "5px",
            padding := "3px",
            zIndex := ZIndex.overlayLow
          )
        case false =>
          VDomModifier.empty
      },
      apply(title, toggle, enabled, resizeEvent, initialPosition, bodyModifier)
    )
  }
  def apply(title: String, toggle: Var[Boolean], enabled: Rx[Boolean], resizeEvent: Observable[Unit], initialPosition: Position, bodyModifier: Ownable[VDomModifier])(implicit ctx: Ctx.Owner): VDomModifier = {
    var mouseDownOffset: Option[LeftPosition] = None
    var currentWidth: Option[Double] = None
    var currentHeight: Option[Double] = None
    var currentPosition: LeftPosition = null
    var domElem: dom.html.Element = null

    val show = Rx { toggle() && enabled() }

    def setPosition(): Unit = if (currentPosition != null && domElem != null) {
      val left = Math.max(0, Math.min(domElem.offsetParent.clientWidth - domElem.offsetWidth, currentPosition.left))
      val top = Math.max(0, Math.min(domElem.offsetParent.clientHeight - domElem.offsetHeight, currentPosition.top))
      domElem.style.left = left + "px"
      domElem.style.top = top + "px"
    }

    show.map {
      case true =>
        div.static(toggle.hashCode)(VDomModifier(
          cls := "moveable-window",

          div(
            Styles.flex,
            justifyContent.spaceBetween,
            backgroundColor := CommonStyles.sidebarBgColor,
            color := "white",

            b(title, paddingLeft := "5px"),
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
            bodyModifier,
            currentWidth.map(currentWidth => width := s"${currentWidth}px"),
            currentHeight.map(currentHeight => height := s"${currentHeight}px"),
          ),

          emitter(resizeEvent).async.foreach { setPosition() },
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
        if (domElem != null) {
          currentHeight = Some(domElem.offsetHeight)
          currentWidth = Some(domElem.offsetWidth)
        }
        VDomModifier.empty
    }
  }
}
