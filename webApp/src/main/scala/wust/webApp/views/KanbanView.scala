package wust.webApp.views

import acyclic.file
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable, UI}
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util.collection._
import wust.webApp.Icons
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.state._
import wust.webUtil.Elements
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer

object KanbanView {

  def apply(focusState: FocusState, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VNode = {

    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    val traverseState = TraverseState(focusState.focusedId)

    val node = Rx {
      val g = GlobalState.rawGraph()
      g.nodesById(focusState.focusedId)
    }

    val kanbanSettings = node.map(_.flatMap(_.settings).fold(KanbanSettings.default)(_.kanbanOrDefault))

    val updateKanbanSettings: (KanbanSettings => KanbanSettings) => Unit = f => node.now.foreach {
      case node: Node.Content =>
        val newNode = node.updateSettings(_.updateKanban(f))
        GlobalState.submitChanges(GraphChanges.addNode(newNode))
      case _ => ()
    }

    div(
      keyed,
      cls := "kanbanview",
      height := "100%",
      overflow.auto,
      Styles.flex,
      alignItems.flexStart,

      kanbanSettings.map { settings =>
        if (settings.hideUncategorized) minimizedInboxColumn(traverseState, updateKanbanSettings)
        else renderInboxColumn(focusState, traverseState, viewRender, selectedNodeIds, updateKanbanSettings)
      },

      // inbox is separated, because it cannot be reordered. The others are in a sortable container
      renderToplevelColumns(focusState, traverseState, viewRender, selectedNodeIds),

      newColumnArea(focusState).apply(Styles.flexStatic),
    )
  }

  private def renderTaskOrStage(
    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    nodeRole: NodeRole,
    viewRender: ViewRenderLike,
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    nodeRole match {
      case NodeRole.Task => TaskNodeCard.renderThunk( focusState, traverseState, nodeId, selectedNodeIds, compactChildren = true)
      case NodeRole.Stage => renderColumn( focusState, traverseState, nodeId, viewRender = viewRender, selectedNodeIds, isTopLevel = isTopLevel)
      case _ => VDomModifier.empty
    }
  }

  private def renderToplevelColumns(
    focusState: FocusState,
    traverseState: TraverseState,
    viewRender: ViewRenderLike,
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val columns = Rx {
      val graph = GlobalState.graph()
      KanbanData.columns(graph, traverseState)
    }

    div(
      cls := s"kanbancolumnarea",
      Styles.flexStatic,
      Styles.flex,

      Rx {
        VDomModifier(
          columns().map { columnId =>
            renderColumn( focusState, traverseState, columnId, viewRender, selectedNodeIds, isTopLevel = true)
          },
          registerDragContainer( DragContainer.Kanban.ColumnArea(focusState.focusedId, columns())),
        )
      }
    )
  }

  private def minimizedInboxColumn(
    traverseState: TraverseState,
    updateKanbanSettings: (KanbanSettings => KanbanSettings) => Unit
  )(implicit ctx: Ctx.Owner): VNode = {
    val childrenCount = Rx {
      val graph = GlobalState.graph()
      KanbanData.inboxNodesCount(graph, traverseState)
    }

    div(
      Styles.flex,
      Styles.flexStatic,
      flexDirection.column,
      alignItems.center,
      onClickDefault.foreach { updateKanbanSettings(_.copy(hideUncategorized = false)) },
      UI.tooltip("right center") := "Expand Uncategorized",

      div(cls := "fa-fw", Icons.expand),

      childrenCount.map {
        case 0 => VDomModifier.empty
        case count => div(cls := "ui tiny label", count, padding := "5px")
      }
    )
  }

  private def renderInboxColumn(
    focusState: FocusState,
    traverseState: TraverseState,
    viewRender: ViewRenderLike,
    selectedNodeIds: Var[Set[NodeId]],
    updateKanbanSettings: (KanbanSettings => KanbanSettings) => Unit
  )(implicit ctx: Ctx.Owner): VNode = {
    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    val children = Rx {
      val graph = GlobalState.graph()
      KanbanData.inboxNodes(graph, traverseState)
    }

    val collapseButton = div(
      div(cls := "fa-fw", Icons.collapse),
      onClickDefault.foreach { updateKanbanSettings(_.copy(hideUncategorized = true)) },
      UI.tooltip("bottom center") := "Collapse"
    )

    div(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      cls := "kanbantoplevelcolumn",
      keyed,
      div(
        cls := "kanbancolumnheader",
        div(
          cls := "kanbancolumntitle kanban-uncategorized-title",
          div(cls := "markdown", p("Uncategorized")), // to be consistent with other column headers
        ),
        position.relative, // for buttonbar
        div(
          position.absolute,
          cls := "buttonbar",
          position.absolute, top := "0", right := "0",
          VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
          DragComponents.drag(DragItem.DisableDrag),
          Styles.flex,
          collapseButton,
          GraphChangesAutomationUI.settingsButton( focusState.focusedId, activeMod = visibility.visible, viewRender = viewRender),
        ),
      ),
      div(
        cls := "kanbancolumnchildren",
        cls := "tiny-scrollbar",
        scrollHandler.modifier,
        children.map { children =>
          VDomModifier(
            registerDragContainer( DragContainer.Kanban.Inbox(focusState.focusedId, children)),
            children.map(nodeId => TaskNodeCard.renderThunk( focusState, traverseState, nodeId, selectedNodeIds, compactChildren = true))
          )
        }
      ),
      addCardField( focusState, focusState.focusedId, scrollHandler)
    )
  }

  private def renderColumn(
    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    viewRender: ViewRenderLike,
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  ): VNode = div.thunk(nodeId.hashCode)(isTopLevel)(Ownable { implicit ctx =>
    val editable = Var(false)
    val node = Rx {
      val graph = GlobalState.graph()
      graph.nodesByIdOrThrow(nodeId)
    }
    val isExpanded = Rx {
      val graph = GlobalState.graph()
      val userId = GlobalState.userId()
      graph.isExpanded(userId, nodeId).getOrElse(true)
    }

    val nextTraverseState = traverseState.step(nodeId)

    val children = Rx {
      val graph = GlobalState.graph()
      KanbanData.columnNodes(graph, nextTraverseState)
    }
    val titleEditConfig = EditableContent.Config.cancelOnError.copy(saveDialogPosition = EditableContent.Position.Bottom)
    val columnTitle = Rx {
      editableNode( node(), editable, config = titleEditConfig, maxLength = Some(TaskNodeCard.maxLength))(ctx)(cls := "kanbancolumntitle")
    }

    val canWrite = NodePermission.canWrite( nodeId)

    val isDeletedNow = Rx {
      val g = GlobalState.rawGraph()
      g.isDeletedNow(nodeId, parentId = traverseState.parentId)
    }
    val buttonBar = div(
      cls := "buttonbar",
      VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
      Styles.flex,
      DragComponents.drag(DragItem.DisableDrag),
      Rx {
        VDomModifier.ifNot(editable())(
          div(
            div(
              cls := "fa-fw",
              if (isExpanded()) Icons.collapse else Icons.expand
            ),
            onClick.stopPropagation.useLazy(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(!isExpanded.now), GlobalState.userId.now)) --> GlobalState.eventProcessor.changes,
            cursor.pointer,
            UI.tooltip("bottom center") := "Collapse"
          ),
          VDomModifier.ifTrue(canWrite())(
            div(div(cls := "fa-fw", Icons.edit), onClick.stopPropagation.use(true) --> editable, cursor.pointer, UI.tooltip("bottom center") := "Edit"),
            div(
              div(cls := "fa-fw", if (isDeletedNow()) Icons.undelete else Icons.delete),
              onClick.stopPropagation foreach {
                if (isDeletedNow.now) {
                  val deleteChanges = GraphChanges.undelete(ChildId(nodeId), ParentId(traverseState.parentId))
                  GlobalState.submitChanges(deleteChanges)
                } else {
                  Elements.confirm("Delete this column?") {
                    val deleteChanges = GraphChanges.delete(ChildId(nodeId), ParentId(traverseState.parentId))
                    GlobalState.submitChanges(deleteChanges)
                    selectedNodeIds.update(_ - nodeId)
                  }
                }

                ()
              },
              cursor.pointer,
              UI.tooltip("bottom center") := (if (isDeletedNow()) "Recover" else "Archive")
            )
          ),
          //          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation.use(Page(nodeId)) --> GlobalState.page, cursor.pointer, UI.tooltip("bottom center") := "Zoom in"),
        )
      },

      GraphChangesAutomationUI.settingsButton( nodeId, activeMod = visibility.visible, viewRender = viewRender),
    )

    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)
    val kanbanColumnBgColor = BaseColors.kanbanColumnBg.copy(h = hue(nodeId)).toHex

    VDomModifier(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      if(isTopLevel) cls := "kanbantoplevelcolumn" else cls := "kanbansubcolumn",
      Rx{
        VDomModifier.ifNot(editable())(DragComponents.dragWithHandle(DragItem.Stage(nodeId))) // prevents dragging when selecting text
      },
      div(
        cls := "kanbancolumnheader",
        cls := "draghandle",
        backgroundColor := kanbanColumnBgColor,

        columnTitle,

        position.relative, // for buttonbar
        buttonBar(position.absolute, top := "0", right := "0"),
      ),
      Rx {
        if(isExpanded()) VDomModifier(
          div(
            cls := "kanbancolumnchildren",
            cls := "tiny-scrollbar",
            Rx {
              VDomModifier(
                registerDragContainer( DragContainer.Kanban.Column(nodeId, children().map(_._1), workspace = focusState.focusedId)),
                children().map { case (id, role) => renderTaskOrStage( focusState, nextTraverseState, nodeId = id, nodeRole = role, viewRender, selectedNodeIds) },
              )
            },
            scrollHandler.modifier,
          ),
        ) else VDomModifier(
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
              div(cls := "fa-fw", Icons.expand, UI.tooltip("bottom center") := "Expand"),
              onClick.stopPropagation.useLazy(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), GlobalState.userId.now)) --> GlobalState.eventProcessor.changes,
              cursor.pointer,
              paddingBottom := "7px",
            ),
            Rx {
              registerDragContainer( DragContainer.Kanban.Column(nodeId, children().map(_._1), workspace = focusState.focusedId))
              , // allows to drop cards on collapsed columns
            }
          )
        )
      },
      div(
        cls := "kanbancolumnfooter",
        Styles.flex,
        justifyContent.spaceBetween,
        addCardField( focusState, nodeId, scrollHandler).apply(width := "100%"),
        // stageCommentZoom,
      )
    )
  })

  val addCardText = "Add Card"
  private def addCardField(
    focusState: FocusState,
    nodeId: NodeId,
    scrollHandler: ScrollBottomHandler,
  )(implicit ctx: Ctx.Owner): VNode = {
    val active = Var[Boolean](false)
    active.foreach{ active =>
      if(active) scrollHandler.scrollToBottomInAnimationFrame()
    }

    def submitAction(userId: UserId)(sub: InputRow.Submission) = {
      val createdNode = Node.MarkdownTask(sub.text)
      val graph = GlobalState.graph.now
      val workspaces = graph.workspacesForParent(graph.idToIdxOrThrow(nodeId)).viewMap(idx => ParentId(graph.nodeIds(idx)))
      val addNode = GraphChanges.addNodeWithParent(createdNode, (workspaces :+ ParentId(nodeId)).distinct)
      val addTags = ViewFilter.addCurrentlyFilteredTags( createdNode.id)

      GlobalState.submitChanges(addNode merge addTags merge sub.changes(createdNode.id))
      FeatureState.use(Feature.CreateTaskInKanban)
    }

    def blurAction(v:String): Unit = {
      if(v.isEmpty) active() = false
    }

    div(
      cls := "kanbanaddnodefield",
      Rx {
        if(active())
          InputRow(
            Some(focusState),
            submitAction(GlobalState.userId()),
            autoFocus = true,
            blurAction = Some(blurAction),
            placeholder = Placeholder.newTask,
            submitOnEnter = true,
            showSubmitIcon = false,
            showMarkdownHelp = false
          )
        else
          div(
            cls := "kanbanaddnodefieldtext",
            s"+ $addCardText",
            color := "rgba(0,0,0,0.62)",
            onClick.stopPropagation.use(true) --> active
          )
      }
    )
  }

  val addColumnText = "Add Column"
  private def newColumnArea(focusState: FocusState)(implicit ctx: Ctx.Owner) = {
    val fieldActive = Var(false)
    def submitAction(sub: InputRow.Submission) = {
      val change = {
        val newStageNode = Node.MarkdownStage(sub.text)
        GraphChanges.addNodeWithParent(newStageNode, ParentId(focusState.focusedId)) merge sub.changes(newStageNode.id)
      }
      GlobalState.submitChanges(change)
      FeatureState.use(Feature.CreateColumnInKanban)
      //TODO: sometimes after adding new column, the add-column-form is scrolled out of view. Scroll, so that it is visible again
    }

    def blurAction(v:String): Unit = {
      if(v.isEmpty) fieldActive() = false
    }

    val marginRightHack = VDomModifier(
      position.relative,
      div(position.absolute, left := "100%", width := "10px", height := "1px") // https://www.brunildo.org/test/overscrollback.html
    )

    div(
      cls := s"kanbannewcolumnarea",
      keyed,
      Rx {
        if(fieldActive()) {
          InputRow(
            Some(focusState),
            submitAction,
            autoFocus = true,
            blurAction = Some(blurAction),
            placeholder = Placeholder.newStage,
            showSubmitIcon = false,
            submitOnEnter = true,
            showMarkdownHelp = false
          )
        } else div(
          onClick.stopPropagation.use(true) --> fieldActive,
          cls := "kanbanaddnodefieldtext",
          paddingTop := "10px",
          color := "rgba(0,0,0,0.62)",
          s"+ $addColumnText",
        )
      },
      marginRightHack
    )
  }

}
