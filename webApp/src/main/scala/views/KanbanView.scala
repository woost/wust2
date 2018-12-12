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

  private val maxLength = 100
  def apply(state: GlobalState, filterAssigned: Boolean)(implicit ctx: Ctx.Owner): VNode = {


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

          val topLevelStages = graph.notDeletedChildrenIdx(pageParentIdx).filter(idx => graph.nodes(idx).role == NodeRole.Stage)
          val allStages:ArraySet = {
            val stages = ArraySet.create(graph.size)
            topLevelStages.foreachElement(stages.add)
            algorithm.depthFirstSearchAfterStartsWithContinue(starts = topLevelStages.toArray, graph.notDeletedChildrenIdx, {idx =>
              val isStage = graph.nodes(idx).role == NodeRole.Stage
              if(isStage) stages += idx
              isStage
            })
            stages
          }

          val inboxTasks: ArraySet  = {
            val inboxTasks = ArraySet.create(graph.size)
            graph.notDeletedChildrenIdx.foreachElement(pageParentIdx){childIdx =>
              if(graph.nodes(childIdx).role == NodeRole.Task) {
                @inline def hasStageParentInPage = graph.notDeletedParentsIdx(childIdx).exists(allStages.contains)
                if(!hasStageParentInPage) inboxTasks += childIdx
              }
            }
            inboxTasks
          }

          val topLevelColumns: Seq[Tree] = topLevelStages.map { stageIdx =>
            if(!filterAssigned)
              graph.roleTree(stageIdx, NodeRole.Stage, pageParentIdx)
            else
              graph.roleTreeWithUserFilter(graph.assignedNodesIdx(graph.idToIdx(state.user().id)), stageIdx, NodeRole.Stage, pageParentIdx)
          }

          val sortedTopLevelColumns:Seq[Tree] = TaskOrdering.constructOrderingOf[Tree](graph, pageParentId, topLevelColumns, (t: Tree) => t.node.id)
          val assigneInbox = if(!filterAssigned) inboxTasks.map(graph.nodeIds) else inboxTasks.mapToArray(identity).filter(idx => graph.assignedNodesIdx.contains(graph.idToIdx(state.user().id))(idx)).map(graph.nodeIds)

            VDomModifier(
              renderInboxColumn(state, pageParentId, pageParentId, assigneInbox, activeReplyFields, selectedNodeIds),
              div(
                cls := s"kanbancolumnarea",
                keyed,
                Styles.flexStatic,

                Styles.flex,
                alignItems.flexStart,
                sortedTopLevelColumns.map(tree => renderStageTree(state, graph, tree, parentId = pageParentId, pageParentId = pageParentId, path = Nil, activeReplyFields, selectedNodeIds, isTopLevel = true)),

                registerSortableContainer(state, DragContainer.Kanban.ColumnArea(pageParentId, sortedTopLevelColumns.map(_.node.id))),
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
    pageParentId: NodeId,
    path: List[NodeId],
    activeReplyFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val pageParentIdx = graph.idToIdx(pageParentId)
    tree match {
      case Tree.Parent(node, children) if node.role == NodeRole.Stage =>
        Rx {
          if(state.graph().isExpanded(state.user.now.id, node.id)) {
            val sortedChildren = TaskOrdering.constructOrderingOf[Tree](graph, node.id, children, (t: Tree) => t.node.id)
            renderColumn(state, graph, node, sortedChildren, parentId, pageParentId, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)
          }
          else
            renderColumn(state, graph, node, Nil, parentId, pageParentId, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel, isCollapsed = true)
        }
      case Tree.Leaf(node) if node.role == NodeRole.Stage =>
          renderColumn(state, graph, node, Nil, parentId, pageParentId, path, activeReplyFields, selectedNodeIds, isTopLevel = isTopLevel)
      case Tree.Leaf(node) if node.role == NodeRole.Task =>
          renderCard(state, node, parentId, pageParentId, selectedNodeIds)
      case _ => VDomModifier.empty // if card is not also direct child of page, it is probably a mistake
    }
  }


  private def renderInboxColumn(
    state: GlobalState,
    parentId:NodeId,
    pageParentId:NodeId,
    children: Seq[NodeId],
    activeReplyFields: Var[Set[List[NodeId]]],
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VNode = {
    val columnColor = BaseColors.kanbanColumnBg.copy(h = hue(parentId)).toHex
    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)
    val sortedChildren = TaskOrdering.constructOrderingOf[NodeId](state.graph.now, parentId, children, identity)

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      cls := "kanbantoplevelcolumn",
      keyed,
      border := s"1px dashed $columnColor",
      p(cls := "kanban-uncategorized-title", "Inbox"),
      div(
        cls := "kanbancolumnchildren",
        registerSortableContainer(state, DragContainer.Kanban.Inbox(parentId, sortedChildren)),
        sortedChildren.map(nodeId => renderCard(state, state.graph.now.nodesById(nodeId), parentId = parentId, pageParentId = pageParentId, selectedNodeIds)),
        scrollHandler.modifier,
      ),
      addNodeField(state, parentId, pageParentId, path = Nil, activeReplyFields, scrollHandler, textColor = Some("rgba(0,0,0,0.62)"))
    )
  }

  private def renderColumn(
    state: GlobalState,
    graph: Graph,
    node: Node,
    children: Seq[Tree],
    parentId: NodeId,
    pageParentId: NodeId,
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
          if(isCollapsed)
            div(div(cls := "fa-fw", freeRegular.faPlusSquare), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Expand")
          else
            div(div(cls := "fa-fw", freeRegular.faMinusSquare), onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Collapse"),
          ifCanWrite(div(div(cls := "fa-fw", freeSolid.faPen), onClick.stopPropagation(true) --> editable, cursor.pointer, UI.popup := "Edit")),
          ifCanWrite(div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(GraphChanges.delete(node.id, parentId))
              selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, UI.popup := "Delete"
          )),
//          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(node.id)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
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
      Rx{
        if(editable()) dragDisabled
        else { // prevents dragging when selecting text
          VDomModifier(
            sortableAs(DragItem.Kanban.Column(node.id)),
            dragTarget(DragItem.Kanban.Column(node.id)),
          )
      }},
      div(
        cls := "kanbancolumnheader",
        keyed(node.id, parentId),
        cls := "draghandle",

        columnTitle,

        position.relative, // for buttonbar
        buttonBar(position.absolute, top := "0", right := "0"),
//        onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
      ),
      if(isCollapsed) VDomModifier(
        div(
          cls := "kanbancolumncollapsed",
          Styles.flex,
          flexDirection.column,
          alignItems.stretch,

          padding := "7px",

          div(
            fontSize.xLarge,
            opacity := 0.5,
            Styles.flex,
            justifyContent.center,
            div(cls := "fa-fw", freeRegular.faPlusSquare, UI.popup := "Expand"),
            onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes,
            cursor.pointer,
            paddingBottom := "7px",
          ),
          registerSortableContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id))), // allows to drop cards on collapsed columns
        )
      ) else VDomModifier(
        div(
          cls := "kanbancolumnchildren",
          registerSortableContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id))),
          keyed(node.id, parentId),
          children.map(tree => renderStageTree(state, graph, tree, parentId = node.id, pageParentId = pageParentId, path = node.id :: path, activeReplyFields, selectedNodeIds)),
          scrollHandler.modifier,
        ),
      ),
      div(
        cls := "kanbancolumnfooter",
        Styles.flex,
        justifyContent.spaceBetween,
        addNodeField(state, node.id, pageParentId, path, activeReplyFields, scrollHandler).apply(width := "100%"),
        Rx{
          // hide comment zoom, when addNodeField is active
          val fullPath = node.id :: path
          val active = activeReplyFields() contains fullPath
          active.ifFalse[VDomModifier](
            div(
              cls := "childstats",
              renderMessageCount(
                if (messageChildrenCount() > 0) VDomModifier(messageChildrenCount())
                else VDomModifier(cls := "emptystat"),
                onClick.stopPropagation.mapTo(state.viewConfig.now.copy(pageChange = PageChange(Page(node.id)), view = View.Conversation)) --> state.viewConfig,
                cursor.pointer
              )
            )
          )
        },
      )
    )
  }

  private val renderMessageCount = {
    div(
      cls := "childstat",
      Styles.flex,
      Styles.flexStatic,
      margin := "5px 5px 5px auto",
      div(Icons.conversation, marginRight := "5px"),
    )
  }

  private val renderTaskCount = {
    div(
      cls := "childstat",
      Styles.flex,
      Styles.flexStatic,
      margin := "5px",
      div(Icons.tasks, marginRight := "5px"),
    )
  }

  private def renderCard(
    state: GlobalState,
    node: Node,
    parentId: NodeId, // is either a column (stage) or else, if the card is in inbox equal to pageParentId
    pageParentId: NodeId,
    selectedNodeIds:Var[Set[NodeId]]
  )(implicit ctx: Ctx.Owner): VNode = {
    val editable = Var(false)

    val rendered = nodeCardEditable(
      state, node,
      maxLength = Some(maxLength),
      editMode = editable,
      submit = state.eventProcessor.changes,
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
//          div(div(cls := "fa-fw", freeRegular.faPlusSquare), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Expand"),
//          Rx {
//            val userid = state.user().id
//            if(assignment().exists(_.id == userid)) {
//              div(div(cls := "fa-fw", freeSolid.faUserTimes), onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(userid, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Remove Yourself")
//            } else {
//              div(div(cls := "fa-fw", freeSolid.faUserCheck), onClick.stopPropagation(GraphChanges.connect(Edge.Assigned)(userid, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Assign Yourself")
//            }
//          },
          div(
            div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              val changes = GraphChanges.delete(node.id, parentId) merge GraphChanges.delete(node.id, pageParentId)
              state.eventProcessor.changes.onNext(changes)
              selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, UI.popup := "Delete"
          ),
//          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(node.id)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
        )
      }
    )

    case class TaskStats(messageChildrenCount: Int, taskChildrenCount: Int, taskDoneCount: Int)
    val taskStats = Rx {
      val graph = state.graph()
      val nodeIdx = graph.idToIdx(node.id)

      val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)

      val taskChildren = graph.taskChildrenIdx(nodeIdx)
      val taskChildrenCount = taskChildren.length

      val taskDoneCount = taskChildren.fold(0) { (count, childIdx) =>
        val workspaces = graph.workspacesForNode(childIdx)
        if (graph.isDoneInAllWorkspaces(childIdx, workspaces)) count + 1
        else count
      }

      TaskStats(messageChildrenCount, taskChildrenCount, taskDoneCount)
    }



    val renderTaskProgress = Rx {
      if (taskStats().taskChildrenCount > 0) {
        val progress = (100 * taskStats().taskDoneCount) / taskStats().taskChildrenCount

        VDomModifier(
          div(
            cls := "childstat",
            Styles.flex,
            flexGrow := 1,
            backgroundColor := "#eee",
            borderRadius := "2px",
            margin := "3px 10px",
            alignItems.flexEnd,
            div(
              height := "3px",
              padding := "0",
              width := s"${math.max(progress, 1)}%",
              backgroundColor := s"${if(progress < 100) "#ccc" else "#32CD32"}",
              UI.popup := s"$progress% Progress. ${taskStats().taskDoneCount} / ${taskStats().taskChildrenCount} done."
            )
          )
        )
      } else VDomModifier(cls := "emptystat")
    }

    rendered(
      // sortable: draggable needs to be direct child of container
      Rx { if(editable()) dragDisabled else sortableAs(DragItem.Kanban.Card(node.id)) }, // prevents dragging when selecting text
      dragTarget(DragItem.Kanban.Card(node.id)),
//      registerDraggableContainer(state),
      keyed(node.id, parentId),
      cls := "draghandle",
      overflow.hidden, // fixes unecessary scrollbar, when card has assignment

      div(
        Styles.flex,
        justifyContent.flexEnd,
        alignItems.flexEnd,
        width := "100%",

        div(
          cls := "childstats",
          Styles.flex,
          flexDirection.row,
          alignItems.center,
          width := "100%",
          Rx{
            VDomModifier(
              renderTaskCount(
                if (taskStats().taskChildrenCount > 0) VDomModifier(s"${taskStats().taskDoneCount}/${taskStats().taskChildrenCount}")
                else VDomModifier(cls := "emptystat"),
                onClick.stopPropagation.mapTo(state.viewConfig.now.copy(pageChange = PageChange(Page(node.id)), view = View.Tasks)) --> state.viewConfig,
                cursor.pointer,
                UI.popup := "Zoom to show subtasks",
              ),
              renderTaskProgress(),
              renderMessageCount(
                if (taskStats().messageChildrenCount > 0) VDomModifier(taskStats().messageChildrenCount)
                else VDomModifier(cls := "emptystat"),
                onClick.stopPropagation.mapTo(state.viewConfig.now.copy(pageChange = PageChange(Page(node.id)), view = View.Conversation)) --> state.viewConfig,
                cursor.pointer,
                UI.popup := "Zoom to show comments",
              ),
            )
          },
        ),
        div(
          Styles.flex,
          flexWrap.wrap,
          assignment.map(_.map(userNode => div(
            Styles.flexStatic,
            Avatar.user(userNode.id)(
              marginRight := "2px",
              width := "22px",
              height := "22px",
              cls := "avatar",
            ),
            keyed(userNode.id),
            UI.popup := s"Assigned to ${displayUserName(userNode.data)}. Click to remove.",
            cursor.pointer,
            onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(userNode.id, node.id)) --> state.eventProcessor.changes,
          ))),
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
    pageParentId: NodeId,
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
      val change = GraphChanges.addNodeWithParent(Node.MarkdownTask(str), parentId :: pageParentId :: Nil)
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
        val newStageNode = Node.MarkdownStage(str)
        val add = GraphChanges.addNodeWithParent(newStageNode, pageParentId)
        val expand = GraphChanges.connect(Edge.Expanded)(state.user.now.id, newStageNode.id)
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
