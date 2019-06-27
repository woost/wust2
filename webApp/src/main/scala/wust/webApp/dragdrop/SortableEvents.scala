package wust.webApp.dragdrop

import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.HTMLElement
import wust.facades.draggable._
import wust.webApp.DevOnly
import wust.webApp.dragdrop.DragValidation._
import wust.webApp.state.GlobalState
import wust.webApp.views.DragComponents.{readDragContainer, readDragPayload, readDragTarget, writeDragPayload}
import wust.webUtil.{BrowserDetect, JSDefined}
import wust.webUtil.outwatchHelpers._

import scala.scalajs.js

// This file registers all drag-event listeners.
// These events are used to check if (onDragOver) drag-actions are valid.
// Valitiy is checked using the partial functions DragActions.sortAction and DragActions.dragAction.
// If e.g. a sort-action is not valid, the sortable:sort is canceled, so that elements don't move out of the way. (https://github.com/Shopify/draggable/tree/master/src/Sortable#events)
// If it is valid, perform the respective action in DragActions.

object SortableEvents {
  val sortable = new Sortable(js.Array[HTMLElement](), new Options {
    draggable = ".draggable"
    handle = ".draghandle"
    delay = 200.0 // prevents drag when touch scrolling is intended
    mirror = new MirrorOptions {
      constrainDimensions = true
      appendTo = "#draggable-mirrors"
    }
  })

  def init(state: GlobalState):Unit = {
    new SortableEvents(state, sortable)
  }
}

class SortableEvents(state: GlobalState, draggable: Draggable) {

  private var currentOverEvent: js.UndefOr[DragOverEvent] = js.undefined
  private var currentOverContainerEvent: js.UndefOr[DragOverContainerEvent] = js.undefined

  private var ctrlDown = false
  private var shiftDown = false
  private def setKeyDownFromEvent(event: DragEvent): Unit = {
    if (!BrowserDetect.isMobile && event.originalEvent != null) {
      ctrlDown = event.originalEvent.ctrlKey
      shiftDown = event.originalEvent.shiftKey
    }
  }

  //TODO: keyup-event for Shift does not work in chrome. It reports Capslock.
  //  if (!BrowserDetect.isMobile) {
  //    keyDown(KeyCode.Shift).foreach(shiftDown = _)
  //  }

  def onStartDrag(): Unit = {
    state.eventProcessor.stopEventProcessing.onNext(true)
  }
  def onStopDrag(): Unit = {
    state.eventProcessor.stopEventProcessing.onNext(false)
  }

  draggable.on[DragStartEvent]("drag:start", (e: DragStartEvent) => {
    setKeyDownFromEvent(e)
    onStartDrag()
    snabbdom.VNodeProxy.setDirty(e.sourceContainer)
    //    dragStartEvent.onNext(e)
    DevOnly {
      val payload = readDragPayload(e.originalSource)
      println(s"\ndrag start: $payload")
    }
  })

  draggable.on[DragOutEvent]("drag:out", { (e: DragOutEvent) =>
    setKeyDownFromEvent(e)
    snabbdom.VNodeProxy.setDirty(e.sourceContainer)
    currentOverEvent = js.undefined
  })

  draggable.on[DragOverContainerEvent]("drag:over:container", (e: DragOverContainerEvent) => {
    setKeyDownFromEvent(e)
    e.overContainer.foreach(snabbdom.VNodeProxy.setDirty)
    DevOnly {
      for {
        overContainer <- e.overContainer
        container <- readDragContainer(overContainer)
      } { println(s"Dragging over container: $container") }
    }
    currentOverContainerEvent = js.defined(e)
  })

  draggable.on[DragOutContainerEvent]("drag:out:container", (e: DragOutContainerEvent) => {
    setKeyDownFromEvent(e)
    snabbdom.VNodeProxy.setDirty(e.overContainer)
    currentOverContainerEvent = js.undefined
  })

  draggable.on[SortableStartEvent]("sortable:start", { (sortableStartEvent: SortableStartEvent) =>
    setKeyDownFromEvent(sortableStartEvent.dragEvent)
    onStartDrag()
    snabbdom.VNodeProxy.setDirty(sortableStartEvent.startContainer)
    // copy dragpayload reference from source to mirror // https://github.com/Shopify/draggable/issues/245
    val payload: js.UndefOr[DragPayload] = readDragPayload(sortableStartEvent.dragEvent.originalSource)
    payload.foreach(writeDragPayload(sortableStartEvent.dragEvent.source, _))

    if (payload == js.defined(DragItem.DisableDrag)) {
      scribe.debug("Drag is disabled on this element.")
      sortableStartEvent.cancel()
    }
  })

  // when dragging over
  draggable.on[SortableSortEvent]("sortable:sort", (sortableSortEvent: SortableSortEvent) => {
    setKeyDownFromEvent(sortableSortEvent.dragEvent)
    sortableSortEvent.overContainer.foreach(snabbdom.VNodeProxy.setDirty)

    (sortableSortEvent, currentOverContainerEvent) match {
      case (sortableSortEvent, JSDefined(currentOverContainerEvent)) =>
        val overSortcontainer = readDragContainer(sortableSortEvent.dragEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

        if (overSortcontainer) {
          validateSortInformation(sortableSortEvent, currentOverContainerEvent, ctrlDown, shiftDown)
        } else {
          // drag action is handled by dragOverEvent instead
          sortableSortEvent.cancel()
        }
      case (sortableSortEvent, _) => sortableSortEvent.cancel()
    }
  })

  draggable.on[DragOverEvent]("drag:over", (dragOverEvent: DragOverEvent) => {
    setKeyDownFromEvent(dragOverEvent)
    snabbdom.VNodeProxy.setDirty(dragOverEvent.overContainer)
    DevOnly {
      readDragTarget(dragOverEvent.over).foreach { target => println(s"Dragging over: $target") }
    }
    currentOverEvent = js.defined(dragOverEvent)

    val notOverSortContainer = !readDragContainer(dragOverEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

    if (notOverSortContainer) {
      validateDragInformation(dragOverEvent, ctrlDown, shiftDown)
    } else {
      // drag action is handled by sortableSortEvent instead
      dragOverEvent.cancel()
    }
  })

  // when dropping
  draggable.on[SortableStopEvent]("sortable:stop", (sortableStopEvent: SortableStopEvent) => {
    setKeyDownFromEvent(sortableStopEvent.dragEvent)
    try {
      snabbdom.VNodeProxy.setDirty(sortableStopEvent.newContainer)
      scribe.debug(s"moved from position ${sortableStopEvent.oldIndex} to new position ${sortableStopEvent.newIndex}")
      (sortableStopEvent, currentOverContainerEvent, currentOverEvent) match {
        case (sortableStopEvent, JSDefined(currentOverContainerEvent), JSDefined(currentOverEvent)) =>
          val overSortcontainer = currentOverContainerEvent.overContainer.exists(overContainer => readDragContainer(overContainer).exists(_.isInstanceOf[SortableContainer]))

          if (overSortcontainer) {
            performSort(state, sortableStopEvent, currentOverContainerEvent, currentOverEvent, ctrlDown, shiftDown)
            // actively repair the containers, since drags can be aborted / emit empty graphchanges
            // happens when creating containment cycles or drag with ctrl (copy)
            readDragContainer(sortableStopEvent.oldContainer).foreach(_.triggerRepair.onNext(()))
            readDragContainer(sortableStopEvent.newContainer).foreach(_.triggerRepair.onNext(()))
          } else {
            performDrag(state, sortableStopEvent, currentOverEvent, ctrlDown, shiftDown)
          }
        case _ =>
          scribe.debug("dropped outside container or target")
      }
    } finally {
      onStopDrag()
    }
  })
}
