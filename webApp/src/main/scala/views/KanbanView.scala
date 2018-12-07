package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole, UserId}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, NodePermission, PageChange, View}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object KanbanView {
  import SharedViewElements._

//  def filterKanbanGraph(g: Graph, parentId: NodeId): Graph = {
//    val transitivePageChildren = g.notDeletedDescendants(parentId)
//    g.filterIds(transitivePageChildren.toSet + parentId)
//  }

  private val maxLength = 100
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {


    val activeReplyFields = Var(Set.empty[List[NodeId]])
    val newColumnFieldActive = Var(false)
    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    div(
      cls := "kanbanview",

      overflow.auto,

      Styles.flex,
      alignItems.flexStart,

      Rx {
        val page = state.page()
        val graph = state.graph()
        page.parentId.map { pageParentId =>
          val pageParentIdx = graph.idToIdx(pageParentId)

          //            val kanbanGraph = filterKanbanGraph(state.graph(), pageParentId)
          //          scribe.info(s"KANBAN GRAPH NODES: ${graph.nodes.map(_.str).mkString(", ")}")

          // inboxTasks: all tasks which are direct children of pageParentId
          // column: trees of all stages which are direct children of pageParentId.
//          val (inboxTasks, columns) = BeforeOrdering.taskGraphToSortedForest(kanbanGraph, state.user.now.id, pageParentId)
          val inboxTasks = graph.childrenIdx(pageParentIdx).filter(idx => graph.nodes(idx).role == NodeRole.Task)
          val topLevelStages = graph.childrenIdx(pageParentIdx).filter(idx => graph.nodes(idx).role == NodeRole.Stage)
          val topLevelColumns:Seq[Tree] = topLevelStages.map(stageIdx => graph.roleTree(stageIdx, NodeRole.Stage))

            VDomModifier(
              renderInboxColumn(state, pageParentId, inboxTasks.map(graph.nodeIds), activeReplyFields, selectedNodeIds),
              div(
                cls := s"kanbancolumnarea",
                keyed,
                Styles.flexStatic,

                Styles.flex,
                alignItems.flexStart,
                topLevelColumns.map(tree => renderStageTree(state, graph, tree, parentId = pageParentId, path = Nil, activeReplyFields, selectedNodeIds, isTopLevel = true)),

                registerSortableContainer(state, DragContainer.Kanban.ColumnArea(pageParentId)),
              ),
              newColumnArea(state, pageParentId, newColumnFieldActive).apply(Styles.flexStatic)
            )
        }
      },
    )
  }

  private def renderStageTree(
    state: GlobalState,
    graph: Graph,
    tree: Tree,
    parentId: NodeId,
    path: List[NodeId],
    activeReplyFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    tree match {
      case Tree.Parent(node, children) if node.role == NodeRole.Stage =>
        Rx {
          if(state.graph().isExpanded(state.user.now.id, node.id)) {
//            val (sortedChildren, _) = BeforeOrdering.sort[Tree](filterKanbanGraph(state.graph.now, node.id), node.id, children, (t: Tree) => t.node.id)
//            scribe.debug(s"Sorting Tree of ${node.str}")
            val (sortedChildren, _) = BeforeOrdering.sort[Tree](graph, node.id, children, (t: Tree) => t.node.id)
            // val (sortedChildren, _) = BeforeOrdering.sort[Tree](state.graph.now, node.id, children, (t: Tree) => t.node.id)
            renderColumn(state, graph, node, sortedChildren, parentId, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)
          }
          else
            renderColumn(state, graph, node, Nil, parentId, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel, isCollapsed = true)
        }
      case Tree.Leaf(node)             =>
        if(node.role == NodeRole.Stage)
          renderColumn(state, graph, node, Nil, parentId, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)
        else
          renderCard(state, node, parentId, selectedNodeIds)
    }
  }


  private def renderInboxColumn(
    state: GlobalState,
    parentId:NodeId,
    children: Seq[NodeId],
    activeReplyFields: Var[Set[List[NodeId]]],
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VNode = {
    val columnColor = BaseColors.kanbanColumnBg.copy(h = hue(parentId)).toHex
    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      cls := "kanbantoplevelcolumn",
      keyed,
      border := s"1px dashed $columnColor",
      p(cls := "kanban-uncategorized-title", "Inbox"),
      div(
        cls := "kanbancolumnchildren",
        registerSortableContainer(state, DragContainer.Kanban.Inbox(parentId)),
        children.map(nodeId => renderCard(state, state.graph.now.nodesById(nodeId), parentId = parentId, selectedNodeIds)),
        scrollHandler.modifier,
      ),
      addNodeField(state, parentId, path = Nil, activeReplyFields, scrollHandler, textColor = Some("rgba(0,0,0,0.62)"))
    )
  }

  private def renderColumn(
    state: GlobalState,
    graph: Graph,
    node: Node,
    children: Seq[Tree],
    parentId: NodeId,
    path: List[NodeId],
    activeReplyFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
    isCollapsed: Boolean = false
  )(implicit ctx: Ctx.Owner): VNode = {

    val editable = Var(false)
    val columnTitle = editableNode(state, node, editMode = editable, submit = state.eventProcessor.changes, maxLength = Some(maxLength))(ctx)(cls := "kanbancolumntitle")

    val messageChildrenCount = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(node.id))
    }

    val canWrite = NodePermission.canWrite(state, node.id)

    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        def ifCanWrite(mod: => VDomModifier): VDomModifier = if (canWrite()) mod else VDomModifier.empty

        if(editable()) {
          VDomModifier.empty
        } else VDomModifier(
          ifCanWrite(div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, UI.popup := "Edit")),
          if(isCollapsed)
            div(div(cls := "fa-fw", freeRegular.faPlusSquare), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Expand")
          else
            div(div(cls := "fa-fw", freeRegular.faMinusSquare), onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Collapse"),
          ifCanWrite(div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(GraphChanges.delete(node.id, parentId))
              selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, UI.popup := "Delete"
          )),
          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(node.id)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
        )
      }
    )

    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      if(isTopLevel) cls := "kanbantoplevelcolumn" else cls := "kanbansubcolumn",
      keyed(node.id, parentId),
      backgroundColor := BaseColors.kanbanColumnBg.copy(h = hue(node.id)).toHex,
      Rx{ if(editable()) sortableAs(DragItem.DisableDrag) else { // prevents dragging when selecting text
        if(isTopLevel) VDomModifier(
          sortableAs(DragItem.Kanban.Column(node.id)),
          dragTarget(DragItem.Kanban.Column(node.id)),
        ) else VDomModifier(
          sortableAs(DragItem.Kanban.Column(node.id)),
          dragTarget(DragItem.Kanban.Column(node.id))
        )
      }},
      div(
        cls := "kanbancolumnheader",
        isCollapsed.ifTrue[VDomModifier](cls := "kanbancolumncollapsed"),
        keyed(node.id, parentId),
        cls := "draghandle",
        cls := "childstats",

        columnTitle,

        Rx{
          renderMessageCount(
            if (messageChildrenCount() > 0) VDomModifier(messageChildrenCount())
            else VDomModifier(cls := "emptystat"),
            onClick.stopPropagation.mapTo(state.viewConfig.now.copy(pageChange = PageChange(Page(node.id)), view = View.Conversation)) --> state.viewConfig,
            cursor.pointer
          )
        },

        position.relative, // for buttonbar
        buttonBar(position.absolute, top := "0", right := "0"),
//        onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
      ),
      isCollapsed.ifFalse[VDomModifier](VDomModifier(
        div(
          cls := "kanbancolumnchildren",
          registerSortableContainer(state, DragContainer.Kanban.Column(node.id)),
          keyed(node.id, parentId),
          children.map(tree => renderStageTree(state, graph, tree, parentId = node.id, path = node.id :: path, activeReplyFields, selectedNodeIds)),
          scrollHandler.modifier,
        ),
        addNodeField(state, node.id, path, activeReplyFields, scrollHandler)
      ))
    )
  }

  private val renderMessageCount = {
    div(
      cls := "childstat",
      Styles.flexStatic,
      Styles.flex,
      margin := "5px",
      div(Icons.conversation, marginRight := "5px"),
    )
  }

  private val renderTaskCount = {
    div(
      cls := "childstat",
      Styles.flexStatic,
      Styles.flex,
      margin := "5px",
      div(Icons.tasks, marginRight := "5px"),
    )
  }


  private def renderCard(state: GlobalState, node: Node, parentId: NodeId, selectedNodeIds:Var[Set[NodeId]])(implicit ctx: Ctx.Owner): VNode = {
    val editable = Var(false)
    val rendered = nodeCardEditable(
      state, node,
      maxLength = Some(maxLength),
      editMode = editable,
      submit = state.eventProcessor.changes
    )

    val assignment = Rx {
      val graph = state.graph()
      val nodeUsers = graph.assignedUsersIdx(graph.idToIdx(node.id))
      nodeUsers.map(userIdx => graph.nodes(userIdx).asInstanceOf[Node.User])
    }


    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
          //          div(div(cls := "fa-fw", freeSolid.faCheck), onClick.stopPropagation(false) --> editable, cursor.pointer)
          VDomModifier.empty
        } else VDomModifier(
          div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, UI.popup := "Edit"),
          div(div(cls := "fa-fw", freeRegular.faPlusSquare), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Expand"),
          Rx {
            val userid = state.user().id
            if(assignment().exists(_.id == userid)) {
              div(div(cls := "fa-fw", freeSolid.faUserTimes), onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(userid, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Remove Yourself")
            } else {
              div(div(cls := "fa-fw", freeSolid.faUserCheck), onClick.stopPropagation(GraphChanges.connect(Edge.Assigned)(userid, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Assign Yourself")
            }
          },
          div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(GraphChanges.delete(node.id, parentId))
              selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, UI.popup := "Delete"
          ),
          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(node.id)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
        )
      }
    )

    val messageChildrenCount = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(node.id))
    }

    val taskChildrenCount = Rx {
      val graph = state.graph()
      graph.taskChildrenIdx.sliceLength(graph.idToIdx(node.id))
    }

    rendered(
      // sortable: draggable needs to be direct child of container
      Rx { if(editable()) sortableAs(DragItem.DisableDrag) else sortableAs(DragItem.Kanban.Card(node.id)) }, // prevents dragging when selecting text
      dragTarget(DragItem.Kanban.Card(node.id)),
//      registerDraggableContainer(state),
      keyed(node.id, parentId),
      cls := "draghandle",

      div(
        Styles.flex,
        justifyContent.spaceBetween,
        alignItems.flexEnd,

        div(
          Styles.flex,
          flexWrap.wrap,
          assignment.map(_.map(userNode => div(
            Styles.flexStatic,
            Avatar.user(userNode.id)(
              marginLeft := "2px",
              width := "22px",
              height := "22px",
              cls := "avatar",
              marginBottom := "2px",
            ),
            keyed(userNode.id),
            UI.popup := s"Assigned to ${displayUserName(userNode.data)}. Click to remove.",
            cursor.pointer,
            onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(userNode.id, node.id)) --> state.eventProcessor.changes,
          ))),
        ),

        div(
          cls := "childstats",
          Styles.flex,
          Styles.flexStatic,
          Rx{
            VDomModifier(
              renderTaskCount(
                if (taskChildrenCount() > 0) VDomModifier(taskChildrenCount())
                else VDomModifier(cls := "emptystat"),
                onClick.stopPropagation.mapTo(state.viewConfig.now.copy(pageChange = PageChange(Page(node.id)), view = View.Kanban)) --> state.viewConfig,
                cursor.pointer
              ),
              renderMessageCount(
                if (messageChildrenCount() > 0) VDomModifier(messageChildrenCount())
                else VDomModifier(cls := "emptystat"),
                onClick.stopPropagation.mapTo(state.viewConfig.now.copy(pageChange = PageChange(Page(node.id)), view = View.Conversation)) --> state.viewConfig,
                cursor.pointer
              ),
            )
          },
        ),
      ),

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "0", right := "0"),
//      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    )
  }

  private def addNodeField(
    state: GlobalState,
    parentId: NodeId,
    path:List[NodeId],
    activeReplyFields: Var[Set[List[NodeId]]],
    scrollHandler: ScrollBottomHandler,
    textColor:Option[String] = None,
  )(implicit ctx: Ctx.Owner): VNode = {
    val fullPath = parentId :: path
    val active = Rx{activeReplyFields() contains fullPath}
    active.foreach{ active =>
      if(active) scrollHandler.scrollToBottomInAnimationFrame()
    }

    def submitAction(str:String) = {
      val change = GraphChanges.addNodeWithParent(Node.MarkdownTask(str), parentId)
      state.eventProcessor.changes.onNext(change)
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
          inputRow(state, submitAction, autoFocus = true, blurAction = Some(blurAction), placeHolderMessage = Some(placeHolder))
        else
          div(
            cls := "kanbanaddnodefieldtext",
            "+ Add Card",
            color :=? textColor,
            onClick foreach { activeReplyFields.update(_ + fullPath) }
          )
      }
    )
  }

  private def newColumnArea(state: GlobalState, pageParentId:NodeId, fieldActive: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    def submitAction(str:String) = {
      val change = {
        val newColumnNode = Node.MarkdownStage(str)
        val add = GraphChanges.addNodeWithParent(newColumnNode, pageParentId)
        val expand = GraphChanges.connect(Edge.Expanded)(state.user.now.id, newColumnNode.id)
        add merge expand
      }
      state.eventProcessor.changes.onNext(change)
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
          inputRow(state, submitAction, autoFocus = true, blurAction = Some(blurAction), placeHolderMessage = Some(placeHolder), textAreaModifiers = VDomModifier(
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
