package wust.webApp.views

import wust.webApp.dragdrop._
import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import wust.graph.Node
import wust.ids._
import wust.util._
import wust.webApp.Ownable
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

object BreadCrumbs {

  /** options */
  private val showOwn = true

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
      src:="halfCircle.svg",
    )
  }

  def apply(state: GlobalState): VNode = {
    div.static(keyValue)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "breadcrumbs",
        Rx {
          val page = state.page()
          val user = state.user()
          val graph = state.graph()
          page.parentId.map { (parentId: NodeId) =>
            val parentDepths = graph.parentDepths(parentId)
            val distanceToNodes = parentDepths.toList.sortBy { case (depth, _) => -depth }
            def elements = distanceToNodes.flatMap { case (distance, gIdToNodeIds) =>
              // when distance is 0, we are either showing ourselves (i.e. id) or
              // a cycle that contains ourselves. The latter case we want to draw, the prior not.
              if(!showOwn && distance == 0 && gIdToNodeIds.size == 1 && gIdToNodeIds.head._2.size == 1)
                None
              else {
                val sortedByGroupId = gIdToNodeIds.toList.sortBy(_._1)
                Some(
                  // "D:" + distance + " ",
                  sortedByGroupId.flatMap { case (gId, nodes) =>
                    // sort nodes within a group by their length towards the root node
                    // this ensures that e.g. „Channels“ comes first
                    val sortedNodes = nodes.sortBy(graph.parentDepth(_))
                    sortedNodes.map { nid: NodeId =>
                      graph.nodesByIdGet(nid) match {
                        // hiding the stage/tag prevents accidental zooming into stages/tags, which in turn prevents to create inconsistent state.
                        // example of unwanted inconsistent state: task is only child of stage/tag, but child of nothing else.
                        case Some(node) if (showOwn || nid != parentId) && node.role != NodeRole.Stage && node.role != NodeRole.Tag =>
                          span(
                            cls := "breadcrumb",
                            nodeTag(state, node, dragOptions = nodeId => drag(DragItem.BreadCrumb(nodeId)), pageOnClick = true)(cursor.pointer),
                          )
                        case _                                                  =>
                          VDomModifier.empty
                      }
                    }
                  }.toSeq
                )
              }
            }.flatten
            div(intersperseWhile(elements, span("/", cls := "divider"), (mod: VDomModifier) => !mod.isInstanceOf[outwatch.dom.EmptyModifier.type]))
          }
        },
        registerDragContainer(state),
        onClick foreach { Analytics.sendEvent("breadcrumbs", "click") },
      )
    })
  }
}
