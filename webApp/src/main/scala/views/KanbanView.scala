package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import org.scalajs.dom.console
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._

object KanbanView extends View {
  override val viewKey = "kanban"
  override val displayName = "Kanban"
  override def isContent = true

  val maxLength = 100
  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    div(
      cls := "kanbanview",
      Styles.growFull,

      Rx {
        val page = state.page()
        val graph = {
          val g = state.graph()
          val pageChildren = page.parentIds.flatMap(g.descendants)
          g.filter(page.parentIdSet ++ pageChildren.toSet ++ pageChildren.flatMap(g.authorIds))
        }

        val forest = graph.filter{ nid =>
          val isContent = graph.nodesById(nid).isInstanceOf[Node.Content]
          val notIsolated = graph.hasChildren(nid) || !graph.parents(nid).forall(page.parentIdSet) || graph.isStaticParentIn(nid, page.parentIds)
          val noPage = !page.parentIdSet.contains(nid)
          isContent && notIsolated && noPage
        }.redundantForest
        val isolatedNodes = graph.nodes.toSeq.filter(n => graph.parents(n.id).forall(page.parentIdSet) && !page.parentIdSet.contains(n.id) && !graph.hasChildren(n.id) && !graph.isStaticParentIn(n.id, page.parentIds) && n.isInstanceOf[Node.Content])

        VDomModifier(
          Styles.flex,
          flexDirection.column,
          div(
            cls := s"kanbancolumnarea",
            key := s"kanbancolumnarea",
            registerSortableContainer(state, DragContainer.Kanban.ColumnArea(state.page().parentIds)),
            Styles.flex, // no Styles.flex, since we set a custom minWidth/Height
            alignItems.flexStart,
            overflowX.auto,
            overflowY.hidden,
            forest.map(tree => renderTree(state, tree, parentIds = page.parentIds, isTopLevel = true, inject = cls := "kanbantoplevelcolumn")),
            newColumnArea(state, page)
          ),
          renderIsolatedNodes(state, state.page(), isolatedNodes)(ctx)(Styles.flexStatic)
        )
      }
    )
  }

  private def newColumnArea(state:GlobalState, page:Page)(implicit ctx: Ctx.Owner) = {
    val fieldActive = Var(false)
    div(
      cls := s"kanbannewcolumnarea",
      key := s"kanbannewcolumnarea",
      registerSortableContainer(state, DragContainer.Kanban.NewColumnArea(page.parentIds)),
      onClick(true) --> fieldActive,
      position.relative,
      Rx {
        if(fieldActive())
          div(
            cls := "kanbannewcolumnareaform",
            cls := "ui form",
            textArea(
              cls := "field fluid",
              fontSize.larger,
              fontWeight.bold,
              rows := 2,
              placeholder := "Press Enter to add.",
              valueWithEnter --> sideEffect { str =>
                val graph = state.graphContent.now
                val selectedNodeIds = state.selectedNodeIds.now
                val change = {
                  val newColumnNode = Node.Content(NodeData.Markdown(str))
                  val add = GraphChanges.addNode(newColumnNode)
                  val makeStatic = GraphChanges.connect(Edge.StaticParentIn)(newColumnNode.id, page.parentIds)
                  add merge makeStatic
                }
                fieldActive() = false
                state.eventProcessor.enriched.changes.onNext(change)
              },
              key := cuid.Cuid(),
              onInsert.asHtml --> sideEffect{elem => elem.focus()},
              onBlur.value --> sideEffect{v => if(v.isEmpty) fieldActive() = false}
            )
          )
        else
          div(
            position.absolute,
            top := "0",
            left := "0",
            right := "0",
            cls := "kanbannewcolumnareacontent",
            "+ Add Column"
          )
      }
    )
  }

  private def renderTree(state: GlobalState, tree:Tree, parentIds:Seq[NodeId], isTopLevel:Boolean = false, inject:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner):VDomModifier = {
    tree match {
      case Tree.Parent(node, children) => renderColumn(state, node, children, parentIds, isTopLevel = isTopLevel)(ctx)(inject)
      case Tree.Leaf(node) =>
        Rx{
          if(state.graph().isStaticParentIn(node.id, parentIds))
            renderColumn(state, node, Nil, parentIds, isTopLevel = isTopLevel, isStaticParent = true)(ctx)(inject)
          else
            renderCard(state, node, parentIds)(ctx)(inject)
        }
    }
  }

  private def renderColumn(state: GlobalState, node: Node, children: List[Tree], parentIds:Seq[NodeId], isTopLevel:Boolean = false, isStaticParent:Boolean = false)(implicit ctx: Ctx.Owner):VNode = {

    val editable = Var(false)
    val columnTitle = editableNode(state, node, editable = editable, submit = state.eventProcessor.enriched.changes, maxLength = Some(maxLength))(ctx)(cls := "kanbancolumntitle")

    val buttonBar = div(
      cls := "kanbanbuttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
          VDomModifier.empty
        } else VDomModifier(
          div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, title := "Edit"),
          isStaticParent.ifTrue[VDomModifier](div(div(cls := "fa-fw", if(isTopLevel) freeSolid.faTimes else freeRegular.faMinusSquare), onClick.stopPropagation(GraphChanges.disconnect(Edge.StaticParentIn)(node.id, parentIds)) --> state.eventProcessor.changes, cursor.pointer, title := "Shrink to Node")),
          div(div(cls := "fa-fw", freeRegular.faTrashAlt), onClick.stopPropagation(GraphChanges.delete(node, state.graph.now, state.page.now)) --> state.eventProcessor.changes, cursor.pointer, title := "Delete"),
          div(div(cls := "fa-fw", freeRegular.faArrowAltCircleRight), onClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig, cursor.pointer, title := "Zoom in"),
        )
      }
    )

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      isTopLevel.ifFalse[VDomModifier](cls := "kanbansubcolumn"),
      backgroundColor := BaseColors.kanbanColumnBg.copy(h = hue(node.id)).toHex,
      if(isTopLevel) VDomModifier(
        draggableAs(state, DragItem.Kanban.ToplevelColumn(node.id)),
        dragTarget(DragItem.Kanban.ToplevelColumn(node.id)) ,
      ) else VDomModifier(
        draggableAs(state, DragItem.Kanban.SubColumn(node.id)),
        dragTarget(DragItem.Kanban.SubColumn(node.id))
      ),
      div(
        cls := "kanbancolumnheader",
        cls := "draghandle",
        columnTitle,

        position.relative,
        buttonBar(position.absolute, top := "0", right := "0")
      ),
      div(
        cls := "kanbancolumnchildren",
        registerSortableContainer(state, DragContainer.Kanban.Column(node.id)),
        key := s"sortablecolumn${node.id}parent${parentIds.mkString}",

        children.map(t => renderTree(state, t, parentIds = node.id :: Nil)),
      ),
      addNodeField(state, node.id) // does not belong to sortable container => always stays at the bottom. TODO: is this a draggable bug? If last element is not draggable, it can still be pushed away by a movable element
    )
  }

  private def renderCard(state:GlobalState, node:Node, parentIds:Seq[NodeId])(implicit ctx: Ctx.Owner):VNode = {
    val editable = Var(false)
    val rendered = nodeCardEditable(
      state, node,
      maxLength = Some(maxLength),
      editable = editable,
      submit = state.eventProcessor.enriched.changes
    )


    val buttonBar = div(
      cls := "kanbanbuttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
//          div(div(cls := "fa-fw", freeSolid.faCheck), onClick.stopPropagation(false) --> editable, cursor.pointer)
          VDomModifier.empty
        } else VDomModifier(
          div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, title := "Edit"),
          div(div(cls := "fa-fw", freeSolid.faExpand), onClick.stopPropagation(GraphChanges.connect(Edge.StaticParentIn)(node.id, parentIds)) --> state.eventProcessor.changes, cursor.pointer, title := "Expand to column"),
          div(div(cls := "fa-fw", freeRegular.faTrashAlt), onClick.stopPropagation(GraphChanges.delete(node, state.graph.now, state.page.now)) --> state.eventProcessor.changes, cursor.pointer, title := "Delete"),
        )
      }
    )

    rendered(
      // sortable: draggable needs to be direct child of container
      editable.map(editable => if(editable) draggableAs(state, DragItem.DisableDrag) else draggableAs(state, DragItem.Kanban.Card(node.id))), // prevents dragging when selecting text
      dragTarget(DragItem.Kanban.Card(node.id)),
      key := s"node${node.id}parent${parentIds.mkString}",
      cls := "draghandle",

      position.relative,
      buttonBar(position.absolute, top := "0", right := "0")
    )
  }

  private def addNodeField(state: GlobalState, parentId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val active = Var(false)
    div(
      cls := "kanbanaddnodefield",
      Rx {
        if(active())
          div(
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
            "+ Add Card",
            onClick(true) --> active
          )
      }
    )
  }

  private def renderIsolatedNodes(state:GlobalState, page:Page, nodes:Seq[Node])(implicit ctx: Ctx.Owner) =
    div(
      cls := "kanbanisolatednodes",
      key := s"kanbanisolatednodes",
      registerSortableContainer(state, DragContainer.Kanban.IsolatedNodes(page.parentIds)),

      nodes.map{ node => renderCard(state, node, page.parentIds) }
    )

}
