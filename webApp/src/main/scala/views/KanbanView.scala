package wust.webApp.views

import fontAwesome.freeSolid
import wust.webApp.DevOnly

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

  private val maxLength = 300 // TODO: use text-overflow:ellipsis instead.
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {


    val activeAddCardFields = Var(Set.empty[List[NodeId]]) // until we use thunks, we have to track, which text fields are active, so they don't get lost when rerendering the whole kanban board
    val newColumnFieldActive = Var(false)
    val newTagFieldActive = Var(false)
    val tagBarExpanded = Var(state.largeScreen)
    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    div(
      height := "100%",
      Styles.flex,
      justifyContent.spaceBetween,

      Rx {
        val page = state.page()
        val graph = state.graph()
        page.parentId.map { pageParentId =>
          val pageParentIdx = graph.idToIdx(pageParentId)
          val workspaces = graph.workspacesForParent(pageParentIdx)
          val firstWorkspaceIdx = workspaces.head //TODO: crashes
          val firstWorkspaceId = graph.nodeIds(workspaces.head)

          val topLevelStages = graph.childrenIdx(firstWorkspaceIdx).filter(idx => graph.nodes(idx).role == NodeRole.Stage)
          val allStages:ArraySet = {
            val stages = ArraySet.create(graph.size)
            topLevelStages.foreachElement(stages.add)
            algorithm.depthFirstSearchAfterStartsWithContinue(starts = topLevelStages.toArray, graph.childrenIdx, {idx =>
              val isStage = graph.nodes(idx).role == NodeRole.Stage
              if(isStage) stages += idx
              isStage
            })
            stages
          }

          val inboxTasks: ArraySet  = {
            val inboxTasks = ArraySet.create(graph.size)
            graph.childrenIdx.foreachElement(firstWorkspaceIdx){childIdx =>
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

          val sortedTopLevelColumns:Seq[Tree] = TaskOrdering.constructOrderingOf[Tree](graph, firstWorkspaceId, topLevelStageTrees, (t: Tree) => t.node.id)
          val assigneInbox = inboxTasks.map(graph.nodeIds)

          div(
            cls := "kanbanview",

            overflow.auto,

            Styles.flex,
            alignItems.flexStart,
            renderInboxColumn(state, firstWorkspaceId, path = Nil, assigneInbox, activeAddCardFields, selectedNodeIds),
            div(
              cls := s"kanbancolumnarea",
              keyed,
              Styles.flexStatic,

              Styles.flex,
              alignItems.flexStart,
              sortedTopLevelColumns.map(tree => renderStageTree(state, graph, tree, parentId = pageParentId, pageParentId = pageParentId, path = Nil, activeAddCardFields, selectedNodeIds, isTopLevel = true)),

              registerDragContainer(state, DragContainer.Kanban.ColumnArea(pageParentId, sortedTopLevelColumns.map(_.node.id))),
            ),
            newColumnArea(state, pageParentId, newColumnFieldActive).apply(Styles.flexStatic),
          )
        }
      },
      Rx {
        val page = state.page()
        val graph = state.graph()
        page.parentId.map { pageParentId =>
          val pageParentIdx = graph.idToIdx(pageParentId)
          val workspaces = graph.workspacesForParent(pageParentIdx)
          val firstWorkspaceIdx = workspaces.head
          val firstWorkspaceId = graph.nodeIds(workspaces.head)
          if(tagBarExpanded())
            tagList(state, firstWorkspaceId, newTagFieldActive, Some(tagBarExpanded)).apply(overflow.auto)
          else
            VDomModifier(
              position.relative,
              div(
                "Show Tags",
                onClick.stopPropagation(true) --> tagBarExpanded,
                cursor.pointer,

                position.absolute,
                top := "0",
                right := "0",
                backgroundColor := CommonStyles.sidebarBgColor,
                color.white,
                borderBottomLeftRadius := "5px",
                padding := "5px",
              )
            )
        }
      }
    )
  }

  def tagList(
    state: GlobalState,
    workspaceId: NodeId,
    newTagFieldActive: Var[Boolean] = Var(false),
    tagBarExpanded: Option[Var[Boolean]] = None,
  )(implicit ctx:Ctx.Owner) = {
    val tags:Rx[Seq[Tree]] = Rx {
      val graph = state.graph()
      val workspaceIdx = graph.idToIdx(workspaceId)
      graph.tagChildrenIdx(workspaceIdx).map(tagIdx => graph.roleTree(root = tagIdx, NodeRole.Tag))
    }
    def renderTagTree(trees:Seq[Tree])(implicit ctx: Ctx.Owner): VDomModifier = trees.map {
      case Tree.Leaf(node) =>
        checkboxNodeTag(state, node)
      case Tree.Parent(node, children) =>
        VDomModifier(
          checkboxNodeTag(state, node),
          div(
            paddingLeft := "10px",
            renderTagTree(children)
          )
        )
    }

    div(
      width := "180px",
      paddingLeft := "10px",
      paddingRight := "10px",
      paddingBottom := "10px",
      backgroundColor := CommonStyles.sidebarBgColor,

      div(
        Styles.flex,
        justifyContent.flexEnd,
        color.white,
        tagBarExpanded.map(tagBarExpanded => closeButton(paddingRight := "0px", onClick.stopPropagation(false) --> tagBarExpanded)),
      ),

      Rx { renderTagTree(tags()) },

      addTagField(state, parentId = workspaceId, workspaceId = workspaceId, newTagFieldActive = newTagFieldActive).apply(marginTop := "10px"),

      drag(target = DragItem.TagBar(workspaceId)),
      registerDragContainer(state),
    )
  }

  private def addTagField(
    state: GlobalState,
    parentId: NodeId,
    workspaceId: NodeId,
    newTagFieldActive: Var[Boolean],
  )(implicit ctx: Ctx.Owner): VNode = {
    def submitAction(str:String) = {
      val createdNode = Node.MarkdownTag(str)
      val change = GraphChanges.addNodeWithParent(createdNode, parentId :: Nil)

      state.eventProcessor.changes.onNext(change)
    }

    def blurAction(v:String): Unit = {
      if(v.isEmpty) newTagFieldActive() = false
    }

    val placeHolder = ""

    div(
      cls := "kanbanaddnodefield",
      keyed(parentId),
      Rx {
        if(newTagFieldActive())
          inputRow(state,
            submitAction,
            autoFocus = true,
            blurAction = Some(blurAction),
            placeHolderMessage = Some(placeHolder),
            submitIcon = freeSolid.faPlus,
          )
        else
          div(
            cls := "kanbanaddnodefieldtext",
            "+ Add Tag",
            color := "rgba(255,255,255,0.62)",
            onClick foreach { newTagFieldActive() = true }
          )
      }
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
    children: Seq[NodeId],
    activeAddCardFields: Var[Set[List[NodeId]]],
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VNode = {
    val columnColor = BaseColors.kanbanColumnBg.copy(h = hue(workspaceId)).toHex
    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)
    val sortedChildren = TaskOrdering.constructOrderingOf[NodeId](state.graph.now, workspaceId, children, identity)

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
        registerDragContainer(state, DragContainer.Kanban.Inbox(workspaceId, sortedChildren)),
        sortedChildren.map(nodeId => renderCard(state, state.graph.now.nodesById(nodeId), parentId = workspaceId, pageParentId = workspaceId, path = path, selectedNodeIds,activeAddCardFields)),
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

    /// Given a node, allow its content to overflow, even if a parent has overflow: hidden
    /** see: https://stackoverflow.com/a/22927412
      * How it works:
      * Wraps the node with two divs, one with position:relative, one with position:absolute, then
      * sets position:fixed on the passed node.
      * Only problem: you need to set a width on the outermost div. */
    def overrideOverflowCutoff(node : VNode, widthPx : Option[Int] = None) = {
      div(position.relative,
          (!widthPx.isEmpty).ifTrue[VDomModifier](width := s"${widthPx.get}px"),
          div(position.absolute,
              node(position.fixed)))
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
      val createSubtasks = menuItem(
        "Create subtasks", "Create subtasks", Icons.tasks,
        onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Tasks)) --> state.viewConfig)
      val startConversation = menuItem("Start conversation", "Start conversation about this card", Icons.conversation,
        onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Conversation)) --> state.viewConfig)
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
        DevOnly(ItemProperties.manageProperties(
                  state, node.id,
                  propertiesBuilder(compressed)))
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
      val moreMenu = overrideOverflowCutoff(
        div(
          // ideally this would be always visible, but since the outer div does no longer hide overflow,
          // the ellipsis are always visible, even if they are overlapped by the „Add card“ area
          //visibility.visible, 
          cls := "ui icon left labeled fluid dropdown",
          Icons.ellipsisV,
          cursor.pointer,
          UI.popup := "More",
          zIndex := ZIndex.overlay,                               // leave zIndex here since otherwise it gets overwritten
          Elements.withoutDefaultPassiveEvents,                   // revert default passive events, else dropdown is not working
          onDomMount.asJquery.foreach(_.dropdown("hide")),   // https://semantic-ui.com/modules/dropdown.html#/usage
          div(
            cls := "menu",
            div(cls := "header", "Context menu", cursor.default),
            moreMenuItems.map(_(false))
          ),

        ),
        // we pass the width manually to make the pressable area big enough
        widthPx = Some(10)
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
              onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Tasks)) --> state.viewConfig,
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
      overflow.hidden, // fixes unecessary scrollbar, when card has assignment

      Components.automatedNodesOfNode(state, node),
      cardTags(state, node.id),
      cardProperties(state, node.id),
      Rx { VDomModifier.ifTrue(!isPlainCard())(cardFooter) },
      Rx {
        val graph = state.graph()
        val userId = state.user().id
        VDomModifier.ifTrue(graph.isExpanded(userId, node.id))(
          subCards(graph)
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
