package wust.webApp.dragdrop

import draggable._
import monix.reactive.Observable
import googleAnalytics.Analytics
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.ext.KeyCode
import wust.util._
import org.scalajs.dom.raw.HTMLElement
import wust.api.AuthUser
import wust.graph.{Edge, GraphChanges, Tree, _}
import wust.ids.{EdgeData, NodeId, NodeRole, UserId}
import wust.webApp.{BrowserDetect, DevOnly}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.|
import DragActions._
import DragValidation._

// This file registers all drag-event listeners and puts the events into PublishSubjects.
// These subjects are used to check if (onDragOver) drag-actions are valid.
// Valitiy is checked using the partial functions DragActions.sortAction and DragActions.dragAction.
// If e.g. a sort-action is not valid, the sortable:sort is canceled, so that elements don't move out of the way. (https://github.com/Shopify/draggable/tree/master/src/Sortable#events)
// If it is valid, perform the respective action in DragActions.

class SortableEvents(state: GlobalState, draggable: Draggable) {

  import TaskOrdering.Position

//  private val dragStartEvent = PublishSubject[DragStartEvent]
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOverContainerEvent = PublishSubject[DragOverContainerEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val dragOutContainerEvent = PublishSubject[DragOutContainerEvent]
  private val sortableStartEvent = PublishSubject[SortableStartEvent]
  private val sortableStopEvent = PublishSubject[SortableStopEvent]
  private val sortableSortEvent = PublishSubject[SortableSortEvent]

  private val currentOverEvent = PublishSubject[js.UndefOr[DragOverEvent]] //TODO: observable derived from other subjects
  private val currentOverContainerEvent = PublishSubject[js.UndefOr[DragOverContainerEvent]] //TODO: observable derived from other subjects

  draggable.on[SortableStartEvent]("sortable:start", sortableStartEvent.onNext _)
  draggable.on[SortableSortEvent]("sortable:sort", (e: SortableSortEvent) => {
    sortableSortEvent.onNext(e)
   // DevOnly(console.log(e))
  })
  draggable.on[SortableStopEvent]("sortable:stop", (e: SortableStopEvent) => {
    sortableStopEvent.onNext(e)
    scribe.debug(s"moved from position ${ e.oldIndex } to new position ${ e.newIndex }")
  })

  draggable.on[DragStartEvent]("drag:start", (e:DragStartEvent) => {
//    dragStartEvent.onNext(e)
    DevOnly {
      val payload = readDragPayload(e.originalSource)
      scribe.info(s"\ndrag start: $payload")
    }
  })
  draggable.on[DragOverEvent]("drag:over", (e:DragOverEvent) => {
    dragOverEvent.onNext(e)
    // DevOnly(console.log(e))
  })
  draggable.on[DragOverContainerEvent]("drag:over:container", (e:DragOverContainerEvent) => {
    dragOverContainerEvent.onNext(e)
    // DevOnly(console.log(e))
  })
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext _)


  dragOverEvent.map { e =>
    DevOnly {
      readDragTarget(e.over).foreach { target => scribe.info(s"Dragging over: $target") }
    }
    js.defined(e)
  }.subscribe(currentOverEvent)

  dragOutEvent.map(_ => js.undefined).subscribe(currentOverEvent)


  dragOverContainerEvent.map { e =>
    DevOnly {
      readDragContainer(e.overContainer).foreach { container => scribe.info(s"Dragging over container: $container") }
    }
    js.defined(e)
  }.subscribe(currentOverContainerEvent)

  dragOutContainerEvent.map(_ => js.undefined).subscribe(currentOverContainerEvent)


  sortableStartEvent.foreachTry { e =>
    // copy dragpayload reference from source to mirror // https://github.com/Shopify/draggable/issues/245
    val payload: js.UndefOr[DragPayload] = readDragPayload(e.dragEvent.originalSource)
    payload.foreach(writeDragPayload(e.dragEvent.source, _))

    if(payload == js.defined(DragItem.DisableDrag)) { scribe.info("Drag is disabled on this element."); e.cancel() }
  }

  //  val ctrlDown = keyDown(KeyCode.Ctrl)
  val ctrlDown = if(BrowserDetect.isMobile) Observable.now(false) else keyDown(KeyCode.Ctrl)
  //  ctrlDown.foreach(down => println(s"ctrl down: $down"))

  //TODO: keyup-event for Shift does not work in chrome. It reports Capslock.
  val shiftDown = Observable.now(false)
  //  val shiftDown = keyDown(KeyCode.Shift)
  //  shiftDown.foreach(down => println(s"shift down: $down"))


  // when dragging over
  sortableSortEvent.withLatestFrom3(currentOverContainerEvent, ctrlDown, shiftDown)((sortableSortEvent, currentOverContainerEvent, ctrl, shift) => (sortableSortEvent, currentOverContainerEvent, ctrl, shift)).foreachTry {
    case (sortableSortEvent, currentOverContainerEvent, ctrl, shift) if currentOverContainerEvent.isDefined =>
      val overSortcontainer = readDragContainer(sortableSortEvent.dragEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

      if(overSortcontainer) {
        scribe.info("over sortcontainer, validating sort information...")
        validateSortInformation(sortableSortEvent, currentOverContainerEvent.get, ctrl, shift)
      } else {
        // drag action is handled by dragOverEvent instead
        sortableSortEvent.cancel()
      }
    case (sortableSortEvent, _, _, _) => sortableSortEvent.cancel()
  }

  dragOverEvent.withLatestFrom2(ctrlDown, shiftDown)((e, ctrl, shift) => (e, ctrl, shift)).foreachTry {
    case (e, ctrl, shift) =>
      val notOverSortContainer = !readDragContainer(e.overContainer).exists(_.isInstanceOf[SortableContainer])

      if(notOverSortContainer) {
        scribe.info("not over sort container, validating drag information...")
        validateDragInformation(e, ctrl, shift)
      } else {
        // drag action is handled by sortableSortEvent instead
        e.cancel()
      }
  }

  // when dropping
  sortableStopEvent.withLatestFrom4(currentOverContainerEvent, currentOverEvent, ctrlDown, shiftDown)((sortableStopEvent, currentOverContainerEvent, currentOverEvent, ctrl, shift) => (sortableStopEvent, currentOverContainerEvent, currentOverEvent, ctrl, shift)).foreachTry {
    case (sortableStopEvent, currentOverContainerEvent, currentOverEvent, ctrl, shift) if currentOverContainerEvent.isDefined =>
      val overSortcontainer = currentOverContainerEvent.flatMap(e => readDragContainer(e.overContainer)).exists(_.isInstanceOf[SortableContainer])

      if(overSortcontainer) {
        performSort(state, sortableStopEvent, currentOverContainerEvent.get, currentOverEvent.get, ctrl, shift)
      } else {
        performDrag(state, sortableStopEvent, currentOverEvent.get, ctrl, shift)
      }

      snabbdom.VNodeProxy.setDirty(sortableStopEvent.oldContainer)
      snabbdom.VNodeProxy.setDirty(sortableStopEvent.newContainer)
    case _ =>
      scribe.info("dropped outside container or target")
  }
}
