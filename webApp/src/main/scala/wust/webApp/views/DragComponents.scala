package wust.webApp.views

import monix.execution.Cancelable
import monix.reactive.Observer
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import outwatch.dom.helpers.EmitterBuilder
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webApp.dragdrop._
import wust.webApp.state.GlobalState

import scala.scalajs.js
import snabbdom.VNodeProxy

object DragComponents {
  def onAfterPayloadWasDragged: EmitterBuilder[Unit, VDomModifier] =
    EmitterBuilder.ofModifier[Unit] { sink =>
      VDomModifier.delay {
        prop(DragItem.draggedActionPropName) := (() => () => sink.onNext(Unit))
      }
    }
  @inline def registerDragContainer: VDomModifier = registerDragContainer()
  def registerDragContainer(container: DragContainer = DragContainer.Default): VDomModifier = {
    var proxy: VNodeProxy = null
    VDomModifier(
      //          border := "2px solid violet",
      outline := "none", // hides focus outline
      container match {
        case _:SortableContainer => cls := "sortable-container"
        case _ => cls := "draggable-container"
      },
      snabbdom.VNodeProxy.repairDomBeforePatch, // draggable modifies the dom, but snabbdom assumes that the dom corresponds to its last vdom representation. So Before patch

      DomMountHook { proxy = _ },
      managedFunction(() => container.triggerRepair.foreach { _ =>
        if(proxy != null) VNodeProxy.repairDom(proxy)
      }),

      prop(DragContainer.propName) := (() => container),
      managedElement.asHtml { elem =>
        SortableEvents.sortable.addContainer(elem)
        Cancelable { () => SortableEvents.sortable.removeContainer(elem) }
      }
    )
  }
  def readDragTarget(elem: dom.html.Element): js.UndefOr[DragTarget] = {
    readPropertyFromElement[DragTarget](elem, DragItem.targetPropName)
  }
  def readDragPayload(elem: dom.html.Element): js.UndefOr[DragPayload] = {
      readPropertyFromElement[DragPayload](elem, DragItem.payloadPropName)
    }
  def writeDragPayload(elem: dom.html.Element, dragPayload: => DragPayload): Unit = {
      writePropertyIntoElement(elem, DragItem.payloadPropName, dragPayload)
    }
  def readDragContainer(elem: dom.html.Element): js.UndefOr[DragContainer] = {
      readPropertyFromElement[DragContainer](elem, DragContainer.propName)
    }
  def readDraggableDraggedAction(elem: dom.html.Element): js.UndefOr[() => Unit] = {
      readPropertyFromElement[() => Unit](elem, DragItem.draggedActionPropName)
    }
  def dragWithHandle(item: DragPayloadAndTarget):VDomModifier = dragWithHandle(item,item)
  def dragWithHandle(
      payload: => DragPayload = DragItem.DisableDrag,
      target: DragTarget = DragItem.DisableDrag,
    ): VDomModifier = {
      @inline def disableDrag = payload.isInstanceOf[DragItem.DisableDrag.type]
      VDomModifier(
        //TODO: draggable bug: draggable sets display:none, then does not restore the old value https://github.com/Shopify/draggable/issues/318
        cls := "draggable", // makes this element discoverable for the Draggable library
        cls := "drag-feedback", // visual feedback for drag-start
        onMouseDown.stopPropagation.discard, // don't trigger global onMouseDown (e.g. closing right sidebar) when dragging
        VDomModifier.ifTrue(disableDrag)(cursor.auto), // overwrites cursor set by .draggable class
        prop(DragItem.payloadPropName) := (() => payload),
        prop(DragItem.targetPropName) := (() => target),
      )
    }
  def drag(item: DragPayloadAndTarget):VDomModifier = drag(item,item)
  def drag(
      payload: => DragPayload = DragItem.DisableDrag,
      target: DragTarget = DragItem.DisableDrag,
    ): VDomModifier = {
      VDomModifier(DragComponents.dragWithHandle(payload, target), cls := "draghandle")
    }
}
