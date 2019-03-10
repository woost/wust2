package wust.webApp.views

import fontAwesome.freeSolid
import monix.reactive.Observer

import collection.breakOut
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph._
import wust.ids.{ChildId, NodeId, NodeRole, ParentId, UserId, View}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.webApp.{BrowserDetect, Icons, ItemProperties}
import wust.webApp.dragdrop.{DragContainer, DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, FocusPreference, GlobalState, NodePermission}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object KanbanView {
  import SharedViewElements._

  case class KanbanData(workspaceId: NodeId, inboxNodes: Seq[Node], columnTree: Seq[Tree])
  object KanbanData {
    def calculate(graph: Graph, focusedId: NodeId): KanbanData = {
      val focusedIdx = graph.idToIdx(focusedId)
      val workspaces = graph.workspacesForParent(focusedIdx)
      val firstWorkspaceIdx = workspaces.head //TODO: crashes
      val firstWorkspaceId = graph.nodeIds(firstWorkspaceIdx)

      val topLevelStages = graph.notDeletedChildrenIdx(firstWorkspaceIdx).filter(idx => graph.nodes(idx).role == NodeRole.Stage)
      val allStages: ArraySet = {
        val stages = ArraySet.create(graph.size)
        topLevelStages.foreachElement(stages.add)
        algorithm.depthFirstSearchAfterStartsWithContinue(starts = topLevelStages.toArray, graph.notDeletedChildrenIdx, { idx =>
          val isStage = graph.nodes(idx).role == NodeRole.Stage
          if(isStage) stages += idx
          isStage
        })
        stages
      }

      val inboxTasks: ArraySet = {
        val inboxTasks = ArraySet.create(graph.size)
        graph.notDeletedChildrenIdx.foreachElement(firstWorkspaceIdx) { childIdx =>
          if(graph.nodes(childIdx).role == NodeRole.Task) {
            @inline def hasStageParentInWorkspace = graph.notDeletedParentsIdx(childIdx).exists(allStages.contains)

            if(!hasStageParentInWorkspace) inboxTasks += childIdx
          }
        }
        inboxTasks
      }

      val topLevelStageTrees: Seq[Tree] = topLevelStages.map { stageIdx =>
        graph.roleTree(stageIdx, NodeRole.Stage)
      }

      val sortedTopLevelColumns: Seq[Tree] = TaskOrdering.constructOrderingOf[Tree](graph, firstWorkspaceId, topLevelStageTrees, (t: Tree) => t.node.id)
      val assigneInbox: Seq[Node] = TaskOrdering.constructOrderingOf[Node](graph, firstWorkspaceId, inboxTasks.map(graph.nodes), _.id)

      KanbanData(firstWorkspaceId, assigneInbox, sortedTopLevelColumns)
    }
  }

  private val maxLength = 300 // TODO: use text-overflow:ellipsis instead.
  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val activeAddCardFields = Var(Set.empty[List[NodeId]]) // until we use thunks, we have to track, which text fields are active, so they don't get lost when rerendering the whole kanban board
    val newColumnFieldActive = Var(false)
    val newTagFieldActive = Var(false)
    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    div(
      height := "100%",
      Styles.flex,
      justifyContent.spaceBetween,

      Rx {
        val graph = state.graph()
        val kanbanData = KanbanData.calculate(graph, focusState.focusedId)

        div(
          cls := "kanbanview",

          overflow.auto,

          Styles.flex,
          alignItems.flexStart,
          renderInboxColumn(state, focusState, kanbanData.workspaceId, path = Nil, kanbanData.inboxNodes, activeAddCardFields, selectedNodeIds),
          div(
            cls := s"kanbancolumnarea",
            keyed,
            Styles.flexStatic,

            Styles.flex,
            alignItems.flexStart,
            kanbanData.columnTree.map(tree => renderStageTree(state, graph, tree, parentId = focusState.focusedId, focusState = focusState, path = Nil, activeAddCardFields, selectedNodeIds, isTopLevel = true)),

            registerDragContainer(state, DragContainer.Kanban.ColumnArea(focusState.focusedId, kanbanData.columnTree.map(_.node.id))),
          ),
          newColumnArea(state, focusState.focusedId, newColumnFieldActive).apply(Styles.flexStatic),
        )
      }
    )
  }

  private def renderStageTree(
    state: GlobalState,
    graph: Graph,
    tree: Tree,
    parentId: NodeId,
    focusState: FocusState,
    path: List[NodeId],
    activeAddCardFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    tree match {
      case Tree.Parent(node, stageChildren) if node.role == NodeRole.Stage =>
        if(graph.isExpanded(state.user.now.id, node.id)) {
          val cardChildren = graph.taskChildrenIdx(graph.idToIdx(node.id)).map(idx => Tree.Leaf(graph.nodes(idx)))
          val sortedChildren = TaskOrdering.constructOrderingOf[Tree](graph, node.id, stageChildren ++ cardChildren, (t: Tree) => t.node.id)
          renderColumn(state, graph, node, sortedChildren, parentId, focusState, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel)
        }
        else
          renderColumn(state, graph, node, Nil, parentId, focusState, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel, isCollapsed = true)
      case Tree.Leaf(node) if node.role == NodeRole.Stage =>
          renderColumn(state, graph, node, Nil, parentId, focusState, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel)
      case Tree.Leaf(node) if node.role == NodeRole.Task =>
          renderCard(state, node, parentId, focusState, path, selectedNodeIds, activeAddCardFields)
      case _ => VDomModifier.empty // if card is not also direct child of page, it is probably a mistake
    }
  }


  private def renderInboxColumn(
    state: GlobalState,
    focusState: FocusState,
    workspaceId: NodeId,
    path: List[NodeId],
    children: Seq[Node],
    activeAddCardFields: Var[Set[List[NodeId]]],
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VNode = {
    val columnColor = BaseColors.kanbanColumnBg.copy(h = hue(workspaceId)).toHex
    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      cls := "kanbantoplevelcolumn",
      keyed,
      border := s"1px dashed $columnColor",
      p(
        cls := "kanban-uncategorized-title",
        Styles.flex,
        justifyContent.spaceBetween,
        alignItems.center,
        "Inbox / Todo",
        div(
          cls := "buttonbar",
          drag(DragItem.DisableDrag),
          Styles.flex,
          GraphChangesAutomationUI.settingsButton(state, workspaceId, activeMod = visibility.visible),
        ),
      ),
      div(
        cls := "kanbancolumnchildren",
        registerDragContainer(state, DragContainer.Kanban.Inbox(workspaceId, children.map(_.id))),
        children.map(node => renderCard(state, node, parentId = workspaceId, focusState = focusState.copy(focusedId = workspaceId), path = path, selectedNodeIds,activeAddCardFields)),
        scrollHandler.modifier,
      ),
      addCardField(state, workspaceId, path = Nil, activeAddCardFields, Some(scrollHandler), textColor = Some("rgba(0,0,0,0.62)"))
    )
  }

  private def renderColumn(
    state: GlobalState,
    graph: Graph,
    node: Node,
    children: Seq[Tree],
    parentId: NodeId,
    focusState: FocusState,
    path: List[NodeId],
    activeAddCardFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
    isCollapsed: Boolean = false,
  )(implicit ctx: Ctx.Owner): VNode = {

    val editable = Var(false)
    val columnTitle = editableNode(state, node, editable, maxLength = Some(maxLength))(ctx)(cls := "kanbancolumntitle")

    val messageChildrenCount = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(node.id))
    }

    val canWrite = NodePermission.canWrite(state, node.id)

    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      drag(DragItem.DisableDrag),
      Rx {
        def ifCanWrite(mod: => VDomModifier): VDomModifier = VDomModifier.ifTrue(canWrite())(mod)

        if(editable()) {
          VDomModifier.empty
        } else VDomModifier(
          if(isCollapsed)
            div(div(cls := "fa-fw", Icons.expand), onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(node.id, state.user.now.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Expand")
          else
            div(div(cls := "fa-fw", Icons.collapse), onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(node.id, state.user.now.id)) --> state.eventProcessor.changes, cursor.pointer, UI.popup := "Collapse"),
          ifCanWrite(div(div(cls := "fa-fw", Icons.edit), onClick.stopPropagation(true) --> editable, cursor.pointer, UI.popup := "Edit")),
          ifCanWrite(div(div(cls := "fa-fw", Icons.delete),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(GraphChanges.delete(ChildId(node.id), ParentId(parentId)))
              selectedNodeIds.update(_ - node.id)
            },
            cursor.pointer, UI.popup := "Archive"
          )),
//          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(node.id)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
        )
      },

      GraphChangesAutomationUI.settingsButton(state, node.id, activeMod = visibility.visible),
    )

    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      if(isTopLevel) cls := "kanbantoplevelcolumn" else cls := "kanbansubcolumn",
      keyed(node.id, parentId),
      backgroundColor := BaseColors.kanbanColumnBg.copy(h = hue(node.id)).toHex,
      Rx{
        VDomModifier.ifNot(editable())(dragWithHandle(DragItem.Stage(node.id))) // prevents dragging when selecting text
      },
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
            onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(node.id, state.user.now.id)) --> state.eventProcessor.changes,
            cursor.pointer,
            paddingBottom := "7px",
          ),
          registerDragContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id), workspace = focusState.focusedId)), // allows to drop cards on collapsed columns
        )
      ) else VDomModifier(
        div(
          cls := "kanbancolumnchildren",
          registerDragContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id), workspace = focusState.focusedId)),
          keyed(node.id, parentId),
          children.map(tree => renderStageTree(state, graph, tree, parentId = node.id, focusState = focusState, path = node.id :: path, activeAddCardFields, selectedNodeIds)),
          scrollHandler.modifier,
        ),
      ),
      div(
        cls := "kanbancolumnfooter",
        Styles.flex,
        justifyContent.spaceBetween,
        addCardField(state, node.id, path, activeAddCardFields, Some(scrollHandler), None).apply(width := "100%"),
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

  def renderCard(
    state: GlobalState,
    node: Node,
    parentId: NodeId, // is either a column (stage), a parent card, or else (if the card is in inbox) equal to focusState.focusedId
    focusState: FocusState,
    path: List[NodeId],
    selectedNodeIds:Var[Set[NodeId]],
    activeAddCardFields: Var[Set[List[NodeId]]],
    showCheckbox:Boolean = false,
    isDone:Boolean = false,
    inOneLine: Boolean = false,
    dragTarget: NodeId => DragTarget = DragItem.Task.apply,
    dragPayload: NodeId => DragPayload = DragItem.Task.apply,
  )(implicit ctx: Ctx.Owner): VNode = {

    case class TaskStats(messageChildrenCount: Int, taskChildrenCount: Int, taskDoneCount: Int, propertiesCount: Int) {
      @inline def progress = (100 * taskDoneCount) / taskChildrenCount
      @inline def isEmpty = messageChildrenCount == 0 && taskChildrenCount == 0 //&& propertiesCount == 0
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

      val propertiesCount = graph.propertiesEdgeIdx(nodeIdx).length

      TaskStats(messageChildrenCount, taskChildrenCount, taskDoneCount, propertiesCount)
    }

    val buttonBar = {
      /// @return a Builder for a menu item which takes a boolean specifying whether it should be compressed or not
      def menuItem(shortName : String,
                   longDesc : String,
                   icon : fontAwesome.IconDefinition,
                   action : VDomModifier) = {
        def builder(compressed : Boolean = false) = div(
          cls := "item",
          span(cls := "icon", icon),
          action,
          cursor.pointer,
          compressed.ifTrue[VDomModifier](UI.popup := longDesc),
          (!compressed).ifTrue[VDomModifier](shortName)
        )
        builder _
      }
      val archive = menuItem(
        "Archive", "Archive", Icons.delete,
        Rx {
          onClick.stopPropagation foreach {
            val graph = state.graph()
            val focusedIdx = graph.idToIdx(focusState.focusedId)
            val nodeIdx = graph.idToIdx(node.id)
            val stageParents = graph.getRoleParentsIdx(nodeIdx, NodeRole.Stage).filter(graph.workspacesForParent(_).contains(focusedIdx)).map(graph.nodeIds)
            val hasMultipleStagesInFocusedNode = stageParents.exists(_ != parentId)
            val removeFromWorkspaces = if (hasMultipleStagesInFocusedNode) GraphChanges.empty else GraphChanges.delete(ChildId(node.id), ParentId(focusState.focusedId))

            val changes = removeFromWorkspaces merge GraphChanges.delete(ChildId(node.id), ParentId(parentId))
            state.eventProcessor.changes.onNext(changes)
            selectedNodeIds.update(_ - node.id)
          }
        })
      val expand = menuItem(
        "Expand", "Expand", Icons.expand,
        onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(node.id, state.user.now.id)) --> state.eventProcessor.changes)
      val collapse = menuItem(
        "Collapse", "Collapse", Icons.collapse,
        onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(node.id, state.user.now.id)) --> state.eventProcessor.changes)
      def toggle(compress : Boolean) = Rx {
        if(state.graph().isExpanded(state.user.now.id, node.id))
          collapse(compress)
        else
          expand(compress)
      }

      /// these are always visible on hover
      val immediateMenuItems = Seq[(Boolean) => VDomModifier](
        toggle _,
        archive
      )

      div(
        cls := "buttonbar",
        drag(DragItem.DisableDrag),
        Styles.flex,
        immediateMenuItems.map(_(true))
      )
    }

    def renderTaskProgress(taskstats: TaskStats) = Rx {
      val progress = taskstats.progress
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
            width := s"${math.max(progress, 0)}%",
            backgroundColor := s"${if(progress < 100) "#ccc" else "#32CD32"}",
            UI.popup := s"$progress% Progress. ${taskStats().taskDoneCount} / ${taskStats().taskChildrenCount} done."
          ),
        ),
      )
    }

    val propertySingle = Rx {
      val graph = state.graph()
      PropertyData.Single(graph, graph.idToIdxOrThrow(node.id))
    }

    val cardDescription = VDomModifier(
      Styles.flex,
      flexWrap.wrap,

      Components.automatedNodesOfNode(state, node),
      propertySingle.map { propertySingle =>
        VDomModifier(
          propertySingle.info.tags.map { tag =>
            Components.removableNodeTag(state, tag, taggedNodeId = node.id)
          },

          propertySingle.properties.map { property =>
            property.values.map { value =>
              Components.removablePropertyTag(state, value.edge, value.node)
            }
          },

          div(
            marginLeft.auto,
            Styles.flex,
            justifyContent.flexEnd,
            flexWrap.wrap,
            propertySingle.info.assignedUsers.map(userNode =>
              Components.removableUserAvatar(state, userNode, targetNodeId = node.id)
            ),
          ),
        )
      }
    )

    val cardFooter = div(
      cls := "childstats",
      Styles.flex,
      alignItems.center,
      Rx{
        VDomModifier(
          VDomModifier.ifTrue(taskStats().taskChildrenCount > 0)(
            renderTaskCount(
              s"${taskStats().taskDoneCount}/${taskStats().taskChildrenCount}",
              UI.popup := "Subtasks",
              onClick.stopPropagation.mapTo {
                val edge = Edge.Expanded(node.id, state.user.now.id)
                if (state.graph.now.isExpanded(state.user.now.id, node.id)) GraphChanges(delEdges = Set(edge)) else GraphChanges(addEdges = Set(edge))
              } --> state.eventProcessor.changes,
              cursor.pointer,
            ),
            renderTaskProgress(taskStats()),
          ),

          VDomModifier.ifTrue(taskStats().messageChildrenCount > 0)(
            renderMessageCount(
              taskStats().messageChildrenCount,
              UI.popup := "Zoom to show comments",
              onClick.stopPropagation(Some(FocusPreference(node.id, Some(View.Conversation)))) --> state.rightSidebarNode,
              cursor.pointer,
            ),
          ),
        )
      },
    )

    nodeCard(
      node,
      maxLength = Some(maxLength),
      contentInject = VDomModifier(
        VDomModifier.ifTrue(isDone)(textDecoration.lineThrough),
        VDomModifier.ifTrue(inOneLine)(alignItems.flexStart, cardDescription, marginRight := "40px") // marginRight to not interfere with button bar...
      ),
      nodeInject = VDomModifier.ifTrue(inOneLine)(marginRight := "10px")
    ).prepend(
      Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id),
      VDomModifier.ifTrue(showCheckbox)(
        taskCheckbox(state, node, parentId :: Nil).apply(float.left, marginRight := "5px")
      )
    ).apply(
      VDomModifier.ifNot(isDone)(drag(payload = dragPayload(node.id), target = dragTarget(node.id))),
      keyed(node.id, parentId),
      // fixes unecessary scrollbar, when card has assignment
      overflow.hidden,

      VDomModifier.ifNot(inOneLine)(div(margin := "0 3px", alignItems.center, cardDescription)),
      cardFooter,

      Rx {
        val graph = state.graph()
        val userId = state.user().id
        VDomModifier.ifTrue(graph.isExpanded(userId, node.id))(
          ListView(state, focusState = focusState.copy(isNested = true, focusedId = node.id)).apply(
            onClick.stopPropagation --> Observer.empty,
            drag(DragItem.DisableDrag),
            boxShadow := "inset rgba(158, 158, 158, 0.45) 0px 1px 0px 1px",
            margin := "3px",
            borderRadius := "3px",
            backgroundColor := "#EFEFEF",
          )
        )
      },

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "3px", right := "3px"), // distance to not interefere with sidebar-focus box-shadow around node
    )
  }

  private def addCardField(
    state: GlobalState,
    parentId: NodeId,
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
      val graph = state.graph.now
      val workspaces:Set[ParentId] = graph.workspacesForParent(graph.idToIdx(parentId)).map(idx => ParentId(graph.nodeIds(idx)))(breakOut)
      val change = GraphChanges.addNodeWithParent(createdNode, workspaces + ParentId(parentId))

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
            showMarkdownHelp = false
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

  private def newColumnArea(state: GlobalState, focusedId:NodeId, fieldActive: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    def submitAction(str:String) = {
      val change = {
        val newStageNode = Node.MarkdownStage(str)
        val add = GraphChanges.addNodeWithParent(newStageNode, ParentId(focusedId))
        val expand = GraphChanges.connect(Edge.Expanded)(newStageNode.id, state.user.now.id)
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
            ),
            showMarkdownHelp = true
          ).apply(
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
