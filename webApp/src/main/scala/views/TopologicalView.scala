package wust.webApp.views

import concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import org.scalajs.dom
import monix.execution.Cancelable
import wust.webApp.dragdrop.{ DragContainer, DragItem }
import fontAwesome.freeSolid
import SharedViewElements._
import wust.webApp.{ BrowserDetect, Icons, ItemProperties }
import wust.webApp.Icons
import outwatch.dom._
import wust.sdk.{ BaseColors, NodeColor }
import outwatch.dom.dsl._
import styles.extra.{ transform, transformOrigin }
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.views.Elements._
import monix.reactive.subjects.{ BehaviorSubject, PublishSubject }
import rx._
import wust.css.{ Styles, ZIndex }
import wust.graph._
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{ FocusState, GlobalState }
import wust.webApp.views.Components._
import wust.util._
import d3v4._
import org.scalajs.dom.console

// Topologically sorted items by property relations
object TopologicalView {
  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    case class NodeInfo(node: Node, depth: Int)

    val propertyName = Var("depends on")
    val nodeDepth: Rx[Array[Int]] = Rx {
      val graph = state.graph()
      val lookup = graph.propertyLookup(propertyName())
      algorithm.longestPathsIdx(lookup)
    }

    val nodeInfos: Rx[Array[NodeInfo]] = Rx {
      val graph = state.graph()
      val depth = nodeDepth()
      val nodeIdx = graph.idToIdxOrThrow(focusState.focusedId)

      graph.childrenIdx.flatMap(nodeIdx) { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        if (node.role == NodeRole.Task) {
          Array(NodeInfo(node, depth(nodeIdx)))
        } else Array.empty[NodeInfo]
      }.sortBy(_.depth)
    }

    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      padding := "20px",

      div(
        Styles.flex,
        justifyContent.flexEnd,
        alignItems.center,
        marginBottom := "10px",

        div("Drag items onto each other to connect.", opacity := 0.5, marginRight.auto),
        div("Field:", marginRight := "10px"),
        div(
          cls := "ui input",
          input(
            tpe := "text",
            value <-- propertyName,
            onInput.value.debounce(300 milliseconds) --> propertyName,
            marginLeft.auto,
          )
        )
      ),
      Rx {
        var lastLevel = 0
        nodeInfos().map { nodeInfo =>
          val isNewGroup = lastLevel != nodeInfo.depth
          lastLevel = nodeInfo.depth
          nodeCard(nodeInfo.node).apply(
            Styles.flex,
            marginBottom := "2px",
            VDomModifier.ifTrue(isNewGroup)(marginTop := "40px"),
            drag(DragItem.TaskConnect(nodeInfo.node.id, propertyName()))
          )
        }
      },
      registerDragContainer(state),
    )
  }
}
