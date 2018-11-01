package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeRole, NodeId}
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
    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    div(
      minWidth := "0",
      minHeight := "0",
      Rx {
        withLoadingAnimation(state) {
          val page = state.page()
          val graph = {
            val g = state.graph()
            val transitivePageChildren = page.parentIds.flatMap(g.notDeletedDescendants)
            g.filterIds(page.parentIdSet ++ transitivePageChildren.toSet ++ transitivePageChildren.flatMap(id => g.authors(id).map(_.id)))
          }

          val unsortedForest = graph.filterIdx { nodeIdx =>
            val node = graph.nodes(nodeIdx)

            @inline def isContent = node.isInstanceOf[Node.Content]

            @inline def isTask = node.role.isInstanceOf[NodeRole.Task.type]

            @inline def noPage = !page.parentIdSet.contains(node.id)

            isContent && isTask && noPage
          }.redundantForestIncludingCycleLeafs

          //        scribe.info(s"SORTING FOREST: $unsortedForest")
          val sortedForest = graph.topologicalSortBy[Tree](unsortedForest, (t: Tree) => t.node.id)
          //        scribe.info(s"SORTED FOREST: $sortedForest")

          //        scribe.info(s"\tNodeCreatedIdx: ${graph.nodeCreated.indices.mkString(",")}")
          //        scribe.info(s"\tNodeCreated: ${graph.nodeCreated.mkString(",")}")
          //
          //        scribe.info(s"chronological nodes idx: ${graph.chronologicalNodesAscendingIdx.mkString(",")}")
          //        scribe.info(s"chronological nodes: ${graph.chronologicalNodesAscending}")

          div(
            cls := s"kanbancolumnarea",
            keyed,
            Styles.flex,
            alignItems.flexStart,
            overflow.auto,

            sortedForest.map(tree => renderTree(state, tree, parentIds = page.parentIds, path = Nil, activeReplyFields, selectedNodeIds, isTopLevel = true, inject = cls := "kanbantoplevelcolumn")),
            newColumnArea(state, page, newColumnFieldActive),

            registerSortableContainer(state, DragContainer.Kanban.ColumnArea(state.page().parentIds)),
          )
        }
      }
    )
  }

  private def renderTree(state: GlobalState, tree: Tree, parentIds: Seq[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]], selectedNodeIds:Var[Set[NodeId]], isTopLevel: Boolean = false, inject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VDomModifier = {
    tree match {
      case Tree.Parent(node, children) =>
        Rx {
          if(state.graph().isExpanded(state.user.now.id, node.id)) {
            val sortedChildren = state.graph.now.topologicalSortBy[Tree](children, (t: Tree) => t.node.id)
            //        scribe.info(s"SORTING CHILDREN: $children => $sortedChildren")
            //        scribe.info(s"\tNodeCreated: ${state.graph.now.nodeCreated}")
            renderColumn(state, node, sortedChildren, parentIds, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)(ctx)(inject)
          }
          else
            renderCard(state, node, parentIds, selectedNodeIds)(ctx)(VDomModifier(
              inject,
              backgroundColor := BaseColors.kanbanColumnBg.copy(h = hue(node.id)).toHex),
              cls := "kanbancolumncollapsed",
            )
        }
      case Tree.Leaf(node)             =>
        Rx {
          if(state.graph().isExpanded(state.user.now.id, node.id))
            renderColumn(state, node, Nil, parentIds, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)(ctx)(inject)
          else
            renderCard(state, node, parentIds, selectedNodeIds)(ctx)(inject)
        }
    }
  }

  private def renderColumn(state: GlobalState, node: Node, children: Seq[Tree], parentIds: Seq[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]], selectedNodeIds:Var[Set[NodeId]], isTopLevel: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {

    val editable = Var(false)
    val columnTitle = editableNode(state, node, editMode = editable, submit = state.eventProcessor.enriched.changes, maxLength = Some(maxLength))(ctx)(cls := "kanbancolumntitle")

    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
          VDomModifier.empty
        } else VDomModifier(
          div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, title := "Edit"),
          div(div(cls := "fa-fw", freeRegular.faMinusSquare), onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, title := "Collapse"),
          div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(GraphChanges.delete(node.id, parentIds))
              selectedNodeIds.update(_ - node.id)
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
        draggableAs(DragItem.Kanban.ToplevelColumn(node.id)),
        dragTarget(DragItem.Kanban.ToplevelColumn(node.id)),
      ) else VDomModifier(
        draggableAs(DragItem.Kanban.SubColumn(node.id)),
        dragTarget(DragItem.Kanban.SubColumn(node.id))
      ),
      div(
        cls := "kanbancolumnheader",
        keyed(node.id, parentIds),
        cls := "draghandle",
        columnTitle,

        position.relative,
        buttonBar(position.absolute, top := "0", right := "0"),
//        onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
      ),
      div(
        cls := "kanbancolumnchildren",
        registerSortableContainer(state, DragContainer.Kanban.Column(node.id)),
        keyed(node.id, parentIds),
        children.map(t => renderTree(state, t, parentIds = node.id :: Nil, path = node.id :: path, activeReplyFields, selectedNodeIds)),
      ),
      addNodeField(state, node.id, path, activeReplyFields) // does not belong to sortable container => always stays at the bottom. TODO: is this a draggable bug? If last element is not draggable, it can still be pushed away by a movable element
    )
  }

  private def renderCard(state: GlobalState, node: Node, parentIds: Seq[NodeId], selectedNodeIds:Var[Set[NodeId]])(implicit ctx: Ctx.Owner): VNode = {
    val editable = Var(false)
    val rendered = nodeCardEditable(
      state, node,
      maxLength = Some(maxLength),
      editMode = editable,
      submit = state.eventProcessor.changes
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
          div(div(cls := "fa-fw", freeRegular.faPlusSquare), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, title := "Expand"),
          div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(GraphChanges.delete(node.id, parentIds))
              selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, title := "Delete"
          ),
          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig, cursor.pointer, title := "Zoom in"),
        )
      }
    )

    rendered(
      // sortable: draggable needs to be direct child of container
      editable.map(editable => if(editable) draggableAs(DragItem.DisableDrag) else draggableAs(DragItem.Kanban.Card(node.id))), // prevents dragging when selecting text
      dragTarget(DragItem.Kanban.Card(node.id)),
      keyed(node.id, parentIds),
      cls := "draghandle",

      position.relative,
      buttonBar(position.absolute, top := "0", right := "0"),
//      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
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
              valueWithEnter foreach { str =>
                val change = GraphChanges.addNodeWithParent(Node.MarkdownTask(str), parentId)
                state.eventProcessor.enriched.changes.onNext(change)
              },
              onDomMount.asHtml --> inNextAnimationFrame(_.focus),
              onBlur.value foreach { v => if(v.isEmpty) activeReplyFields.update(_ - fullPath) }
            )
          )
        else
          div(
            keyed(parentId),
            "+ Add Card",
            // not onClick, because if another reply-field is already open, the click first triggers the blur-event of
            // the active field. If the field was empty it disappears, and shifts the reply-field away from the cursor
            // before the click was finished. This does not happen with onMouseDown combined with deferred opening of the new reply field.
            onMouseDown foreach { defer{ activeReplyFields.update(_ + fullPath) } }
          )
      }
    )
  }

  private def newColumnArea(state: GlobalState, page: Page, fieldActive: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    val marginRightHack = div(position.absolute, left := "100%", width := "10px", height := "1px") // https://www.brunildo.org/test/overscrollback.html
    div(
      cls := s"kanbannewcolumnarea",
      keyed,
      registerSortableContainer(state, DragContainer.Kanban.NewColumnArea(page.parentIds)),
      position.relative,
      onClick.stopPropagation(true) --> fieldActive,
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
              valueWithEnter foreach { str =>
                val change = {
                  val newColumnNode = Node.MarkdownTask(str)
                  val add = GraphChanges.addNode(newColumnNode)
                  val expand = GraphChanges.connect(Edge.Expanded)(state.user.now.id, newColumnNode.id)
                  //                  val addOrder = GraphChanges.connect(Edge.Before)(newColumnNode.id, DataOrdering.getLastInOrder(state.graph.now, state.graph.now.graph. page.parentIds))
                  add merge expand
                }
                state.eventProcessor.enriched.changes.onNext(change)
              },
              onDomMount.asHtml --> inNextAnimationFrame(_.focus()),
              onBlur.value foreach { v => if(v.isEmpty) fieldActive() = false }
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
            "+ Add Column",
          )
      },
      marginRightHack
    )
  }

}
