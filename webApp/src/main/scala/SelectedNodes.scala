package wust.webApp

import fastparse.core.Parsed
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
      backgroundColor := "rgba(65,184,255, 0.5)",
      Rx {
        val graph = state.graph()
        val selectedNodeIds = state.selectedNodeIds()
        if (selectedNodeIds.nonEmpty)
          div(
            padding := "5px 5px 2px 5px",
            Styles.flex,
            alignItems.center,
            flexWrap.wrap,
            selectedNodeIds.toSeq
              .sortBy(nodeId => graph.nodeModified(nodeId): Long)
              .map { nodeId =>
                val node = graph.nodesById(nodeId)
                nodeCard(state, node)
              }
          )
        else
          div()
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
