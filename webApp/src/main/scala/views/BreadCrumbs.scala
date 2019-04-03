package wust.webApp.views

import wust.sdk.{BaseColors, NodeColor}
import wust.css.Styles
import wust.webApp.dragdrop._
import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import wust.graph.{Node, Page}
import wust.ids._
import wust.util._
import wust.webApp.Ownable
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import scala.collection.breakOut

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
      modifier(state, None, state.page.map(_.parentId), nid => state.urlConfig.update(_.focus(Page(nid))))
    })
  }
  def apply(state: GlobalState, filterUpTo: Option[NodeId], parentIdRx: Rx[Option[NodeId]], parentIdAction: NodeId => Unit): VNode = {
    div.static(keyValue)(Ownable { implicit ctx =>
      modifier(state, filterUpTo, parentIdRx, parentIdAction)
    })
  }

  private def modifier(state: GlobalState, filterUpTo: Option[NodeId], parentIdRx: Rx[Option[NodeId]], parentIdAction: NodeId => Unit)(implicit ctx: Ctx.Owner) = {
    VDomModifier(
      cls := "breadcrumbs",
      Rx {
        val parentId = parentIdRx()
        val user = state.user()
        val graph = state.rawGraph()
        parentId.map { (parentId: NodeId) =>
          val parentDepths: Map[Int, Map[Int, Seq[NodeId]]] = graph.notDeletedParentDepths(parentId)
          val distanceToNodes: Seq[(Int, Map[Int, Seq[NodeId]])] = parentDepths.toList.sortBy { case (depth, _) => -depth }
          def elementNodes = distanceToNodes.flatMap { case (distance, gIdToNodeIds) =>
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
                  nodes.sortBy(graph.parentDepth(_))
                }
              )
            }
          }.flatten

          val elements: List[VDomModifier] = filterUpTo.fold(elementNodes)(id => elementNodes.dropWhile(_ != id)).map { nid =>
            val onClickFocus = VDomModifier(
              cursor.pointer,
              onClick foreach { e =>
                parentIdAction(nid)
                e.stopPropagation()
              }
            )
            graph.nodesByIdGet(nid) match {
              // hiding the stage/tag prevents accidental zooming into stages/tags, which in turn prevents to create inconsistent state.
              // example of unwanted inconsistent state: task is only child of stage/tag, but child of nothing else.
              case Some(node) if (showOwn || nid != parentId) && node.role != NodeRole.Stage && node.role != NodeRole.Tag =>
                (node.role match {
                  case NodeRole.Message | NodeRole.Task | NodeRole.Note =>
                    nodeCardAsOneLineText(node)(onClickFocus)
                  case _                                => // usually NodeRole.Project
                    nodeTag(state, node, dragOptions = nodeId => drag(DragItem.BreadCrumb(nodeId)))(
                      onClickFocus,
                      backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(node.id)).toHex,
                      border := "1px solid",
                      borderColor := BaseColors.pageBorder.copy(h = NodeColor.hue(node.id)).toHex,
                      color.black,
                    ).prepend(
                      Styles.flex,
                      nodeAvatar(node, size = 13)(marginRight := "5px", marginTop := "2px", flexShrink := 0),
                    )
                }).apply(cls := "breadcrumb")

              case _                                                  => VDomModifier.empty
            }
          }(breakOut)

          intersperseWhile(elements, span("/", cls := "divider"), (mod: VDomModifier) => !mod.isInstanceOf[outwatch.dom.EmptyModifier.type])
        }
      },
      registerDragContainer(state),
      onClick foreach { Analytics.sendEvent("breadcrumbs", "click") },
    )
  }
}
