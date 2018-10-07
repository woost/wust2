package wust.webApp.views

import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph.Node
import wust.ids._
import wust.util._
import wust.util.time.time
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

object BreadCrumbs {

  /** options */
  val showOwn = true
  val channelsAlwaysFirst = true ///< whether "Channels" should always come first

  def intersperse[T](list: List[T], co: T): List[T] = list match {
    case one :: two :: rest => one :: co :: intersperse(two :: rest, co)
    case short              => short
  }

  def cycleIndicator(rotate : Boolean) = {
    //"\u21ba"
    img(
      cls := "cycle-indicator",
      rotate.ifTrue[VDomModifier](style("transform") := "rotate(180deg)"),
      src:="halfCircle.svg",
    )
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "breadcrumbs",
      Rx {
        time("bench: breadcrumbs") {
        val page = state.page()
        val user = state.user()
        val graph =  state.graph().filter{case _:Node.User => false; case n if n.id == user.channelNodeId => false; case _ => true}
        val parentIds = page.parentIds
        parentIds.map { (parentId : NodeId) =>
          val parentDepths = graph.parentDepths(parentId)
          val distanceToNodes = parentDepths.toSeq.sortBy { case (depth, _) => -depth }
          val elements = distanceToNodes.map { case (distance, gIdToNodeIds) =>
            // when distance is 0, we are either showing ourselves (i.e. id) or
            // a cycle that contains ourselves. The latter case we want to draw, the prior not.
            if(!showOwn && distance == 0 && gIdToNodeIds.size == 1 && gIdToNodeIds.toSeq.head._2.size == 1)
              None
            else {
              val sortedByGroupId = gIdToNodeIds.toSeq.sortBy(_._1)
              Some(span(
                     // "D:" + distance + " ",
                     sortedByGroupId.map { case (gId, nodes) =>
                       // sort nodes within a group by their length towards the root node
                       // this ensures that e.g. „Channels“ comes first
                       val sortedNodes = nodes.sortBy(graph.lookup.parentDepth(_))
                       span(
                         cls := "breadcrumb",
                         if (gId != -1) cycleIndicator(false) else "",
                         sortedNodes.map{ (n : NodeId) =>
                           graph.nodesById.get(n) match {
                             case Some(node) if (showOwn || n != parentId) => nodeTag(state, node)(cursor.pointer)
                             case _ => span()
                           }
                         } : Seq[VNode],
                         if (gId != -1) cycleIndicator(true) else "",
                         )
                     }.toSeq
                   ))
              }
            }
            div(intersperse(elements.toList.flatten, span("/", cls := "divider")))
          }
        }
      },
      registerDraggableContainer(state),
      onClick handleWith{Analytics.sendEvent("breadcrumbs", "click")},
    )
  }
}
