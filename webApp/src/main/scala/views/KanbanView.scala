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
import fontAwesome.{freeRegular, freeSolid}
import org.scalajs.dom.raw.HTMLInputElement
import wust.ids.EdgeData.StaticParentIn
import wust.ids.{NodeData, NodeId}
import wust.sdk.BaseColors
import wust.webApp.parsers.NodeDataParser
import wust.util._

import scala.collection.breakOut

object KanbanView extends View {
  override val viewKey = "kanban"
  override val displayName = "Kanban"

  val maxLength = 100
  val columnWidth = "200px"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    div(
      overflow.auto,
      Styles.growFull,

      Rx {
        val graph = state.graphContent()
        val page = state.page()

        val forest = graph.filter{ nid =>
          val isContent = graph.nodesById(nid).isInstanceOf[Node.Content]
          val notIsolated = graph.hasChildren(nid) || graph.hasParents(nid)
          isContent && notIsolated
        }.redundantForest
        val isolatedNodes = graph.nodes.toSeq.filter(n => !graph.hasParents(n.id) && !graph.hasChildren(n.id) && n.isInstanceOf[Node.Content])

        VDomModifier(
          div(
            key := s"kanbancolumns",
            registerSortableContainer(state, DragContainer.Page(state.page().parentIds)),
            Styles.flex,
            alignItems.flexStart,
            flexWrap.wrap,
            overflow.auto,
            forest.map(tree => renderTree(state, tree, parentIds = page.parentIds, inject = cls := "kanbancolumn")),
          ),
          renderIsolatedNodes(state, state.page(), isolatedNodes)
        )
      }
    )
  }

  private def renderTree(state: GlobalState, tree:Tree, parentIds:Seq[NodeId], inject:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner):VDomModifier = {
    tree match {
      case Tree.Parent(node, children) => renderColumn(state, node, children, parentIds)(ctx)(inject)
      case Tree.Leaf(node) =>
        Rx{
          if(state.graph().isStaticParentIn(node.id, parentIds))
            renderColumn(state, node, Nil, parentIds, isStaticParent = true)(ctx)(inject)
          else
            renderCard(state, node, parentIds)(ctx)(inject)
        }
    }
  }

  private def renderColumn(state: GlobalState, node: Node, children: List[Tree], parentIds:Seq[NodeId], isStaticParent:Boolean = false)(implicit ctx: Ctx.Owner):VNode = {
    val columnTitle = Rendered.renderNodeData(node.data, maxLength = Some(maxLength))(cls := "kanbancolumntitle")
    div(
      cls := "kanbansubcolumn",
      backgroundColor := BaseColors.kanbanColumnBg.copy(h = hue(node.id)).toHex,
      borderRadius := "3px",
      draggableAs(state, DragItem.KanbanColumn(node.id)), // sortable: draggable needs to be direct child of container
      dragTarget(DragItem.KanbanColumn(node.id)),
      key := s"draggablecolumn${node.id}parent${parentIds.mkString}",
      div(
        registerSortableContainer(state, DragContainer.KanbanColumn(node.id)),
        //            key := s"sortablecolumn${tree.node.id}parent${parentId}",

        div(
          Styles.flex,
          justifyContent.spaceBetween,
          columnTitle,
          isStaticParent.ifTrue[VDomModifier](div(freeRegular.faMinusSquare, onClick(GraphChanges.disconnect(Edge.StaticParentIn)(node.id, parentIds)) --> state.eventProcessor.changes, cursor.pointer))
        ),
        children.map(t => renderTree(state, t, parentIds = node.id :: Nil, inject = marginTop := "8px")),
      ),
      addNodeField(state, node.id) // does not belong to sortable container => always stays at the bottom
    )
  }

  private def renderCard(state:GlobalState, node:Node, parentIds:Seq[NodeId])(implicit ctx: Ctx.Owner):VNode = {
    val rendered = renderNodeCardCompact(
      state, node,
      injected = VDomModifier(renderNodeData(node.data, Some(maxLength)))
    )
    val buttonBar = div(
        padding := "4px",
        paddingLeft := "0px",
        Styles.flexStatic,
        div(freeRegular.faListAlt, onClick(GraphChanges.connect(Edge.StaticParentIn)(node.id, parentIds)) --> state.eventProcessor.changes, cursor.pointer)
      )
    rendered(
      draggableAs(state, DragItem.KanbanCard(node.id)), // sortable: draggable needs to be direct child of container
      dragTarget(DragItem.KanbanCard(node.id)),
      key := s"node${node.id}parent${parentIds.mkString}",

      Styles.flex,
      justifyContent.spaceBetween,
      buttonBar
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
              key := cuid.Cuid(),
              onInsert.asHtml --> sideEffect{elem => elem.focus()},
              onBlur.value --> sideEffect{v => if(v.isEmpty) active() = false}
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

  private def renderIsolatedNodes(state:GlobalState, page:Page, nodes:Seq[Node])(implicit ctx: Ctx.Owner) =
    div(
      cls := "kanbanisolatednodes",
      registerSortableContainer(state, DragContainer.Page(page.parentIds)),
      //      key := s"kanbanisolatednodes",

      minHeight := "30px",

      margin := "0px 5px 20px 5px",
      Styles.flex,
      flexWrap.wrap,
      alignItems.flexStart,
      nodes.map{ node =>
        nodeCardCompact(state, node, maxLength = Some(maxLength))(ctx)(
          key := s"kanbanisolated${node.id}",
          marginRight := "3px",
          marginTop := "8px",
          draggableAs(state, DragItem.KanbanCard(node.id)),
          dragTarget(DragItem.KanbanCard(node.id)),
        )
      }
    )

}
