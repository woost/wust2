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


object DragValidation {

  def extractSortInformation(e:SortableEvent, lastDragOverContainerEvent: DragOverContainerEvent):(js.UndefOr[DragContainer], js.UndefOr[DragPayload], js.UndefOr[DragContainer]) = {
    val overContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].overContainer.asInstanceOf[js.UndefOr[dom.html.Element]] // https://github.com/Shopify/draggable/issues/256
    val overContainer = overContainerWorkaround.orElse(lastDragOverContainerEvent.overContainer)
    val sourceContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].sourceContainer.asInstanceOf[dom.html.Element] // TODO: report as feature request
    val payloadOpt = readDragPayload(e.dragEvent.source)
    // containers are written by registerDragContainer
    val targetContainerOpt = overContainer.flatMap(readDragContainer)
    val sourceContainerOpt = readDragContainer(sourceContainerWorkaround)
    (sourceContainerOpt, payloadOpt, targetContainerOpt)
  }

  def validateSortInformation(e: SortableSortEvent, lastDragOverContainerEvent: DragOverContainerEvent, ctrl: Boolean, shift: Boolean): Unit = {
    extractSortInformation(e, lastDragOverContainerEvent) match {
      case (JSDefined(sourceContainer), JSDefined(payload), JSDefined(overContainer)) =>
        if(sortAction.isDefinedAt((payload, sourceContainer, overContainer, ctrl, shift))) {
          println(s"valid sort action: $payload: $sourceContainer -> $overContainer")
        } else {
          e.cancel()
          println(s"sort not allowed: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          validateDragInformation(e.dragEvent, ctrl, shift)
        }
      case (sourceContainer, payload, overContainer)                                                                              =>
        e.cancel()
        println(s"incomplete sort information: $payload: $sourceContainer -> $overContainer")
    }
  }

  def validateDragInformation(e: DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    val targetOpt = readDragTarget(e.over)
    val payloadOpt = readDragPayload(e.originalSource)
    (payloadOpt, targetOpt) match {
      case (JSDefined(payload), JSDefined(target)) =>
        if(dragAction.isDefinedAt((payload, target, ctrl, shift))) {
          println(s"valid drag action: $payload -> $target)")
        } else {
          e.cancel()
          println(s"drag not allowed: $payload -> $target)")
        }
      case (payload, target)                                          =>
        e.cancel()
        println(s"incomplete drag information: $payload -> $target)")
    }
  }

  def performSort(state:GlobalState, e: SortableStopEvent, currentOverContainerEvent:DragOverContainerEvent, currentOverEvent:DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    extractSortInformation(e, currentOverContainerEvent) match {
      case (JSDefined(sourceContainer), JSDefined(payload), JSDefined(overContainer)) =>
        // target is null, since sort actions do not look at the target. The target moves away automatically.
        val successful = sortAction.runWith { calculateChange =>
          state.eventProcessor.changes.onNext(calculateChange(e, state.graph.now, state.user.now.id))
        }((payload, sourceContainer, overContainer, ctrl, shift))

        if(successful) {
          println(s"sort action successful: $payload: $sourceContainer -> $overContainer")
          Analytics.sendEvent("drag", "sort", s"${sourceContainer.productPrefix}-${payload.productPrefix}-${overContainer.productPrefix}${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
        }
        else {
          println(s"sort action not defined: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          performDrag(state, e,currentOverEvent,ctrl, shift)
        }
      case (sourceContainerOpt, payloadOpt, overContainerOpt)                                                                     =>
        println(s"incomplete sort information: $payloadOpt: $sourceContainerOpt -> $overContainerOpt")
    }
  }

  def performDrag(state:GlobalState, e: SortableStopEvent, currentOverEvent:DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    println("performing drag...")
    val afterDraggedActionOpt = readDraggableDraggedAction(e.dragEvent.originalSource)
    val payloadOpt = readDragPayload(e.dragEvent.originalSource)
    val targetOpt = readDragTarget(currentOverEvent.over)
    (payloadOpt, targetOpt) match {
     case (JSDefined(payload), JSDefined(target)) =>
       val successful = dragAction.runWith { calculateChange =>
         state.eventProcessor.changes.onNext(calculateChange(state.graph.now, state.user.now.id))
       }((payload, target, ctrl, shift))

       if(successful) {
         println(s"drag action successful: $payload -> $target")
         Analytics.sendEvent("drag", "drop", s"${ payload.productPrefix }-${ target.productPrefix }${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
         afterDraggedActionOpt.foreach{action =>
           println(s"performing afterDraggedAction...")
           action.apply()
         }
       }
       else {
         println(s"drag action not defined: $payload -> $target ${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }, defined($payload, $target, $ctrl, $shift): ${dragAction.isDefinedAt((payload, target, ctrl, shift))}")
         Analytics.sendEvent("drag", "nothandled", s"${ payload.productPrefix }-${ target.productPrefix } ${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
       }
     case (payload, target) =>
       println(s"incomplete drag information: $payload -> $target)")
    }
  }
}
