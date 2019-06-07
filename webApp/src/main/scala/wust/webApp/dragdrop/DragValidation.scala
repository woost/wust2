package wust.webApp.dragdrop

import wust.facades.draggable._
import wust.facades.googleanalytics.Analytics
import org.scalajs.dom
import wust.util._
import wust.webApp.dragdrop.DragActions._
import wust.webApp.state.GlobalState
import wust.webApp.views.DragComponents.{readDragContainer, readDragPayload, readDragTarget, readDraggableDraggedAction}
import wust.webUtil.JSDefined

import scala.scalajs.js


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
          scribe.debug(s"valid sort action: $payload: $sourceContainer -> $overContainer")
        } else {
          e.cancel()
          scribe.debug(s"sort not allowed: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          validateDragInformation(e.dragEvent, ctrl, shift)
        }
      case (sourceContainer, payload, overContainer)                                                                              =>
        e.cancel()
        scribe.debug(s"incomplete sort information: $payload: $sourceContainer -> $overContainer")
    }
  }

  def validateDragInformation(e: DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    val targetOpt = readDragTarget(e.over)
    val payloadOpt = readDragPayload(e.originalSource)
    (payloadOpt, targetOpt) match {
      case (JSDefined(payload), JSDefined(target)) =>
        if(dragAction.isDefinedAt((payload, target, ctrl, shift))) {
          scribe.debug(s"valid drag action: $payload -> $target)")
        } else {
          e.cancel()
          scribe.debug(s"drag not allowed: $payload -> $target)")
        }
      case (payload, target)                                          =>
        e.cancel()
        scribe.debug(s"incomplete drag information: $payload -> $target)")
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
          scribe.debug(s"sort action successful: $payload: $sourceContainer -> $overContainer")
          Analytics.sendEvent("drag", "sort", s"${sourceContainer.productPrefix}-${payload.productPrefix}-${overContainer.productPrefix}${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
        }
        else {
          scribe.debug(s"sort action not defined: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          performDrag(state, e,currentOverEvent,ctrl, shift)
        }
      case (sourceContainerOpt, payloadOpt, overContainerOpt)                                                                     =>
        scribe.debug(s"incomplete sort information: $payloadOpt: $sourceContainerOpt -> $overContainerOpt")
    }
  }

  def performDrag(state:GlobalState, e: SortableStopEvent, currentOverEvent:DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    scribe.debug("performing drag...")
    val afterDraggedActionOpt = readDraggableDraggedAction(e.dragEvent.originalSource)
    val payloadOpt = readDragPayload(e.dragEvent.originalSource)
    val targetOpt = readDragTarget(currentOverEvent.over)
    (payloadOpt, targetOpt) match {
     case (JSDefined(payload), JSDefined(target)) =>
       val successful = dragAction.runWith { calculateChange =>
         state.eventProcessor.changes.onNext(calculateChange(state.rawGraph.now, state.user.now.id))
       }((payload, target, ctrl, shift))

       if(successful) {
         scribe.debug(s"drag action successful: $payload -> $target")
         Analytics.sendEvent("drag", "drop", s"${ payload.productPrefix }-${ target.productPrefix }${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
         afterDraggedActionOpt.foreach{action =>
           scribe.debug(s"performing afterDraggedAction...")
           action.apply()
         }
       }
       else {
         scribe.debug(s"drag action not defined: $payload -> $target ${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }, defined($payload, $target, $ctrl, $shift): ${dragAction.isDefinedAt((payload, target, ctrl, shift))}")
         Analytics.sendEvent("drag", "nothandled", s"${ payload.productPrefix }-${ target.productPrefix } ${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
       }
     case (payload, target) =>
       scribe.debug(s"incomplete drag information: $payload -> $target)")
    }
  }
}
