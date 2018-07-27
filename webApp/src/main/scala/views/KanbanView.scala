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
import collection.breakOut

object KanbanView extends View {
  override val key = "kanban"
  override val displayName = "Kanban"

  val maxLength = 100
  val columnWidth = "200px"

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
        val isolatedNodes = graph.nodes.toSeq.filter(n => !graph.hasParents(n.id) && !graph.hasChildren(n.id) && n.isInstanceOf[Node.Content])

        VDomModifier(
          forest.map(tree => renderTree(state, tree)(ctx)(
            cls := "kanbancolumn"
          )),
          renderIsolatedNodes(state, isolatedNodes)
        )
      },

//      div(
//        width := "80px",
//        height := "80px",
//      ),
//        dragTarget(DragItem.SelectedNodesBar),
//      cls := "dropzone",
    )
  }

  private def renderIsolatedNodes(state:GlobalState, nodes:Seq[Node])(implicit ctx: Ctx.Owner) =
    div(
//      backgroundColor := "rgba(128,128,128,0.5)",
      width := columnWidth,
//      height := "100px",
      margin := "0px 5px 20px 5px",
      display.flex,
      flexWrap.wrap,
      alignItems.flexStart,
      nodes.map{ node =>
        div(
//          cls := "dropzone",
//          height := "30px",
//          width := "30px",
        nodeCardCompact(state, node, maxLength = Some(maxLength))(ctx)(
          marginRight := "3px",
          marginBottom := "3px",
          draggableAs(state, DragItem.KanbanCard(node.id)),
          dragTarget(DragItem.KanbanCard(node.id)),
          cls := "draggable-dropzone--occupied",
        )
        )
      }
    )

  private def renderTree(state: GlobalState, tree:Tree)(implicit ctx: Ctx.Owner):VNode = {
    div(
      borderRadius := "3px",
      tree match {
        case Tree.Parent(node, children) =>
          val rendered = Rendered.renderNodeData(node.data, maxLength = Some(maxLength))
          VDomModifier(
            cls := "kanbansubcolumn",
            backgroundColor := RGB("#ff7a8e").hcl.copy(h = hue(tree.node.id)).toHex,

            draggableAs(state, DragItem.KanbanColumn(node.id)),
            dragTarget(DragItem.KanbanColumn(node.id)),
//            cls := "dropzone draggable-dropzone--occupied",

            rendered( children.map(t => renderTree(state, t)(ctx)(marginTop := "8px"))),
            addNodeField(state, node.id)
          )
        case Tree.Leaf(node) =>
          val rendered = renderNodeCardCompact(
            state, node,
            injected = VDomModifier(renderNodeData(node.data, Some(maxLength)))
          )(ctx)(
            draggableAs(state, DragItem.KanbanCard(node.id)),
            dragTarget(DragItem.KanbanCard(node.id)),
//            cls := "dropzone draggable-dropzone--occupied",
          )
          rendered(
            fontSize.medium,
            minWidth := columnWidth
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
