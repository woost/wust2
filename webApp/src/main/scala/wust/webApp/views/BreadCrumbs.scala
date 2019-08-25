package wust.webApp.views

import wust.facades.googleanalytics.Analytics
import fontAwesome.freeSolid
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._
import wust.api.AuthUser
import wust.graph.{Graph, Page}
import wust.ids._
import wust.util._
import wust.webApp.WoostConfig
import wust.webApp.dragdrop._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer

import scala.collection.breakOut
import wust.webApp.state.FeatureState

// Breadcrumbs are a path of nodes rendered up to an endpoint.
// The path starts either at the highest reachable root nodes or at a specified endpoint.

object BreadCrumbs {
  sealed trait EndPoint
  object EndPoint {
    case class Node(nodeId: NodeId , inclusive: Boolean = true) extends EndPoint
    case object None extends EndPoint
  }

  def apply(
    graph: Graph,
    start: EndPoint,
    end: EndPoint.Node,
    clickAction: NodeId => Unit,
    hideIfSingle:Boolean = false,
  )(implicit ctx: Ctx.Owner): VNode = {
    div(
      modifier(graph, start, end = end, clickAction = clickAction, hideIfSingle = hideIfSingle)
    )
  }

  def modifier(
    graph: Graph,
    start: EndPoint,
    end: EndPoint.Node,
    clickAction: NodeId => Unit,
    hideIfSingle:Boolean = false,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    VDomModifier(
      cls := "breadcrumbs",
      {
        val endDepths: Map[Int, Map[Int, Seq[NodeId]]] = graph.parentDepths(end.nodeId)
        val distanceToNodes: Seq[(Int, Map[Int, Seq[NodeId]])] = endDepths.toArray.sortBy { case (depth, _) => -depth }
        def elementNodes = distanceToNodes.flatMap { case (distance, gIdToNodeIds) =>
          // when distance is 0, we are either showing ourselves (i.e. id) or
          // a cycle that contains ourselves. The latter case we want to draw, the prior not.
          if(!end.inclusive && distance == 0 && gIdToNodeIds.size == 1 && gIdToNodeIds.head._2.size == 1)
            None
          else {
            val sortedByGroupId = gIdToNodeIds.toArray.sortBy(_._1)
            Some(
              // "D:" + distance + " ",
              sortedByGroupId.flatMap { case (gId, nodes) =>
                // sort nodes within a group by their length towards the root node
                // this ensures that e.g. „Channels“ comes first
                nodes.sortBy(n => graph.parentDepth(graph.idToIdxOrThrow(n)))
              }
            )
          }
        }.flatten

        val elements: List[VDomModifier] = {
          (start match {
            case startPoint:EndPoint.Node =>
              if(startPoint.inclusive)
                elementNodes.dropWhile(_ != startPoint.nodeId)
              else
                elementNodes.dropWhile(_ != startPoint.nodeId).dropWhile(_ == startPoint.nodeId)
            case EndPoint.None => elementNodes

          }).map { nid =>
            val onClickFocus = VDomModifier(
              cursor.pointer,
              onClick foreach { e =>
                clickAction(nid)
                e.stopPropagation()
              }
            )
            graph.nodesById(nid) match {
              // hiding the stage/tag prevents accidental zooming into stages/tags, which in turn prevents to create inconsistent GlobalState.
              // example of unwanted inconsistent state: task is only child of stage/tag, but child of nothing else.
              case Some(node) if (end.inclusive || nid != end) && node.role != NodeRole.Stage && node.role != NodeRole.Tag =>
                Components.nodeCardAsOneLineText(node, projectWithIcon = true).apply(
                  cls := "breadcrumb",
                  // VDomModifier.ifTrue(graph.isDeletedNowInAllParents(nid))(cls := "node-deleted"),
                  DragItem.fromNodeRole(node.id, node.role).map(DragComponents.drag(_)),
                  onClickFocus,
                )

              case _ => VDomModifier.empty
            }
          }(breakOut)
        }

        VDomModifier.ifTrue(!hideIfSingle || elements.length > 1)(
          intersperseWhile(elements, div(freeSolid.faAngleRight, cls := "divider"), (mod: VDomModifier) => !mod.isInstanceOf[outwatch.dom.EmptyModifier.type])
        )
      },
      registerDragContainer,
      onClick foreach {
        FeatureState.use(Feature.ClickBreadcrumb)
      },
    )
  }

  private def intersperse[T](list: List[T], co: T): List[T] = list match {
    case one :: two :: rest => one :: co :: intersperse(two :: rest, co)
    //    case one :: Nil         => one :: co :: Nil
    //    case Nil                => Nil
    case short => short
  }
  private def intersperseWhile[T](list: List[T], co: T, cond: T => Boolean): List[T] = list match {
    case one :: two :: rest if cond(one) => one :: co :: intersperseWhile(two :: rest, co, cond)
    case _ :: two :: rest => intersperseWhile(two :: rest, co, cond)
    //    case one :: Nil         => one :: co :: Nil
    //    case Nil                => Nil
    case short => short
  }

  private def cycleIndicator(rotate : Boolean) = {
    //"\u21ba"
    img(
      cls := "cycle-indicator",
      rotate.ifTrue[VDomModifier](transform := "rotate(180deg)"),
      src:= WoostConfig.value.urls.halfCircle
    )
  }

}
