package wust.webApp.views

import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch._
import outwatch.dsl._
import colibri._
import colibri.ext.rx._
import rx._
import wust.css.{Styles, ZIndex}
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._

object MovableElement {
  sealed trait Position
  @inline final case class LeftPosition(left: Double, top: Double) extends Position
  @inline final case class RightPosition(right: Double, bottom: Double) extends Position

  final case class Window(
    title: VDomModifier,
    toggleLabel:VDomModifier,
    isVisible: Var[Boolean],
    initialPosition: Position,
    initialHeight: Int,
    initialWidth: Int,
    resizable: Boolean,
    titleModifier: Ownable[VDomModifier],
    bodyModifier: Ownable[VDomModifier]
  )

  def withToggleSwitch[F[_] : Source](windows: Seq[Window], enabled: Rx[Boolean], resizeEvent: F[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {
    val activeWindow = Var(0)
    div(
      enabled.map {
        case true =>
          div(
            Styles.flex,

            windows.zipWithIndex.map { case (window, index) =>
              div(
                Styles.flexStatic,
                cursor.pointer,
                marginLeft := "8px", // space between toggles

                onClick.stopPropagation.foreach {
                  if (window.isVisible.now) Var.set(
                    activeWindow -> index,
                    window.isVisible -> !window.isVisible.now
                  ) else window.isVisible() = !window.isVisible.now
                },
                onClick.use(index) --> activeWindow,
                window.isVisible.map {
                  case true => VDomModifier.empty
                  case false => opacity := 0.5
                },
                window.toggleLabel, // last because it can overwrite modifiers
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

  def apply[F[_] : Source](window: Window, enabled: Rx[Boolean], resizeEvent: F[Unit], index: Int, activeWindow: Var[Int])(implicit ctx: Ctx.Owner): VDomModifier = {
    import window._

    var mouseDownOffset: Option[LeftPosition] = None
    var currentWidth: Option[Double] = None
    var currentHeight: Option[Double] = None
    var currentPosition: LeftPosition = null
    var domElem: dom.html.Element = null
    var refElem : dom.Element= dom.document.getElementById("main-viewrender") // domElem.offsetParent
    var domElemBody: dom.html.Element = null

    val show = Rx { isVisible() && enabled() }

    def setPosition(): Unit = if (currentPosition != null && domElem != null) {
      val left = Math.max(0, Math.min(refElem.clientWidth - domElem.offsetWidth, currentPosition.left))
      val top = Math.max(0, Math.min(refElem.clientHeight - domElem.offsetHeight, currentPosition.top))
      domElem.style.left = left + "px"
      domElem.style.top = top + "px"
    }

    show.map {
      case true =>
        div.thunkStatic(isVisible.hashCode)(VDomModifier(
          cls := "movable-window",

          Styles.flex,
          flexDirection.column,
          width := s"${initialWidth}px",
          height := s"${initialHeight}px",
          VDomModifier.ifTrue(resizable)(resize := "both"),

          zIndex <-- activeWindow.map { activeWindow =>
            if (activeWindow == index) ZIndex.overlayLow + 1 else ZIndex.overlayLow
          },

          onMouseDown.stopPropagation.use(index) --> activeWindow,

          div(
            cls := "movable-window-title",
            title,
            div(cls := "fa-fw", freeSolid.faTimes, cursor.pointer, onClick.use(false) --> isVisible),

            onMouseDown.stopPropagation.foreach { ev =>
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
            titleModifier,
          ),

          div(
            onMouseDown.stopPropagation.use(index) --> activeWindow, // in case other mouseDown events are stopped
            Styles.growFull,
            bodyModifier,
            onDomMount.asHtml.foreach(domElemBody = _),
            currentWidth.map(currentWidth => width := s"${currentWidth}px"),
            currentHeight.map(currentHeight => height := s"${currentHeight}px"),
          ),

          emitter(resizeEvent).delayMillis(200).foreach { setPosition() }, // delay a bit, so that any rendering from the resize event as actually done.
          emitter(events.window.onResize).foreach { setPosition() },
          onDomMount.foreach { elem =>
            domElem = elem.asInstanceOf[dom.html.Element]
            if (currentPosition == null) {
              currentPosition = initialPosition match {
                case p: LeftPosition => p
                case p: RightPosition =>
                  val left = refElem.clientWidth - p.right - domElem.offsetWidth
                  val top = refElem.clientHeight - p.bottom - domElem.offsetHeight
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
