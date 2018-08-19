package wust.webApp.views

import fontAwesome._
import fontAwesome.freeSolid._
import googleAnalytics.Analytics
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._

import scala.collection.immutable.{ListMap, SortedMap}
import wust.api.AuthUser
import wust.graph._
import wust.ids._
import wust.sdk.ChangesHistory
import wust.sdk.NodeColor._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Components._
import wust.webApp.views._
import wust.util._
import wust.webApp.state.GlobalState

import scala.scalajs.js
import scala.scalajs.js.Date

object BreadCrumbs {
  import MainViewParts._

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
        val page = state.page()
        val graph = state.graph()
        val parentIds = page.parentIds
        parentIds.map { (parentId : NodeId) =>
          val parentDepths = graph.parentDepths(parentId)
          val distanceToNodes = parentDepths.toSeq.sortBy(_._1).reverse
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
                       span(
                         cls := "breadcrumb",
                         if (gId != -1) cycleIndicator(false) else "",
                         nodes.map{ (n : NodeId) =>
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
          intersperse(elements.toList.flatten, span("/", cls := "divider"))
        }.flatten
      },
      registerDraggableContainer(state),
      onClick --> sideEffect{Analytics.sendEvent("breadcrumbs", "click")},
    )
  }
}
