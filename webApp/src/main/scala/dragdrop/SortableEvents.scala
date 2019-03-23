package wust.webApp.dragdrop

import draggable._
import org.scalajs.dom.ext.KeyCode
import wust.webApp.dragdrop.DragValidation._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.{BrowserDetect, DevOnly}

import scala.scalajs.js

// This file registers all drag-event listeners.
// These events are used to check if (onDragOver) drag-actions are valid.
// Valitiy is checked using the partial functions DragActions.sortAction and DragActions.dragAction.
// If e.g. a sort-action is not valid, the sortable:sort is canceled, so that elements don't move out of the way. (https://github.com/Shopify/draggable/tree/master/src/Sortable#events)
// If it is valid, perform the respective action in DragActions.

class SortableEvents(state: GlobalState, draggable: Draggable) {

  private var currentOverEvent: js.UndefOr[DragOverEvent] = js.undefined
  private var currentOverContainerEvent: js.UndefOr[DragOverContainerEvent] = js.undefined

  private var ctrlDown = false
  if (!BrowserDetect.isMobile) {
    keyDown(KeyCode.Ctrl).foreach(ctrlDown = _)
  }

  //TODO: keyup-event for Shift does not work in chrome. It reports Capslock.
  private var shiftDown = false
  //  if (!BrowserDetect.isMobile) {
  //    keyDown(KeyCode.Shift).foreach(shiftDown = _)
  //  }

  def onStartDrag() {
    state.eventProcessor.stopEventProcessing.onNext(true)
  }
  def onStopDrag() {
    state.eventProcessor.stopEventProcessing.onNext(false)
  }

  draggable.on[DragStartEvent]("drag:start", (e: DragStartEvent) => {
    onStartDrag()
    snabbdom.VNodeProxy.setDirty(e.sourceContainer)
    //    dragStartEvent.onNext(e)
    DevOnly {
      val payload = readDragPayload(e.originalSource)
      println(s"\ndrag start: $payload")
    }
  })

  draggable.on[DragOutEvent]("drag:out", { (e: DragOutEvent) =>
    snabbdom.VNodeProxy.setDirty(e.sourceContainer)
    currentOverEvent = js.undefined
  })

  draggable.on[DragOverContainerEvent]("drag:over:container", (e: DragOverContainerEvent) => {
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
    snabbdom.VNodeProxy.setDirty(e.overContainer)
    currentOverContainerEvent = js.undefined
  })

  draggable.on[SortableStartEvent]("sortable:start", { (sortableStartEvent:SortableStartEvent) =>
    onStartDrag()
    snabbdom.VNodeProxy.setDirty(sortableStartEvent.startContainer)
    // copy dragpayload reference from source to mirror // https://github.com/Shopify/draggable/issues/245
    val payload: js.UndefOr[DragPayload] = readDragPayload(sortableStartEvent.dragEvent.originalSource)
    payload.foreach(writeDragPayload(sortableStartEvent.dragEvent.source, _))

    if (payload == js.defined(DragItem.DisableDrag)) { println("Drag is disabled on this element."); sortableStartEvent.cancel() }
  })


  // when dragging over
  draggable.on[SortableSortEvent]("sortable:sort", (sortableSortEvent: SortableSortEvent) => {
    sortableSortEvent.overContainer.foreach(snabbdom.VNodeProxy.setDirty)

    (sortableSortEvent, currentOverContainerEvent) match {
      case (sortableSortEvent, JSDefined(currentOverContainerEvent)) =>
        val overSortcontainer = readDragContainer(sortableSortEvent.dragEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

        if(overSortcontainer) {
          println("over sortcontainer, validating sort information...")
          validateSortInformation(sortableSortEvent, currentOverContainerEvent, ctrlDown, shiftDown)
        } else {
          // drag action is handled by dragOverEvent instead
          sortableSortEvent.cancel()
        }
      case (sortableSortEvent, _)                              => sortableSortEvent.cancel()
    }
  })

  draggable.on[DragOverEvent]("drag:over", (dragOverEvent: DragOverEvent) => {
    snabbdom.VNodeProxy.setDirty(dragOverEvent.overContainer)
    DevOnly {
      readDragTarget(dragOverEvent.over).foreach { target => println(s"Dragging over: $target") }
    }
    currentOverEvent = js.defined(dragOverEvent)

    val notOverSortContainer = !readDragContainer(dragOverEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

    if (notOverSortContainer) {
      println("not over sort container, validating drag information...")
      validateDragInformation(dragOverEvent, ctrlDown, shiftDown)
    } else {
      // drag action is handled by sortableSortEvent instead
      dragOverEvent.cancel()
    }
  })


  // when dropping
  draggable.on[SortableStopEvent]("sortable:stop", (sortableStopEvent: SortableStopEvent) => {
    try {
      snabbdom.VNodeProxy.setDirty(sortableStopEvent.newContainer)
      scribe.debug(s"moved from position ${sortableStopEvent.oldIndex} to new position ${sortableStopEvent.newIndex}")
      (sortableStopEvent, currentOverContainerEvent, currentOverEvent) match {
        case (sortableStopEvent, JSDefined(currentOverContainerEvent), JSDefined(currentOverEvent)) =>
          val overSortcontainer = currentOverContainerEvent.overContainer.exists(overContainer => readDragContainer(overContainer).exists(_.isInstanceOf[SortableContainer]))

          if (overSortcontainer) {
            performSort(state, sortableStopEvent, currentOverContainerEvent, currentOverEvent, ctrlDown, shiftDown)
          } else {
            performDrag(state, sortableStopEvent, currentOverEvent, ctrlDown, shiftDown)
          }
        case _ =>
          println("dropped outside container or target")
      }
    } finally {
      onStopDrag()
    }
  })
}
