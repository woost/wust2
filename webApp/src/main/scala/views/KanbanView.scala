package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import wust.webApp.Icons
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object KanbanView {

  private val maxLength = 100
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {


    val activeReplyFields = Var(Set.empty[List[NodeId]])
    val newColumnFieldActive = Var(false)

    div(
      cls := "kanbanview",
      Styles.growFull,

      Rx {
        val page = state.page()
        val graph = {
          val g = state.graph()
          val pageChildren = page.parentIds.flatMap(g.descendants)
          g.filterIds(page.parentIdSet ++ pageChildren.toSet ++ pageChildren.flatMap(g.authorIds))
        }

        val forest = graph.filterIds { nid =>
          val isContent = graph.nodesById(nid).isInstanceOf[Node.Content]
          val notIsolated = graph.hasChildren(nid) || !graph.parents(nid).forall(page.parentIdSet) || graph.isStaticParentIn(nid, page.parentIds)
          val noPage = !page.parentIdSet.contains(nid)
          isContent && notIsolated && noPage
        }.lookup.redundantForestIncludingCycleLeafs
        val isolatedNodes = graph.nodes.toSeq.filter(n => graph.parents(n.id).exists(page.parentIdSet) && !page.parentIdSet.contains(n.id) && !graph.hasChildren(n.id) && !graph.isStaticParentIn(n.id, page.parentIds) && n.isInstanceOf[Node.Content])

        VDomModifier(
          Styles.flex,
          flexDirection.column,
          div(
            cls := s"kanbancolumnarea",
            keyed,
            registerSortableContainer(state, DragContainer.Kanban.ColumnArea(state.page().parentIds)),
            Styles.flex, // no Styles.flex, since we set a custom minWidth/Height
            alignItems.flexStart,
            overflowX.auto,
            overflowY.hidden,
            forest.map(tree => renderTree(state, tree, parentIds = page.parentIds, path = Nil, activeReplyFields, isTopLevel = true, inject = cls := "kanbantoplevelcolumn")),
            newColumnArea(state, page, newColumnFieldActive)
          ),
          renderIsolatedNodes(state, state.page(), isolatedNodes)(ctx)(Styles.flexStatic)
        )
      }
    )
  }

  private def newColumnArea(state: GlobalState, page: Page, fieldActive: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    div(
      cls := s"kanbannewcolumnarea",
      keyed,
      registerSortableContainer(state, DragContainer.Kanban.NewColumnArea(page.parentIds)),
      onClick.stopPropagation(true) --> fieldActive,
      position.relative,
      Rx {
        if(fieldActive()) {
          div(
            keyed,
            cls := "kanbannewcolumnareaform",
            cls := "ui form",
            textArea(
              keyed,
              cls := "field fluid",
              fontSize.larger,
              fontWeight.bold,
              rows := 2,
              placeholder := "Press Enter to add.",
              valueWithEnter handleWith { str =>
                val change = {
                  val newColumnNode = Node.Content(NodeData.Markdown(str))
                  val add = GraphChanges.addNode(newColumnNode)
                  val makeStatic = GraphChanges.connect(Edge.StaticParentIn)(newColumnNode.id, page.parentIds)
                  add merge makeStatic
                }
                state.eventProcessor.enriched.changes.onNext(change)
              },
              onDomMount.asHtml --> inAnimationFrame(_.focus()),
              onBlur.value handleWith { v => if(v.isEmpty) fieldActive() = false }
            )
          )
        }
        else
          div(
            keyed,
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

  private def renderTree(state: GlobalState, tree: Tree, parentIds: Seq[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]], isTopLevel: Boolean = false, inject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VDomModifier = {
    tree match {
      case Tree.Parent(node, children) => renderColumn(state, node, children.toSeq, parentIds, path, activeReplyFields, isTopLevel = isTopLevel)(ctx)(inject)
      case Tree.Leaf(node)             =>
        Rx {
          if(state.graph().isStaticParentIn(node.id, parentIds))
            renderColumn(state, node, Nil, parentIds, path, activeReplyFields, isTopLevel = isTopLevel, isStaticParent = true)(ctx)(inject)
          else
            renderCard(state, node, parentIds)(ctx)(inject)
        }
    }
  }

  private def renderColumn(state: GlobalState, node: Node, children: Seq[Tree], parentIds: Seq[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]], isTopLevel: Boolean = false, isStaticParent: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {

    val editable = Var(false)
    val columnTitle = editableNode(state, node, editable = editable, submit = state.eventProcessor.enriched.changes, newTagParentIds = parentIds, maxLength = Some(maxLength))(ctx)(cls := "kanbancolumntitle")

    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
          VDomModifier.empty
        } else VDomModifier(
          div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, title := "Edit"),
          isStaticParent.ifTrue[VDomModifier](div(div(cls := "fa-fw", freeRegular.faMinusSquare), onClick.stopPropagation(GraphChanges.disconnect(Edge.StaticParentIn)(node.id, parentIds)) --> state.eventProcessor.changes, cursor.pointer, title := "Shrink to Node")),
          div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation handleWith {
              state.eventProcessor.changes.onNext(GraphChanges.delete(node.id, state.graph.now))
              state.selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, title := "Delete"
          ),
          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig, cursor.pointer, title := "Zoom in"),
        )
      }
    )

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      keyed(node.id, parentIds),
      isTopLevel.ifFalse[VDomModifier](cls := "kanbansubcolumn"),
      backgroundColor := BaseColors.kanbanColumnBg.copy(h = hue(node.id)).toHex,
      if(isTopLevel) VDomModifier(
        draggableAs(state, DragItem.Kanban.ToplevelColumn(node.id)),
        dragTarget(DragItem.Kanban.ToplevelColumn(node.id)),
      ) else VDomModifier(
        draggableAs(state, DragItem.Kanban.SubColumn(node.id)),
        dragTarget(DragItem.Kanban.SubColumn(node.id))
      ),
      div(
        cls := "kanbancolumnheader",
        keyed(node.id, parentIds),
        cls := "draghandle",
        columnTitle,

        position.relative,
        buttonBar(position.absolute, top := "0", right := "0"),
        onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
      ),
      div(
        cls := "kanbancolumnchildren",
        registerSortableContainer(state, DragContainer.Kanban.Column(node.id)),
        keyed(node.id, parentIds),
        children.map(t => renderTree(state, t, parentIds = node.id :: Nil, path = node.id :: path, activeReplyFields)),
      ),
      addNodeField(state, node.id, path, activeReplyFields) // does not belong to sortable container => always stays at the bottom. TODO: is this a draggable bug? If last element is not draggable, it can still be pushed away by a movable element
    )
  }

  private def renderCard(state: GlobalState, node: Node, parentIds: Seq[NodeId])(implicit ctx: Ctx.Owner): VNode = {
    val editable = Var(false)
    val rendered = nodeCardEditable(
      state, node,
      maxLength = Some(maxLength),
      editable = editable,
      submit = state.eventProcessor.enriched.changes,
      newTagParentIds = parentIds
    )


    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
          //          div(div(cls := "fa-fw", freeSolid.faCheck), onClick.stopPropagation(false) --> editable, cursor.pointer)
          VDomModifier.empty
        } else VDomModifier(
          div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, title := "Edit"),
          div(div(cls := "fa-fw", freeSolid.faExpand), onClick.stopPropagation(GraphChanges.connect(Edge.StaticParentIn)(node.id, parentIds)) --> state.eventProcessor.changes, cursor.pointer, title := "Expand to column"),
          div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation handleWith {
              state.eventProcessor.changes.onNext(GraphChanges.delete(node.id, state.graph.now))
              state.selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, title := "Delete"
          ),
          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig, cursor.pointer, title := "Zoom in"),
        )
      }
    )

    rendered(
      // sortable: draggable needs to be direct child of container
      editable.map(editable => if(editable) draggableAs(state, DragItem.DisableDrag) else draggableAs(state, DragItem.Kanban.Card(node.id))), // prevents dragging when selecting text
      dragTarget(DragItem.Kanban.Card(node.id)),
      keyed(node.id, parentIds),
      cls := "draghandle",

      position.relative,
      buttonBar(position.absolute, top := "0", right := "0"),
      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    )
  }

  private def addNodeField(state: GlobalState, parentId: NodeId, path:List[NodeId], activeReplyFields: Var[Set[List[NodeId]]])(implicit ctx: Ctx.Owner): VNode = {
    val fullPath = parentId :: path
    val active = Rx{activeReplyFields() contains fullPath}
    div(
      cls := "kanbanaddnodefield",
      keyed(parentId),
      Rx {
        if(active())
          div(
            cls := "ui form",
            keyed(parentId),
            textArea(
              keyed(parentId),
              cls := "field fluid",
              rows := 2,
              placeholder := "Press Enter to add.",
              valueWithEnter handleWith { str =>
                val graph = state.graphContent.now
                val selectedNodeIds = state.selectedNodeIds.now
                val change = GraphChanges.addNodeWithParent(Node.Content(NodeData.Markdown(str)), parentId)
                state.eventProcessor.enriched.changes.onNext(change)
              },
              onDomMount.asHtml --> inAnimationFrame(_.focus),
              onBlur.value handleWith { v => if(v.isEmpty) activeReplyFields.update(_ - fullPath) }
            )
          )
        else
          div(
            keyed(parentId),
            "+ Add Card",
            // not onClick, because if another reply-field is already open, the click first triggers the blur-event of
            // the active field. If the field was empty it disappears, and shifts the reply-field away from the cursor
            // before the click was finished. This does not happen with onMouseDown combined with deferred opening of the new reply field.
            onMouseDown handleWith { defer{ activeReplyFields.update(_ + fullPath) } }
          )
      }
    )
  }

  private def renderIsolatedNodes(state: GlobalState, page: Page, nodes: Seq[Node])(implicit ctx: Ctx.Owner) =
    div(
      keyed,
      if (nodes.isEmpty) VDomModifier.empty else "Unused Nodes",
      div(
        cls := "kanbanisolatednodes",
        keyed,
        registerSortableContainer(state, DragContainer.Kanban.IsolatedNodes(page.parentIds)),

        nodes.map { node => renderCard(state, node, page.parentIds) }
      )
    )

}
