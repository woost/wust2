package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.sdk.NodeColor._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import Elements._
import Rendered._
import colorado.RGB
import wust.ids.{NodeData, NodeId}
import wust.webApp.parsers.NodeDataParser

object KanbanView extends View {
  override val key = "kanban"
  override val displayName = "Kanban"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    div(
      Styles.flex,
      alignItems.flexStart,
      flexWrap.wrap,
      overflow.auto,
      Rx {
        val graph = state.graphContent()
        val forest = graph.filter{ nid =>
          val isContent = graph.nodesById(nid).isInstanceOf[Node.Content]
          val notIsolated = graph.hasChildren(nid) || graph.hasParents(nid)
          isContent && notIsolated
        }.redundantForest

        forest.map(tree => renderTree(state, tree)(ctx)(
          margin := "0px 5px 20px 5px",
          boxShadow := "0px 1px 0px 1px rgba(158,158,158,0.45)",
        ))
      }
    )
  }

  private def renderTree(state: GlobalState, tree:Tree)(implicit ctx: Ctx.Owner):VNode = {
    div(
      borderRadius := "3px",
      tree match {
        case Tree.Parent(node, children) =>
          val rendered = Rendered.renderNodeData(node.data, maxLength = Some(100))
          VDomModifier(
            padding := "7px",
            color := "white",
            fontWeight.bold,
            fontSize.large,
            backgroundColor := RGB("#ff7a8e").hcl.copy(h = hue(tree.node.id)).toHex,
            boxShadow := "0px 1px 0px 1px rgba(99,99,99,0.45)",

            draggableAs(state, DragPayload.Node(node.id)),
            dragTarget(DragTarget.Parent(node.id)),

            rendered( children.map(t => renderTree(state, t)(ctx)(marginTop := "8px"))),
            addNodeField(state, node.id)
          )
        case Tree.Leaf(node) =>
          val rendered = renderNodeCardCompact(
            state, node,
            injected = VDomModifier(renderNodeData(node.data, Some(100)))
          )(ctx)(
            draggableAs(state, DragPayload.Node(node.id)),
            dragTarget(DragTarget.Node(node.id)),
          )
          rendered(
            fontSize.medium,
            minWidth := "200px"
          )
      }
    )
  }

  private def addNodeField(state: GlobalState, parentId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val active = Var(false)
    div(
      Rx {
        if(active())
          div(
            marginTop := "10px",
            cls := "ui form",
            textArea(
              cls := "field fluid",
              rows := 2,
              placeholder := "Press Enter to add.",
              valueWithEnter --> sideEffect { str =>
                val graph = state.graphContent.now
                val selectedNodeIds = state.selectedNodeIds.now
                val change = GraphChanges.addNodeWithParent(Node.Content(NodeData.Markdown(str)), parentId)
                active() = false
                state.eventProcessor.enriched.changes.onNext(change)
              },
              onInsert.asHtml --> sideEffect{elem => elem.focus()}
            )
          )
        else
          div(
            marginTop := "10px",
            fontSize.medium,
            fontWeight.normal,
            cursor.pointer,
            color := "rgba(255,255,255,0.5)",
            "+ Add Node",
            onClick(true) --> active
          )
      }
    )
  }
}
