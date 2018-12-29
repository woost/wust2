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
    val overContainer = overContainerWorkaround.getOrElse(lastDragOverContainerEvent.overContainer)
    val sourceContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].sourceContainer.asInstanceOf[dom.html.Element] // TODO: report as feature request
    val payloadOpt = readDragPayload(e.dragEvent.source)
    // containers are written by registerDragContainer
    val targetContainerOpt = readDragContainer(overContainer)
    val sourceContainerOpt = readDragContainer(sourceContainerWorkaround)
    (sourceContainerOpt, payloadOpt, targetContainerOpt)
  }

  def validateSortInformation(e: SortableSortEvent, lastDragOverContainerEvent: DragOverContainerEvent, ctrl: Boolean, shift: Boolean): Unit = {
    extractSortInformation(e, lastDragOverContainerEvent) match {
      case (sourceContainer, payload, overContainer) if sourceContainer.isDefined && payload.isDefined && overContainer.isDefined =>
        if(sortAction.isDefinedAt((sourceContainer.get, payload.get, overContainer.get, ctrl, shift))) {
          scribe.info(s"valid sort action: $payload: $sourceContainer -> $overContainer")
        } else {
          e.cancel()
          scribe.info(s"sort not allowed: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          validateDragInformation(e.dragEvent, ctrl, shift)
        }
      case (sourceContainer, payload, overContainer)                                                                              =>
        e.cancel()
        scribe.info(s"incomplete sort information: $payload: $sourceContainer -> $overContainer")
    }
  }

  def validateDragInformation(e: DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    val targetOpt = readDragTarget(e.over)
    val payloadOpt = readDragPayload(e.originalSource)
    (payloadOpt, targetOpt) match {
      case (payload, target) if payload.isDefined && target.isDefined =>
        if(dragAction.isDefinedAt((payload.get, target.get, ctrl, shift))) {
          scribe.info(s"valid drag action: $payload -> $target)")
        } else {
          e.cancel()
          scribe.info(s"drag not allowed: $payload -> $target)")
        }
      case (payload, target)                                          =>
        e.cancel()
        scribe.info(s"incomplete drag information: $payload -> $target)")
    }
  }

  def performSort(state:GlobalState, e: SortableStopEvent, currentOverContainerEvent:DragOverContainerEvent, currentOverEvent:DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    extractSortInformation(e, currentOverContainerEvent) match {
      case (sourceContainer, payload, overContainer) if sourceContainer.isDefined && payload.isDefined && overContainer.isDefined =>
        // target is null, since sort actions do not look at the target. The target moves away automatically.
        val successful = sortAction.runWith { calculateChange =>
          state.eventProcessor.changes.onNext(calculateChange(e, state.graph.now, state.user.now.id))
        }((sourceContainer.get, payload.get, overContainer.get, ctrl, shift))

        if(successful)
          scribe.info(s"sort action successful: $payload: $sourceContainer -> $overContainer")
        else {
          scribe.info(s"sort action not defined: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          performDrag(state, e,currentOverEvent,ctrl, shift)
        }
      case (sourceContainerOpt, payloadOpt, overContainerOpt)                                                                     =>
        scribe.info(s"incomplete sort information: $payloadOpt: $sourceContainerOpt -> $overContainerOpt")
    }
  }

  def performDrag(state:GlobalState, e: SortableStopEvent, currentOverEvent:DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    scribe.info("performing drag...")
         val afterDraggedActionOpt = readDraggableDraggedAction(e.dragEvent.originalSource)
         val payloadOpt = readDragPayload(e.dragEvent.originalSource)
         val targetOpt = readDragTarget(currentOverEvent.over)
         (payloadOpt, targetOpt) match {
           case (payload, target) if payload.isDefined && target.isDefined =>
             val successful = dragAction.runWith { calculateChange =>
               state.eventProcessor.changes.onNext(calculateChange(e, state.graph.now, state.user.now.id))
             }((payload.get, target.get, ctrl, shift))

             if(successful) {
               scribe.info(s"drag action successful: $payload -> $target")
               afterDraggedActionOpt.foreach{action =>
                 scribe.info(s"performing afterDraggedAction...")
                 action.apply()
               }
             }
             else {
               scribe.info(s"drag action not defined: $payload -> $target")
               Analytics.sendEvent("drag", "nothandled", s"${ payload.get.productPrefix }-${ target.get.productPrefix } ${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
             }
           case (payload, target) =>
             scribe.info(s"incomplete drag information: $payload -> $target)")
         }
  }
}
