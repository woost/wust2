package wust.webApp.views

import fontAwesome.freeSolid

import collection.breakOut
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph._
import wust.ids.{NodeId, NodeRole, UserId}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.webApp.{BrowserDetect, Icons, ItemProperties}
import wust.webApp.dragdrop.{DragContainer, DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, NodePermission, View}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object KanbanView {
  import SharedViewElements._

  case class KanbanData(workspaceId: NodeId, inboxNodes: Seq[Node], columnTree: Seq[Tree])
  object KanbanData {
    def calculate(graph: Graph, pageParentId: NodeId): KanbanData = {
      val pageParentIdx = graph.idToIdx(pageParentId)
      val workspaces = graph.workspacesForParent(pageParentIdx)
      val firstWorkspaceIdx = workspaces.head //TODO: crashes
      val firstWorkspaceId = graph.nodeIds(firstWorkspaceIdx)

      val topLevelStages = graph.childrenIdx(firstWorkspaceIdx).filter(idx => graph.nodes(idx).role == NodeRole.Stage)
      val allStages: ArraySet = {
        val stages = ArraySet.create(graph.size)
        topLevelStages.foreachElement(stages.add)
        algorithm.depthFirstSearchAfterStartsWithContinue(starts = topLevelStages.toArray, graph.childrenIdx, { idx =>
          val isStage = graph.nodes(idx).role == NodeRole.Stage
          if(isStage) stages += idx
          isStage
        })
        stages
      }

      val inboxTasks: ArraySet = {
        val inboxTasks = ArraySet.create(graph.size)
        graph.childrenIdx.foreachElement(firstWorkspaceIdx) { childIdx =>
          if(graph.nodes(childIdx).role == NodeRole.Task) {
            @inline def hasStageParentInWorkspace = graph.parentsIdx(childIdx).exists(allStages.contains)

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
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {


    val activeAddCardFields = Var(Set.empty[List[NodeId]]) // until we use thunks, we have to track, which text fields are active, so they don't get lost when rerendering the whole kanban board
    val newColumnFieldActive = Var(false)
    val newTagFieldActive = Var(false)
    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    div(
      height := "100%",
      Styles.flex,
      justifyContent.spaceBetween,

      Rx {
        val page = state.page()
        val graph = state.graph()

        page.parentId.map { pageParentId =>
          val kanbanData = KanbanData.calculate(graph, pageParentId)

          div(
            cls := "kanbanview",

            overflow.auto,

            Styles.flex,
            alignItems.flexStart,
            renderInboxColumn(state, kanbanData.workspaceId, path = Nil, kanbanData.inboxNodes, activeAddCardFields, selectedNodeIds),
            div(
              cls := s"kanbancolumnarea",
              keyed,
              Styles.flexStatic,

              Styles.flex,
              alignItems.flexStart,
              kanbanData.columnTree.map(tree => renderStageTree(state, graph, tree, parentId = pageParentId, pageParentId = pageParentId, path = Nil, activeAddCardFields, selectedNodeIds, isTopLevel = true)),

              registerDragContainer(state, DragContainer.Kanban.ColumnArea(pageParentId, kanbanData.columnTree.map(_.node.id))),
            ),
            newColumnArea(state, pageParentId, newColumnFieldActive).apply(Styles.flexStatic),
          )
        }
      },
      //onDomMount.async.foreach(state.isContentLoading() = false)
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
      case Tree.Parent(node, stageChildren) if node.role == NodeRole.Stage =>
        if(graph.isExpanded(state.user.now.id, node.id)) {
          val cardChildren = graph.taskChildrenIdx(graph.idToIdx(node.id)).map(idx => Tree.Leaf(graph.nodes(idx)))
          val sortedChildren = TaskOrdering.constructOrderingOf[Tree](graph, node.id, stageChildren ++ cardChildren, (t: Tree) => t.node.id)
          renderColumn(state, graph, node, sortedChildren, parentId, pageParentId, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel)
        }
        else
          renderColumn(state, graph, node, Nil, parentId, pageParentId, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel, isCollapsed = true)
      case Tree.Leaf(node) if node.role == NodeRole.Stage =>
          renderColumn(state, graph, node, Nil, parentId, pageParentId, path, activeAddCardFields, selectedNodeIds, isTopLevel = isTopLevel)
      case Tree.Leaf(node) if node.role == NodeRole.Task =>
          renderCard(state, node, parentId, pageParentId, path, selectedNodeIds, activeAddCardFields)
      case _ => VDomModifier.empty // if card is not also direct child of page, it is probably a mistake
    }
  }


  private def renderInboxColumn(
    state: GlobalState,
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
          Styles.flex,
          GraphChangesAutomationUI.settingsButton(state, workspaceId),
        ),
      ),
      div(
        cls := "kanbancolumnchildren",
        registerDragContainer(state, DragContainer.Kanban.Inbox(workspaceId, children.map(_.id))),
        children.map(node => renderCard(state, node, parentId = workspaceId, pageParentId = workspaceId, path = path, selectedNodeIds,activeAddCardFields)),
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
    pageParentId: NodeId,
    path: List[NodeId],
    activeAddCardFields: Var[Set[List[NodeId]]],
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
    isCollapsed: Boolean = false,
  )(implicit ctx: Ctx.Owner): VNode = {

    val editable = Var(false)
    val columnTitle = editableNode(state, node, editMode = editable, maxLength = Some(maxLength))(ctx)(cls := "kanbancolumntitle")

    val messageChildrenCount = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(node.id))
    }

    val canWrite = NodePermission.canWrite(state, node.id)

    val buttonBar = div(
      cls := "buttonbar",
      Styles.flex,
      Rx {
        def ifCanWrite(mod: => VDomModifier): VDomModifier = VDomModifier.ifTrue(canWrite())(mod)

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
            cursor.pointer, UI.popup := "Archive"
          )),
//          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(node.id)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
        )
      },

      GraphChangesAutomationUI.settingsButton(state, node.id),
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
            onClick.stopPropagation.mapTo(state.urlConfig.now.focus(Page(node.id), View.Conversation)) --> state.urlConfig,
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
            onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes,
            cursor.pointer,
            paddingBottom := "7px",
          ),
          registerDragContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id), workspace = pageParentId)), // allows to drop cards on collapsed columns
        )
      ) else VDomModifier(
        div(
          cls := "kanbancolumnchildren",
          registerDragContainer(state, DragContainer.Kanban.Column(node.id, children.map(_.node.id), workspace = pageParentId)),
          keyed(node.id, parentId),
          children.map(tree => renderStageTree(state, graph, tree, parentId = node.id, pageParentId = pageParentId, path = node.id :: path, activeAddCardFields, selectedNodeIds)),
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
    parentId: NodeId, // is either a column (stage), a parent card, or else (if the card is in inbox) equal to pageParentId
    pageParentId: NodeId,
    path: List[NodeId],
    selectedNodeIds:Var[Set[NodeId]],
    activeAddCardFields: Var[Set[List[NodeId]]],
    showCheckbox:Boolean = false,
    isDone:Boolean = false,
    inOneLine: Boolean = false,
    dragTarget: NodeId => DragTarget = DragItem.Task.apply,
    dragPayload: NodeId => DragPayload = DragItem.Task.apply,
  )(implicit ctx: Ctx.Owner): VNode = {
    val editable = Var(false)

    val assignment = Rx {
      val graph = state.graph()
      val nodeUsers = graph.assignedUsersIdx(graph.idToIdx(node.id))
      nodeUsers.map(userIdx => graph.nodes(userIdx).asInstanceOf[Node.User])
    }


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

    val isPlainCard = Rx { taskStats().isEmpty && assignment().isEmpty }

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
      val createSubtasks = menuItem(
        "Create subtasks", "Create subtasks", Icons.tasks,
        onClick.stopPropagation.mapTo(state.urlConfig.now.focus(Page(node.id), View.Tasks)) --> state.urlConfig)
      val startConversation = menuItem("Start conversation", "Start conversation about this card", Icons.conversation,
        onClick.stopPropagation.mapTo(state.urlConfig.now.focus(Page(node.id), View.Conversation)) --> state.urlConfig)
      val archive = menuItem(
        "Archive", "Archive", Icons.delete,
        Rx {
          onClick.stopPropagation foreach {
            val graph = state.graph()
            val nodeIdx = graph.idToIdx(node.id)
            val workspaces:Array[NodeId] = graph.workspacesForNode(nodeIdx).map(graph.nodeIds)
            val stageParents:Array[NodeId] = graph.getRoleParentsIdx(nodeIdx, NodeRole.Stage).map(graph.nodeIds)(breakOut)

            val changes = GraphChanges.delete(node.id, workspaces) merge GraphChanges.delete(node.id, stageParents)
            state.eventProcessor.changes.onNext(changes)
            selectedNodeIds.update(_ - node.id)
          }
        })
      val edit = menuItem(
        "Edit", "Edit", Icons.edit, 
        onClick.stopPropagation(true) --> editable
      )
      val expand = menuItem(
        "Expand", "Expand", Icons.expand,
        onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes)
      val collapse = menuItem(
        "Collapse", "Collapse", Icons.collapse,
        onClick.stopPropagation(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, node.id)) --> state.eventProcessor.changes)
      val propertiesBuilder = menuItem(
        ItemProperties.naming, ItemProperties.naming, Icons.property,
        span())
      def properties(compressed : Boolean) = {
        ItemProperties.manageProperties(
                  state, node.id,
                  propertiesBuilder(compressed))
      }
      def toggle(compress : Boolean) = Rx {
        if(state.graph().isExpanded(state.user.now.id, node.id))
          collapse(compress)
        else
          expand(compress),
      }
      /// these are only visible via the more menu
      val moreMenuItems = Seq[(Boolean) => VDomModifier](
        createSubtasks,
        startConversation,
        toggle _,
        edit,
        archive,
        properties
      )
      /// these are always visible on hover
      val immediateMenuItems = Seq[(Boolean) => VDomModifier](
        toggle _,
        edit,
        archive
      )
      // ideally this would be always visible, but since the outer div does no longer hide overflow,
      // the ellipsis are always visible, even if they are overlapped by the „Add card“ area
      //visibility.visible,
      val moreMenu = UI.dropdownMenu(VDomModifier(
        div(cls := "header", "Context menu", cursor.default),
        moreMenuItems.map(_(false))
      )).apply(
        cls := "pointing top right",
        div(cls := "fa-fw", Icons.ellipsisV),
        UI.popup := "More",
      )

      div(
        cls := "buttonbar",
        Styles.flex,
        Rx {
          if(editable()) {
            VDomModifier.empty
          } else VDomModifier(
            immediateMenuItems.map(_(true)),
          )
        },
        Rx { (!editable()).ifTrue[VDomModifier](moreMenu) }
      )
    }

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

    def cardFooter(implicit ctx:Ctx.Owner) = div(
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
              if (taskStats().taskChildrenCount > 0) VDomModifier(
                s"${taskStats().taskDoneCount}/${taskStats().taskChildrenCount}",
                UI.popup := "Zoom to show subtasks",
              )
              else VDomModifier(
                cls := "emptystat",
                UI.popup := "Create subtasks"
              ),
              onClick.stopPropagation.mapTo(state.urlConfig.now.focus(Page(node.id), View.Tasks)) --> state.urlConfig,
              cursor.pointer,
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
              onClick.stopPropagation.mapTo(state.urlConfig.now.focus(Page(node.id), View.Conversation)) --> state.urlConfig,
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
    )

    def cardTags(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner) = {
      Rx {
        val graph = state.rawGraph()
        val nodeIdx = graph.idToIdx(nodeId)
        val tags = graph.tagParentsIdx(nodeIdx).map(graph.nodes)
        VDomModifier.ifTrue(tags.nonEmpty) {
          div(
            margin := "5px",
            marginTop := "0",
            textAlign.right,
            tags.map(tag => removableNodeTag(state, tag, taggedNodeId = nodeId)),
          )
        }
      }
    }

    def cardProperties(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner) = {
      Rx {
        val graph = state.rawGraph()
        val nodeIdx = graph.idToIdx(nodeId)
        val properties = graph.propertyPairIdx(nodeIdx)
        VDomModifier.ifTrue(properties.nonEmpty) {
          div(
            margin := "5px",
            marginTop := "0",
            textAlign.right,
            properties.map { case (propertyKey: Edge.LabeledProperty, propertyValue: Node) =>
              Components.removablePropertyTag(state, propertyKey, propertyValue)
            }
          )
        }
      }
    }

    def subCards(graph:Graph)(implicit ctx: Ctx.Owner) = {
      div(
        boxShadow := "inset rgba(158, 158, 158, 0.45) 0px 1px 0px 1px",
        margin := "5px",
        padding := "1px 5px 6px 5px",
        borderRadius := "3px",
        backgroundColor := "#EFEFEF",
        partitionedTaskChildren(node.id, graph) match {
          case (doneTasks, todoTasks) =>
            val sortedTodoTasks = TaskOrdering.constructOrderingOf[Int](graph, node.id, todoTasks, graph.nodeIds)
            VDomModifier(
              div(
                minHeight := "50px",
                sortedTodoTasks.map{ childIdx =>
                  val childNode = graph.nodes(childIdx)
                  renderCard(state,childNode,parentId = node.id, pageParentId = node.id, path = node.id :: path, selectedNodeIds = selectedNodeIds, activeAddCardFields = activeAddCardFields, showCheckbox = true).apply(
                    marginTop := "5px",
                  )
                },
                // sortable: draggable needs to be direct child of container
                registerDragContainer(state, DragContainer.Kanban.Card(node.id, sortedTodoTasks.map(graph.nodeIds))),
              ),
              div(
                doneTasks.map{ childIdx =>
                  val childNode = graph.nodes(childIdx)
                  renderCard(state,childNode,parentId = node.id, pageParentId = node.id, path = node.id :: path,selectedNodeIds = selectedNodeIds, activeAddCardFields = activeAddCardFields, showCheckbox = true, isDone = true).apply(
                    marginTop := "5px",
                    opacity := 0.5,
                  )
                }
              )
            )
        },
        addCardField(state, node.id, path = path, activeAddCardFields, scrollHandler = None, textColor = Some("rgba(0,0,0,0.62)")).apply(padding := "8px 0px 0px 0px")
      )
    }

    nodeCardEditable(
      state, node,
      maxLength = Some(maxLength),
      editMode = editable,
      contentInject = if(isDone) textDecoration.lineThrough else VDomModifier.empty
      ).prepend(
      if(showCheckbox)
        VDomModifier(
          taskCheckbox(state, node, parentId :: Nil).apply(float.left, marginRight := "5px")
        )
      else VDomModifier.empty
    ).apply(
      Rx{ VDomModifier.ifNot(editable() || isDone)(drag(payload = dragPayload(node.id), target = dragTarget(node.id))) }, // prevents dragging when selecting text
      keyed(node.id, parentId),
      // fixes unecessary scrollbar, when card has assignment
      // removed by tkarolski, to allow dropdown menu to popup
      //overflow.hidden,

      div(
        Styles.flex,
        flexDirection.row,
        Components.automatedNodesOfNode(state, node),
        cardTags(state, node.id),
        cardProperties(state, node.id),
        Rx { VDomModifier.ifTrue(!isPlainCard())(cardFooter) },
      ),
      Rx {
        val graph = state.graph()
        val userId = state.user().id
        VDomModifier.ifTrue(graph.isExpanded(userId, node.id))(
          subCards(graph)
        )
      },

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "0", right := "0"),
//      onDblClick.stopPropagation(state.urlConfig.now.copy(page = Page(node.id))) --> state.urlConfig,
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
      val workspaces:Set[NodeId] = graph.workspacesForParent(graph.idToIdx(parentId)).map(graph.nodeIds)(breakOut)
      val change = GraphChanges.addNodeWithParent(createdNode, workspaces + parentId)

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
