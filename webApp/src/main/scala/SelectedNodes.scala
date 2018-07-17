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
      Rx {
        val graph = state.graph()
        val selectedNodeIds = state.selectedNodeIds()
        if (selectedNodeIds.nonEmpty)
          div(
            padding := "5px",
            Styles.flex,
            flexWrap.wrap,
            selectedNodeIds.toSeq
              .sortBy(nodeId => graph.nodeModified(nodeId): Long)
              .map { nodeId =>
                val node = graph.nodesById(nodeId)
                div(
                  cls := "hard-shadow chatmsg-card",
                  marginBottom := "3px",
                  div(
                    cls := "chatmsg-content",
                    editableNode(state, node, span(node.data.str)),
                    span(
                      "×",
                      cls := "removebutton",
                      onClick.stopPropagation --> sideEffect {
                        state.selectedNodeIds.update(_ - nodeId)
                      }
                    )
                  )
                )
              }
          )
        else
          div()
      }
    )
  }
}
