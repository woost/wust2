package wust.webApp.dragdrop

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import wust.facades.draggable._
import wust.webApp.DebugOnly
import wust.webApp.dragdrop.DragValidation._
import wust.webApp.state.GlobalState
import wust.webApp.views.DragComponents.{ readDragContainer, readDragPayload, readDragTarget, writeDragPayload }
import wust.webUtil.JSDefined
import collection.mutable

import outwatch.repairdom.RepairDom

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

  def init(): Unit = {
    new SortableEvents(sortable)
  }
}

class SortableEvents(draggable: Draggable) {

  private var currentOverEvent: js.UndefOr[DragOverEvent] = js.undefined
  private var currentOverContainerEvent: js.UndefOr[DragOverContainerEvent] = js.undefined

  def onStartDrag(): Unit = {
    GlobalState.eventProcessor.stopEventProcessing.onNext(true)
  }
  def onStopDrag(): Unit = {
    // actively repair the containers, since drags can be aborted / emit empty graphchanges
    // happens when creating containment cycles or drag with ctrl (copy)
    domCleanup()
    GlobalState.eventProcessor.stopEventProcessing.onNext(false)
  }

  val dirtyContainers = mutable.HashSet.empty[HTMLElement]
  def setDirty(element: HTMLElement): Unit = {
    RepairDom.setDirty(element)
    dirtyContainers += element
  }
  def domCleanup(): Unit = {
    dirtyContainers.foreach{ element =>
      readDragContainer(element).foreach(_.triggerRepair.onNext(()))
    }
    dirtyContainers.clear()
  }

  draggable.on[DragStartEvent]("drag:start", (e: DragStartEvent) => {
    onStartDrag()
    setDirty(e.sourceContainer)
    //    dragStartEvent.onNext(e)
    DebugOnly {
      val payload = readDragPayload(e.originalSource)
      scribe.debug(s"\ndrag start: $payload")
    }
  })

  draggable.on[DragOutEvent]("drag:out", { (e: DragOutEvent) =>
    setDirty(e.sourceContainer)
    currentOverEvent = js.undefined
  })

  draggable.on[DragOverContainerEvent]("drag:over:container", (e: DragOverContainerEvent) => {
    e.overContainer.foreach(setDirty)
    DebugOnly {
      for {
        overContainer <- e.overContainer
        container <- readDragContainer(overContainer)
      } { scribe.debug(s"Dragging over container: $container") }
    }
    currentOverContainerEvent = js.defined(e)
  })

  draggable.on[DragOutContainerEvent]("drag:out:container", (e: DragOutContainerEvent) => {
    setDirty(e.overContainer)
    currentOverContainerEvent = js.undefined
  })

  draggable.on[SortableStartEvent]("sortable:start", { (sortableStartEvent: SortableStartEvent) =>
    onStartDrag()
    setDirty(sortableStartEvent.startContainer)
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
    sortableSortEvent.overContainer.foreach(setDirty)

    (sortableSortEvent, currentOverContainerEvent) match {
      case (sortableSortEvent, JSDefined(currentOverContainerEvent)) =>
        val overSortcontainer = readDragContainer(sortableSortEvent.dragEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

        if (overSortcontainer) {
          val currentEvent = dom.window.asInstanceOf[js.Dynamic].event.asInstanceOf[js.UndefOr[js.Dynamic]].map(_.detail.originalEvent)
          val ctrlDown = currentEvent.exists(_.ctrlKey.asInstanceOf[Boolean])
          val shiftDown = currentEvent.exists(_.shiftKey.asInstanceOf[Boolean])
          validateSortInformation(sortableSortEvent, currentOverContainerEvent, ctrlDown, shiftDown)
        } else {
          // drag action is handled by dragOverEvent instead
          sortableSortEvent.cancel()
        }
      case (sortableSortEvent, _) => sortableSortEvent.cancel()
    }
  })

  draggable.on[DragOverEvent]("drag:over", (dragOverEvent: DragOverEvent) => {
    setDirty(dragOverEvent.overContainer)
    DebugOnly {
      readDragTarget(dragOverEvent.over).foreach { target => scribe.debug(s"Dragging over: $target") }
    }
    currentOverEvent = js.defined(dragOverEvent)

    val notOverSortContainer = !readDragContainer(dragOverEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

    if (notOverSortContainer) {
      val currentEvent = dom.window.asInstanceOf[js.Dynamic].event.asInstanceOf[js.UndefOr[js.Dynamic]].map(_.detail.originalEvent)
      val ctrlDown = currentEvent.exists(_.ctrlKey.asInstanceOf[Boolean])
      val shiftDown = currentEvent.exists(_.shiftKey.asInstanceOf[Boolean])
      validateDragInformation(dragOverEvent, ctrlDown, shiftDown)
    } else {
      // drag action is handled by sortableSortEvent instead
      dragOverEvent.cancel()
    }
  })

  // when dropping
  draggable.on[SortableStopEvent]("sortable:stop", (sortableStopEvent: SortableStopEvent) => {
    try {
      setDirty(sortableStopEvent.newContainer)
      scribe.debug(s"moved from position ${sortableStopEvent.oldIndex} to new position ${sortableStopEvent.newIndex}")
      (sortableStopEvent, currentOverContainerEvent, currentOverEvent) match {
        case (sortableStopEvent, JSDefined(currentOverContainerEvent), JSDefined(currentOverEvent)) =>
          val overSortcontainer = currentOverContainerEvent.overContainer.exists(overContainer => readDragContainer(overContainer).exists(_.isInstanceOf[SortableContainer]))

          val currentEvent = dom.window.asInstanceOf[js.Dynamic].event.asInstanceOf[js.UndefOr[js.Dynamic]].map(_.detail.originalEvent)
          val ctrlDown = currentEvent.exists(_.ctrlKey.asInstanceOf[Boolean])
          val shiftDown = currentEvent.exists(_.shiftKey.asInstanceOf[Boolean])

          if (overSortcontainer) {
            performSort(sortableStopEvent, currentOverContainerEvent, currentOverEvent, ctrlDown, shiftDown)
          } else {
            performDrag(sortableStopEvent, currentOverEvent, ctrlDown, shiftDown)
          }

        case _ =>
          scribe.debug("dropped outside container or target")
      }
    } finally {
      onStopDrag()
    }
  })
}
