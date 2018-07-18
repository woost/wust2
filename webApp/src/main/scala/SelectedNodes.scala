package wust.webApp

import cats.data.{NonEmptyList, NonEmptySet}
import fastparse.core.Parsed
import cats.syntax._
import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util._
import wust.util.collection._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._

import scala.collection.breakOut

object SelectedNodes {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      backgroundColor := "#85D5FF",
      Rx {
        val graph = state.graph()
        val sortedNodeIds = state.selectedNodeIds().toList.sortBy(nodeId => graph.nodeModified(nodeId): Long)
        NonEmptyList.fromList(sortedNodeIds) match {
          case Some(nonEmptyNodeIds) =>
            div(
              padding := "5px 5px 2px 5px",
              Styles.flex,
              alignItems.center,

              nodeList(state, nonEmptyNodeIds, state.graph())(ctx)(marginRight.auto),
              deleteAllButton(state, nonEmptyNodeIds),
              clearSelectionButton(state)
            )
          case None => div()
        }
      }
    )
  }

  private def nodeList(state:GlobalState, selectedNodeIds:NonEmptyList[NodeId], graph:Graph)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      alignItems.center,
      flexWrap.wrap,
      selectedNodeIds.toList.map { nodeId =>
          val node = graph.nodesById(nodeId)
          nodeCard(state, node)
        }
    )
  }

  private def deleteAllButton(state:GlobalState, selectedNodeIds:NonEmptyList[NodeId])(implicit ctx: Ctx.Owner) = {
    div(
      freeRegular.faTrashAlt,
      cls := "removebutton",
      margin := "5px",

      onClick --> sideEffect{_ =>
        val changes = GraphChanges.delete(selectedNodeIds.toList, state.graph.now, state.page.now)
        state.eventProcessor.changes.onNext(changes)
        state.selectedNodeIds() = Set.empty[NodeId]
      }
    )
  }

  private def clearSelectionButton(state:GlobalState) = {
    div(
      "×",
      cls := "removebutton",
      margin := "5px",
      fontWeight.bold,
      onClick --> sideEffect {
        state.selectedNodeIds() = Set.empty[NodeId]
      }
    )
  }

  private def nodeCard(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner) = {
    nodeCardCompact(state,node,injected = Seq[VDomModifier](
      Styles.flex,
      alignItems.center,
      span(
        "×",
        cls := "removebutton",
        onClick.stopPropagation --> sideEffect {
          state.selectedNodeIds.update(_ - node.id)
        }
      )
    ), cutLength = true)(ctx)(marginBottom := "3px")
  }
}
