package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object KanbanView {
  import SharedViewElements._

  private val maxLength = 100
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {


    val activeReplyFields = Var(Set.empty[List[NodeId]])
    val newColumnFieldActive = Var(false)
    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    div(
      cls := "kanbanview",
      Styles.flex,
      alignItems.flexStart,
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
            Styles.flexStatic,

            Styles.flex,
            alignItems.flexStart,
            overflow.auto,

            sortedForest.map(tree => renderTree(state, tree, parentIds = page.parentIds, path = Nil, activeReplyFields, selectedNodeIds, isTopLevel = true)),

            registerSortableContainer(state, DragContainer.Kanban.ColumnArea(state.page().parentIds)),
          )
        }
      },
      Rx{ newColumnArea(state, newColumnFieldActive).apply(Styles.flexStatic) },
    )
  }

  private def renderTree(state: GlobalState, tree: Tree, parentIds: Seq[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]], selectedNodeIds:Var[Set[NodeId]], isTopLevel: Boolean = false)(implicit ctx: Ctx.Owner): VDomModifier = {
    tree match {
      case Tree.Parent(node, children) =>
        Rx {
          if(state.graph().isExpanded(state.user.now.id, node.id)) {
            val sortedChildren = state.graph.now.topologicalSortBy[Tree](children, (t: Tree) => t.node.id)
            //        scribe.info(s"SORTING CHILDREN: $children => $sortedChildren")
            //        scribe.info(s"\tNodeCreated: ${state.graph.now.nodeCreated}")
            renderColumn(state, node, sortedChildren, parentIds, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)
          }
          else
            renderColumn(state, node, Nil, parentIds, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel, isCollapsed = true)
        }
      case Tree.Leaf(node)             =>
        Rx {
          if(state.graph().isExpanded(state.user.now.id, node.id))
            renderColumn(state, node, Nil, parentIds, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)
          else
            renderCard(state, node, parentIds, selectedNodeIds)
        }
    }
  }

  private def renderColumn(state: GlobalState, node: Node, children: Seq[Tree], parentIds: Seq[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]], selectedNodeIds:Var[Set[NodeId]], isTopLevel: Boolean = false, isCollapsed: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {

    val editable = Var(false)
    val columnTitle = editableNode(state, node, editMode = editable, submit = state.eventProcessor.enriched.changes, maxLength = Some(maxLength))(ctx)(cls := "kanbancolumntitle")

    val messageChildrenCount = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(node.id))
    }


    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
          VDomModifier.empty
        } else VDomModifier(
          div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, title := "Edit"),
          if(isCollapsed)
            div(div(cls := "fa-fw", freeRegular.faPlusSquare), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, title := "Expand")
          else
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

    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      if(isTopLevel) cls := "kanbantoplevelcolumn" else cls := "kanbansubcolumn",
      keyed(node.id, parentIds),
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
        isCollapsed.ifTrue[VDomModifier](cls := "kanbancolumncollapsed"),
        keyed(node.id, parentIds),
        cls := "draghandle",

        columnTitle,

        Rx{ (messageChildrenCount() > 0).ifTrue[VDomModifier](renderMessageCount(messageChildrenCount(), color := "rgba(255, 255, 255, 0.81)", marginBottom := "10px", onClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig, cursor.pointer)) },

        position.relative, // for buttonbar
        buttonBar(position.absolute, top := "0", right := "0"),
//        onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
      ),
      isCollapsed.ifFalse[VDomModifier](VDomModifier(
        div(
          cls := "kanbancolumnchildren",
          registerSortableContainer(state, DragContainer.Kanban.Column(node.id)),
          keyed(node.id, parentIds),
          children.map(t => renderTree(state, t, parentIds = node.id :: Nil, path = node.id :: path, activeReplyFields, selectedNodeIds)),
          scrollHandler.modifier,
        ),
        addNodeField(state, node.id, path, activeReplyFields, scrollHandler)
      ))
    )
  }

  private val renderMessageCount = {
    div(
      Styles.flexStatic,
      Styles.flex,
      color := "gray",
      margin := "5px",
      div(freeRegular.faComments, marginRight := "5px"),
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

    val messageChildrenCount = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(node.id))
    }

    rendered(
      Styles.flex,
      alignItems.flexEnd,
      justifyContent.spaceBetween,

      // sortable: draggable needs to be direct child of container
      editable.map(editable => if(editable) draggableAs(DragItem.DisableDrag) else draggableAs(DragItem.Kanban.Card(node.id))), // prevents dragging when selecting text
      dragTarget(DragItem.Kanban.Card(node.id)),
      keyed(node.id, parentIds),
      cls := "draghandle",

      Rx{ (messageChildrenCount() > 0).ifTrue[VDomModifier](renderMessageCount(messageChildrenCount(), onClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig, cursor.pointer)) },

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "0", right := "0"),
//      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    )
  }

  private def addNodeField(state: GlobalState, parentId: NodeId, path:List[NodeId], activeReplyFields: Var[Set[List[NodeId]]], scrollHandler: ScrollBottomHandler)(implicit ctx: Ctx.Owner): VNode = {
    val fullPath = parentId :: path
    val active = Rx{activeReplyFields() contains fullPath}
    active.foreach{ active =>
      if(active) scrollHandler.scrollToBottomInAnimationFrame()
    }

    def submitAction(str:String) = {
      val change = GraphChanges.addNodeWithParent(Node.MarkdownTask(str), parentId)
      state.eventProcessor.enriched.changes.onNext(change)
    }

    def blurAction(v:String) = {
      if(v.isEmpty) activeReplyFields.update(_ - fullPath)
    }

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add."

    div(
      cls := "kanbanaddnodefield",
      keyed(parentId),
      Rx {
        if(active())
          inputField(state, submitAction, autoFocus = true, blurAction = Some(blurAction), placeHolderMessage = Some(placeHolder))
        else
          div(
            "+ Add Card",
            onClick foreach { activeReplyFields.update(_ + fullPath) }
          )
      }
    )
  }

  private def newColumnArea(state: GlobalState, fieldActive: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    def submitAction(str:String) = {
      val change = {
        val newColumnNode = Node.MarkdownTask(str)
        val add = GraphChanges.addNode(newColumnNode)
        val expand = GraphChanges.connect(Edge.Expanded)(state.user.now.id, newColumnNode.id)
        //                  val addOrder = GraphChanges.connect(Edge.Before)(newColumnNode.id, DataOrdering.getLastInOrder(state.graph.now, state.graph.now.graph. page.parentIds))
        add merge expand
      }
      state.eventProcessor.enriched.changes.onNext(change)
      //TODO: sometimes after adding new column, the add-column-form is scrolled out of view. Scroll, so that it is visible again
    }

    def blurAction(v:String) = {
      if(v.isEmpty) fieldActive() = false
    }

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add."

    val marginRightHack = VDomModifier(
      position.relative,
      div(position.absolute, left := "100%", width := "10px", height := "1px") // https://www.brunildo.org/test/overscrollback.html
    )

    div(
      cls := s"kanbannewcolumnarea",
      keyed,
      onClick.stopPropagation(true) --> fieldActive,
      Rx {
        if(fieldActive()) {
          inputField(state, submitAction, autoFocus = true, blurAction = Some(blurAction), placeHolderMessage = Some(placeHolder), textAreaModifiers = VDomModifier(
            fontSize.larger,
            fontWeight.bold,
            minHeight := "50px",
          )).apply(
            cls := "kanbannewcolumnareaform",
          )
        }
        else
          div(
            cls := "kanbannewcolumnareacontent",
            margin.auto,
            "+ Add Column",
          )
      },
      marginRightHack
    )
  }

}
