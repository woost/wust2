package wust.webApp.views

import fontAwesome.freeSolid
import monix.reactive.Observer

import collection.breakOut
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.util.macros.InlineList
import wust.webApp.{BrowserDetect, Icons, ItemProperties, Ownable}
import wust.webApp.dragdrop.{DragContainer, DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusPreference, FocusState, GlobalState, NodePermission}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import algorithm.dfs

import scala.scalajs.js

object KanbanView {
  import SharedViewElements._

  val sortableAreaMinHeight = "10px"

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])

    div(
      cls := "kanbanview",
      height := "100%",
      overflow.auto,
      Styles.flex,
      alignItems.flexStart,

      renderInboxColumn(state, focusState, selectedNodeIds),

      renderToplevelColumns(state, focusState, selectedNodeIds),

      newColumnArea(state, focusState.focusedId).apply(Styles.flexStatic),
    )
  }

  private def renderTaskOrStage(
    state: GlobalState,
    focusState: FocusState,
    nodeId: NodeId,
    nodeRole: NodeRole,
    parentId: NodeId,
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    nodeRole match {
      case NodeRole.Task => TaskNodeCard.renderThunk(state, nodeId, parentId, focusState, selectedNodeIds)
      case NodeRole.Stage => renderColumn(state, nodeId, parentId, focusState, selectedNodeIds, isTopLevel = isTopLevel)
      case _ => VDomModifier.empty
    }
  }

  private def renderToplevelColumns(
    state: GlobalState,
    focusState: FocusState,
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val columns = Rx {
      val graph = state.graph()
      KanbanData.columns(graph, focusState.focusedId)
    }

    div(
      cls := s"kanbancolumnarea",
      Styles.flexStatic,
      Styles.flex,
      alignItems.flexStart,

      Rx {
        VDomModifier(
          columns().map { columnId =>
            renderColumn(state, columnId, focusState.focusedId, focusState, selectedNodeIds, isTopLevel = true)
          },
          registerDragContainer(state, DragContainer.Kanban.ColumnArea(focusState.focusedId, columns())),
        )
      }
    )
  }


  private def renderInboxColumn(
    state: GlobalState,
    focusState: FocusState,
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VNode = {
    val columnColor = BaseColors.kanbanColumnBg.copy(h = hue(focusState.focusedId)).toHex
    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    val children = Rx {
      val graph = state.graph()
      KanbanData.inboxNodes(graph, focusState.focusedId)
    }

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
          VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
          drag(DragItem.DisableDrag),
          Styles.flex,
          GraphChangesAutomationUI.settingsButton(state, focusState.focusedId, activeMod = visibility.visible),
        ),
      ),
      div(
        cls := "kanbancolumnchildren",
        scrollHandler.modifier,
        children.map { children =>
          VDomModifier(
            registerDragContainer(state, DragContainer.Kanban.Inbox(focusState.focusedId, children)),
            children.map(nodeId => TaskNodeCard.renderThunk(state, nodeId, parentId = focusState.focusedId, focusState = focusState, selectedNodeIds))
          )
        }
      ),
      addCardField(state, focusState.focusedId, scrollHandler, textColor = Some("rgba(0,0,0,0.62)"))
    )
  }

  private def renderColumn(
    state: GlobalState,
    nodeId: NodeId,
    parentId: NodeId,
    focusState: FocusState,
    selectedNodeIds:Var[Set[NodeId]],
    isTopLevel: Boolean = false,
  ): VNode = div.thunk(nodeId.hashCode)(isTopLevel)(Ownable { implicit ctx =>
    val editable = Var(false)
    val node = Rx {
      val graph = state.graph()
      graph.nodesByIdOrThrow(nodeId)
    }
    val isExpanded = Rx {
      val graph = state.graph()
      val user = state.user()
      graph.isExpanded(user.id, nodeId).getOrElse(true)
    }
    val children = Rx {
      val graph = state.graph()
      KanbanData.columnNodes(graph, nodeId)
    }
    val columnTitle = Rx {
      editableNode(state, node(), editable, maxLength = Some(TaskNodeCard.maxLength))(ctx)(cls := "kanbancolumntitle")
    }

    val canWrite = NodePermission.canWrite(state, nodeId)

    val buttonBar = div(
      cls := "buttonbar",
      VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
      Styles.flex,
      drag(DragItem.DisableDrag),
      Rx {
        VDomModifier.ifNot(editable())(
          div(
            div(
              cls := "fa-fw",
              if (isExpanded()) Icons.collapse else Icons.expand
            ),
            onClick.stopPropagation.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(!isExpanded.now), state.user.now.id)) --> state.eventProcessor.changes,
            cursor.pointer,
            UI.popup := "Collapse"
          ),
          VDomModifier.ifTrue(canWrite())(
            div(div(cls := "fa-fw", Icons.edit), onClick.stopPropagation(true) --> editable, cursor.pointer, UI.popup := "Edit"),
            div(
              div(cls := "fa-fw", Icons.delete),
              onClick.stopPropagation foreach {
                state.eventProcessor.changes.onNext(GraphChanges.delete(ChildId(nodeId), ParentId(parentId)))
                selectedNodeIds.update(_ - nodeId)
              },
              cursor.pointer, UI.popup := "Archive"
            )
          ),
          //          div(div(cls := "fa-fw", Icons.zoom), onClick.stopPropagation(Page(nodeId)) --> state.page, cursor.pointer, UI.popup := "Zoom in"),
        )
      },

      GraphChangesAutomationUI.settingsButton(state, nodeId, activeMod = visibility.visible),
    )

    val scrollHandler = new ScrollBottomHandler(initialScrollToBottom = false)

    VDomModifier(
      // sortable: draggable needs to be direct child of container
      cls := "kanbancolumn",
      if(isTopLevel) cls := "kanbantoplevelcolumn" else cls := "kanbansubcolumn",
      backgroundColor := BaseColors.kanbanColumnBg.copy(h = hue(nodeId)).toHex,
      Rx{
        VDomModifier.ifNot(editable())(dragWithHandle(DragItem.Stage(nodeId))) // prevents dragging when selecting text
      },
      div(
        cls := "kanbancolumnheader",
        cls := "draghandle",

        columnTitle,

        position.relative, // for buttonbar
        buttonBar(position.absolute, top := "0", right := "0"),
        //        onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
      ),
      Rx {
        if(isExpanded()) VDomModifier(
          div(
            cls := "kanbancolumnchildren",
            Rx {
              VDomModifier(
                registerDragContainer(state, DragContainer.Kanban.Column(nodeId, children().map(_._1), workspace = focusState.focusedId)),
                children().map { case (id, role) => renderTaskOrStage(state, focusState, nodeId = id, nodeRole = role, parentId = nodeId, selectedNodeIds) },
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
              div(cls := "fa-fw", Icons.expand, UI.popup := "Expand"),
              onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), state.user.now.id)) --> state.eventProcessor.changes,
              cursor.pointer,
              paddingBottom := "7px",
            ),
            Rx {
              registerDragContainer(state, DragContainer.Kanban.Column(nodeId, children().map(_._1), workspace = focusState.focusedId))
              , // allows to drop cards on collapsed columns
            }
          )
        )
      },
      div(
        cls := "kanbancolumnfooter",
        Styles.flex,
        justifyContent.spaceBetween,
        addCardField(state, nodeId, scrollHandler, None).apply(width := "100%"),
        // stageCommentZoom,
      )
    )
  })

  private def addCardField(
    state: GlobalState,
    parentId: NodeId,
    scrollHandler: ScrollBottomHandler,
    textColor:Option[String] = None,
  )(implicit ctx: Ctx.Owner): VNode = {
    val active = Var[Boolean](false)
    active.foreach{ active =>
      if(active) scrollHandler.scrollToBottomInAnimationFrame()
    }

    def submitAction(userId: UserId)(str:String) = {
      val createdNode = Node.MarkdownTask(str)
      val graph = state.graph.now
      val workspaces:Set[ParentId] = graph.workspacesForParent(graph.idToIdxOrThrow(parentId)).map(idx => ParentId(graph.nodeIds(idx)))(breakOut)
      val addNode = GraphChanges.addNodeWithParent(createdNode, workspaces + ParentId(parentId))
      val addTags = ViewFilter.addCurrentlyFilteredTags(state, createdNode.id)

      state.eventProcessor.changes.onNext(addNode merge addTags)
    }

    def blurAction(v:String): Unit = {
      if(v.isEmpty) active() = false
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
            onClick(true) --> active
          )
      }
    )
  }

  private def newColumnArea(state: GlobalState, focusedId:NodeId)(implicit ctx: Ctx.Owner) = {
    val fieldActive = Var(false)
    def submitAction(str:String) = {
      val change = {
        val newStageNode = Node.MarkdownStage(str)
        GraphChanges.addNodeWithParent(newStageNode, ParentId(focusedId))
      }
      state.eventProcessor.changes.onNext(change)
      //TODO: sometimes after adding new column, the add-column-form is scrolled out of view. Scroll, so that it is visible again
    }

    def blurAction(v:String): Unit = {
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
            ),
            showMarkdownHelp = false
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
