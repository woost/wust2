package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import collection.breakOut
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
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {


    val activeAddCardFields = Var(Set.empty[List[NodeId]]) // until we use thunks, we have to track, which text fields are active, so they don't get lost when rerendering the whole kanban board
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
            graph.roleTree(stageIdx, NodeRole.Stage, pageParentIdx)
          }

          val sortedTopLevelColumns:Seq[Tree] = TaskOrdering.constructOrderingOf[Tree](graph, pageParentId, topLevelColumns, (t: Tree) => t.node.id)
          val assigneInbox = inboxTasks.map(graph.nodeIds)

            VDomModifier(
              renderInboxColumn(state, pageParentId, pageParentId, path = Nil, assigneInbox, activeAddCardFields, selectedNodeIds),
              div(
                cls := s"kanbancolumnarea",
                keyed,
                Styles.flexStatic,

                Styles.flex,
                alignItems.flexStart,
                sortedTopLevelColumns.map(tree => renderStageTree(state, graph, tree, parentId = pageParentId, pageParentId = pageParentId, path = Nil, activeAddCardFields, selectedNodeIds, isTopLevel = true)),

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
    activeAddCardFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val pageParentIdx = graph.idToIdx(pageParentId)
    tree match {
      case Tree.Parent(node, children) if node.role == NodeRole.Stage =>
        Rx {
          if(state.graph().isExpanded(state.user.now.id, node.id)) {
            val sortedChildren = TaskOrdering.constructOrderingOf[Tree](graph, node.id, children, (t: Tree) => t.node.id)
            renderColumn(state, graph, node, sortedChildren, parentId, pageParentId, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel)
          }
          else
            renderColumn(state, graph, node, Nil, parentId, pageParentId, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel, isCollapsed = true)
        }
      case Tree.Leaf(node) if node.role == NodeRole.Stage =>
          renderColumn(state, graph, node, Nil, parentId, pageParentId, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel)
      case Tree.Leaf(node) if node.role == NodeRole.Task =>
          renderCard(state, node, parentId, pageParentId, path, selectedNodeIds, activeAddCardFields)
      case _ => VDomModifier.empty // if card is not also direct child of page, it is probably a mistake
    }
  }


  private def renderInboxColumn(
    state: GlobalState,
    parentId:NodeId,
    pageParentId:NodeId,
    path: List[NodeId],
    children: Seq[NodeId],
    activeAddCardFields: Var[Set[List[NodeId]]],
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
        sortedChildren.map(nodeId => renderCard(state, state.graph.now.nodesById(nodeId), parentId = parentId, pageParentId = pageParentId, path = path, selectedNodeIds,activeAddCardFields)),
        scrollHandler.modifier,
      ),
      addCardField(state, parentId, pageParentId, path = Nil, activeAddCardFields, Some(scrollHandler), textColor = Some("rgba(0,0,0,0.62)"))
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
    activeAddCardFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
    isCollapsed: Boolean = false,
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
            div(div(cls := "fa-fw", Icons.expand), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Expand")
          else
            div(div(cls := "fa-fw", Icons.collapse), onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Collapse"),
          ifCanWrite(div(div(cls := "fa-fw", Icons.edit), onClick.stopPropagation(true) --> editable, cursor.pointer, UI.popup := "Edit")),
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

    val stageCommentZoom = Rx{
      // hide comment zoom, when addNodeField is active
      val fullPath = node.id :: path
      val active = activeAddCardFields() contains fullPath
      active.ifFalse[VDomModifier](
        div(
          cls := "childstats",
          renderMessageCount(
            if (messageChildrenCount() > 0) VDomModifier(messageChildrenCount())
            else VDomModifier(cls := "emptystat"),
            onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Conversation)) --> state.viewConfig,
            cursor.pointer
          )
        )
      )
    }

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
            div(cls := "fa-fw", Icons.expand, UI.popup := "Expand"),
            onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes,
            cursor.pointer,
            paddingBottom := "7px",
          ),
          registerSortableContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id), workspace = pageParentId)), // allows to drop cards on collapsed columns
        )
      ) else VDomModifier(
        div(
          cls := "kanbancolumnchildren",
          registerSortableContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id), workspace = pageParentId)),
          keyed(node.id, parentId),
          children.map(tree => renderStageTree(state, graph, tree, parentId = node.id, pageParentId = pageParentId, path = node.id :: path, activeAddCardFields, selectedNodeIds)),
          scrollHandler.modifier,
        ),
      ),
      div(
        cls := "kanbancolumnfooter",
        Styles.flex,
        justifyContent.spaceBetween,
        addCardField(state, node.id, pageParentId, path, activeAddCardFields, Some(scrollHandler), None).apply(width := "100%"),
        // stageCommentZoom,
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
    parentId: NodeId, // is either a column (stage), a parent card, or else (if the card is in inbox) equal to pageParentId
    pageParentId: NodeId,
    path: List[NodeId],
    selectedNodeIds:Var[Set[NodeId]],
    activeAddCardFields: Var[Set[List[NodeId]]],
    showCheckbox:Boolean = false,
  )(implicit ctx: Ctx.Owner): VNode = {
    val editable = Var(false)

    val rendered = nodeCardEditable(
      state, node,
      maxLength = Some(maxLength),
      editMode = editable,
      submit = state.eventProcessor.changes).prepend(
      if(showCheckbox)
        VDomModifier(
          taskCheckbox(state, node, parentId :: Nil).apply(float.left, marginRight := "5px")
        )
      else VDomModifier.empty
    )

    val assignment = Rx {
      val graph = state.graph()
      val nodeUsers = graph.assignedUsersIdx(graph.idToIdx(node.id))
      nodeUsers.map(userIdx => graph.nodes(userIdx).asInstanceOf[Node.User])
    }



    case class TaskStats(messageChildrenCount: Int, taskChildrenCount: Int, taskDoneCount: Int) {
      @inline def progress = (100 * taskDoneCount) / taskChildrenCount
      @inline def isEmpty = messageChildrenCount == 0 && taskChildrenCount == 0
      @inline def nonEmpty = !isEmpty
    }
    val taskStats = Rx {
      val graph = state.graph()
      val nodeIdx = graph.idToIdx(node.id)

      val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)

      val taskChildren = graph.taskChildrenIdx(nodeIdx)
      val taskChildrenCount = taskChildren.length

      val taskDoneCount = taskChildren.fold(0) { (count, childIdx) =>
        if (graph.isDone(childIdx)) count + 1
        else count
      }

      TaskStats(messageChildrenCount, taskChildrenCount, taskDoneCount)
    }


    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        if(editable()) {
          //          div(div(cls := "fa-fw", freeSolid.faCheck), onClick.stopPropagation(false) --> editable, cursor.pointer)
          VDomModifier.empty
        } else VDomModifier(
          VDomModifier.ifTrue(taskStats().isEmpty)(VDomModifier(
            div(div(cls := "fa-fw", Icons.tasks), onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Tasks)) --> state.viewConfig, cursor.pointer, UI.popup := "Zoom to show subtasks"),
            div(div(cls := "fa-fw", Icons.conversation), onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Conversation)) --> state.viewConfig, cursor.pointer, UI.popup := "Start conversation about this card"),
          )),
          if(state.graph().isExpanded(state.user.now.id, node.id))
            div(div(cls := "fa-fw", Icons.collapse), onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Collapse")
          else
            div(div(cls := "fa-fw", Icons.expand), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Expand"),
          div(div(cls := "fa-fw", Icons.edit), onClick.stopPropagation(true) --> editable, cursor.pointer, UI.popup := "Edit"),
          div(
            div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              val graph = state.graph()
              val nodeIdx = graph.idToIdx(node.id)
              val workspaces:Array[NodeId] = graph.workspacesForNode(nodeIdx).map(graph.nodeIds)
              val stageParents:Array[NodeId] = graph.getRoleParentsIdx(nodeIdx, NodeRole.Stage).map(graph.nodeIds)(breakOut)

              val changes = GraphChanges.delete(node.id, workspaces) merge GraphChanges.delete(node.id, stageParents)
              state.eventProcessor.changes.onNext(changes)
              selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, UI.popup := "Delete"
          ),
          //          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(node.id)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
        )
      }
    )

    val renderTaskProgress = Rx {
      if (taskStats().taskChildrenCount > 0) {

        val progress = taskStats().progress
        VDomModifier(
          div(
            cls := "childstat",
            Styles.flex,
            flexGrow := 1,
            alignItems.flexEnd,
            minWidth := "40px",
            backgroundColor := "#eee",
            borderRadius := "2px",
            margin := "3px 10px",
            div(
              height := "3px",
              padding := "0",
              width := s"${math.max(progress, 1)}%",
              backgroundColor := s"${if(progress < 100) "#ccc" else "#32CD32"}",
              UI.popup := s"$progress% Progress. ${taskStats().taskDoneCount} / ${taskStats().taskChildrenCount} done."
            ),
          ),
        )
      } else VDomModifier(cls := "emptystat")
    }

    def partitionedTaskChildren(nodeId:NodeId, graph:Graph):(Seq[Int], Seq[Int]) = {
      val nodeIdx = graph.idToIdx(nodeId)
      graph.taskChildrenIdx(nodeIdx).partition(graph.isDone)
    }

    rendered(
      // sortable: draggable needs to be direct child of container
      Rx { if(editable()) dragDisabled else sortableAs(DragItem.Kanban.Card(node.id)) }, // prevents dragging when selecting text
      dragTarget(DragItem.Kanban.Card(node.id)),
//      registerDraggableContainer(state),
      keyed(node.id, parentId),
      cls := "draghandle",
      overflow.hidden, // fixes unecessary scrollbar, when card has assignment

      Rx {
        VDomModifier.ifTrue(taskStats().nonEmpty || assignment().nonEmpty)(
          div(
            cls := "cardfooter",
            Styles.flex,
            justifyContent.flexEnd,
            alignItems.flexEnd,
            flexGrow := 1,

            div(
              cls := "childstats",
              Styles.flex,
              flexDirection.row,
              alignItems.center,
              flexGrow := 1,
              Rx{
                VDomModifier(
                  renderTaskCount(
                    if (taskStats().taskChildrenCount > 0) VDomModifier(s"${taskStats().taskDoneCount}/${taskStats().taskChildrenCount}")
                    else VDomModifier(cls := "emptystat"),
                    onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Tasks)) --> state.viewConfig,
                    cursor.pointer,
                    UI.popup := "Zoom to show subtasks",
                  ),
                  renderTaskProgress(),
                  renderMessageCount(
                    if (taskStats().messageChildrenCount > 0) VDomModifier(
                      taskStats().messageChildrenCount,
                      UI.popup := "Zoom to show comments",
                    )
                    else VDomModifier(
                      cls := "emptystat",
                      UI.popup := "Start conversation about this card"
                    ),
                    onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Conversation)) --> state.viewConfig,
                    cursor.pointer,
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
        )
      },

      Rx {
        val graph = state.graph()
        VDomModifier.ifTrue(graph.isExpanded(state.user().id, node.id))(
          div(
            boxShadow := "inset rgba(158, 158, 158, 0.45) 0px 1px 0px 1px",
            margin := "5px",
            padding := "1px 5px 6px 5px",
            borderRadius := "3px",
            backgroundColor := "#EFEFEF",
            partitionedTaskChildren(node.id, graph) match {
              case (doneTasks, todoTasks) =>
                val sortedTodoTasks = TaskOrdering.constructOrderingOf[Int](graph, pageParentId, todoTasks, graph.nodeIds)
                VDomModifier(
                  div(
                    minHeight := "50px",
                    sortedTodoTasks.map{ childIdx =>
                      val childNode = graph.nodes(childIdx)
                      renderCard(state,childNode,parentId = node.id, pageParentId = node.id, path = node.id :: path, selectedNodeIds = selectedNodeIds, activeAddCardFields = activeAddCardFields, showCheckbox = true).apply(
                        marginTop := "5px",
                      )
                    },
                    registerSortableContainer(state, DragContainer.Kanban.Card(node.id, sortedTodoTasks.map(graph.nodeIds))),
                  ),
                  div(
                    doneTasks.map{ childIdx =>
                      val childNode = graph.nodes(childIdx)
                      renderCard(state,childNode,parentId = node.id, pageParentId = node.id, path = node.id :: path,selectedNodeIds = selectedNodeIds, activeAddCardFields = activeAddCardFields, showCheckbox = true).apply(
                        marginTop := "5px",
                        opacity := 0.5,
                        textDecoration.lineThrough,
                      )
                    }
                  )
                )
            },
            addCardField(state, node.id, pageParentId, path = path, activeAddCardFields, scrollHandler = None, textColor = Some("rgba(0,0,0,0.62)")).apply(padding := "8px 0px 0px 0px")
          )
        )
      },

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "0", right := "0"),
//      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    )
  }

  private def addCardField(
    state: GlobalState,
    parentId: NodeId,
    pageParentId: NodeId,
    path:List[NodeId],
    activeAddCardFields: Var[Set[List[NodeId]]],
    scrollHandler: Option[ScrollBottomHandler] = None,
    textColor:Option[String] = None,
  )(implicit ctx: Ctx.Owner): VNode = {
    val fullPath = parentId :: path
    val active = Rx{activeAddCardFields() contains fullPath}
    active.foreach{ active =>
      if(active) scrollHandler.foreach(_.scrollToBottomInAnimationFrame())
    }

    def submitAction(userId: UserId)(str:String) = {
      val createdNode = Node.MarkdownTask(str)
      val change = GraphChanges.addNodeWithParent(createdNode, parentId :: pageParentId :: Nil)

      state.eventProcessor.changes.onNext(change)
    }

    def blurAction(v:String) = {
      if(v.isEmpty) activeAddCardFields.update(_ - fullPath)
    }

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add."

    div(
      cls := "kanbanaddnodefield",
      keyed(parentId),
      Rx {
        if(active())
          inputRow(state,
            submitAction(state.user().id),
            autoFocus = true,
            blurAction = Some(blurAction),
            placeHolderMessage = Some(placeHolder),
            submitIcon = freeSolid.faPlus,
          )
        else
          div(
            cls := "kanbanaddnodefieldtext",
            "+ Add Card",
            color :=? textColor,
            onClick foreach { activeAddCardFields.update(_ + fullPath) }
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
          inputRow(state,
            submitAction,
            autoFocus = true,
            blurAction = Some(blurAction),
            placeHolderMessage = Some(placeHolder),
            submitIcon = freeSolid.faPlus,
            textAreaModifiers = VDomModifier(
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
