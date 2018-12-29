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


object Sorting {

  import TaskOrdering.Position

  def parseDomPositions(e: SortableStopEvent): Option[(Position, Position)] = {

    // Get index of moved node in previous container
    val origElem = e.dragEvent.originalSource

    // Get index of moved node in new container
    val sourceElem = e.dragEvent.source

    // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
    val previousContChildren: js.Array[HTMLElement] = e.oldContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.source || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror"))
    val newContChildren: js.Array[HTMLElement] = e.newContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.originalSource || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror"))

    val prevPos = previousContChildren.indexOf(origElem)
    val nextPos = newContChildren.indexOf(sourceElem)

    if(prevPos != -1 && nextPos != -1) Some((prevPos, nextPos))
    else None
  }

  @inline def checkContainerChanged(from: DragContainer, into: DragContainer) = from != into
  @inline def checkPositionChanged(previousPosition: Position, newPosition: Position) = previousPosition != newPosition

  // Hints:
  // - Most outer container contains unclassified nodes as well
  // - Only one "big" sortable => container always the same (oldContainer == newContainer)
  // - A container corresponds to a parent node
  // - The index in a container correspond to the index in the topological sorted node list of the corresponding parent node
  def sortingChanges(graph: Graph, userId: UserId, e: SortableStopEvent, sortNode: NodeId, from: SortableContainer, into: SortableContainer): GraphChanges = {

    import DragContainer._
    scribe.debug("Computing sorting change")
    //TODO: Is a SortEvent triggered when a new card is created?
    parseDomPositions(e) match {
      case Some((previousDomPosition, newDomPosition)) =>
        val containerChanged = checkContainerChanged(from, into)

        val gc = if(!containerChanged && !checkPositionChanged(previousDomPosition, newDomPosition)) { scribe.debug("item dropped on same place (no movement)"); GraphChanges.empty }
                 else
                   TaskOrdering.constructGraphChangesByContainer(graph, userId, sortNode, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId, from.items, into.items)


        //                 else if(from.isInstanceOf[Kanban.Inbox] && into.isInstanceOf[Kanban.Inbox])
        //                   TaskOrdering.constructGraphChangesByContainer(graph, userId, sortNode, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId, from.asInstanceOf[Kanban.Inbox].items, into.asInstanceOf[Kanban.Inbox].items)
        //                 else
        //                   TaskOrdering.constructGraphChangesByOrdering(graph, userId, sortNode, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId)

        scribe.debug("Calculated new sorting graph change!")
        scribe.debug(gc.toPrettyString(graph))
        gc
      case _                                           =>
        TaskOrdering.abortSorting("Could not determine position of elements")
    }
  }
}
