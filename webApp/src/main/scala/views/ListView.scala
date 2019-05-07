package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.webApp.{BrowserDetect, Icons, Ownable}
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.util.collection._

object ListView {
  import SharedViewElements._

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
      fieldAndList(state, focusState).apply(
        overflow.auto,
        padding := "5px",
        flexGrow := 2,
      )
  }

  def fieldAndList(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner) = {
    div(
      addListItemInputField(state, focusState.focusedId, autoFocusInsert = !focusState.isNested),

      renderInboxColumn(state, focusState),

      renderToplevelColumns(state, focusState),
    )
  }

  private def renderNodeCard(state: GlobalState, focusState: FocusState, parentId: NodeId, nodeId: NodeId, isDone: Boolean): VNode = {
    TaskNodeCard.renderThunk(
      state = state,
      nodeId = nodeId,
      parentId = parentId,
      focusState = focusState,
      showCheckbox = true,
      isDone = isDone,
      inOneLine = true
    ).apply(
      margin := "3px 4px",
    )
  }

  private def renderToplevelColumns(
    state: GlobalState,
    focusState: FocusState,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val columns = Rx {
      val graph = state.graph()
      KanbanData.columns(graph, focusState.focusedId)
    }

    div(
      Rx {
        VDomModifier(
          columns().map { columnId =>
            renderColumn(state, parentId = focusState.focusedId, nodeId = columnId, focusState = focusState)
          },
          registerDragContainer(state, DragContainer.Kanban.ColumnArea(focusState.focusedId, columns())),
        )
      }
    )
  }

  private def renderInboxColumn(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val children = Rx {
      val graph = state.graph()
      KanbanData.inboxNodes(graph, focusState.focusedId)
    }

    //      registerDragContainer(state, DragContainer.Kanban.ColumnArea(focusState.focusedId, inboxIds)),
    div(
      minHeight := KanbanView.sortableAreaMinHeight,

      Styles.flex,
      flexDirection.columnReverse,

      Rx {
        VDomModifier(
          registerDragContainer(state, DragContainer.Kanban.Inbox(focusState.focusedId, children())),

          children().map { nodeId =>
            renderNodeCard(state, focusState = focusState, parentId = focusState.focusedId, nodeId = nodeId, isDone = false)
          }
        )
      }
    )
  }

  private def renderTaskOrStage(
    state: GlobalState,
    focusState: FocusState,
    nodeId: NodeId,
    nodeRole: NodeRole,
    parentId: NodeId,
    parentIsDone: Boolean,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    nodeRole match {
      case NodeRole.Task => renderNodeCard(state, focusState, nodeId = nodeId, parentId = parentId, isDone = parentIsDone)
      case NodeRole.Stage => renderColumn(state, focusState, nodeId = nodeId, parentId = parentId)
      case _ => VDomModifier.empty
    }
  }

  private def renderColumn(state: GlobalState, focusState: FocusState, parentId: NodeId, nodeId: NodeId): VNode = div.static(nodeId.hashCode)(Ownable { implicit ctx =>
    val isExpanded = Rx {
      val graph = state.graph()
      val user = state.user()
      graph.isExpanded(user.id, nodeId).getOrElse(true)
    }

    val stage = Rx {
      state.graph().nodesById(nodeId)
    }

    val isDone = Rx {
      state.graph().isDoneStage(stage())
    }

    val children = Rx {
      val graph = state.graph()
      KanbanData.columnNodes(graph, nodeId)
    }

    VDomModifier(
      paddingTop := "5px",
      div(
        height := "1px",
        backgroundColor := NodeColor.tagColor(nodeId).toHex,
        margin := "15px 5px 3px 5px",
      ),
      div(
        Styles.flex,
        renderExpandCollapseButton(state, nodeId, isExpanded, alwaysShow = true),
        Rx {
          renderNodeData(stage().data).apply(paddingLeft := "5px")
        }
      ),

      Rx {
        VDomModifier.ifTrue(isExpanded())(
          expandedNodeContentWithLeftTagColor(state, nodeId).apply(
            div(
              flexGrow := 2,
              paddingLeft := "5px",
              Styles.flex,
              flexDirection.columnReverse,

              Rx {
                VDomModifier(
                  registerDragContainer(state, DragContainer.Kanban.Column(nodeId, children().map(_._1), workspace = focusState.focusedId)),
                  children().map { case (id, role) => renderTaskOrStage(state, focusState, nodeId = id, nodeRole = role, parentId = nodeId, parentIsDone = isDone()) }
                )
              }
            )
          )
        )
      }
    )
  })

  private def addListItemInputField(state: GlobalState, focusedNodeId: NodeId, autoFocusInsert: Boolean)(implicit ctx: Ctx.Owner) = {
    def submitAction(userId: UserId)(str: String) = {
      val createdNode = Node.MarkdownTask(str)
      val addNode = GraphChanges.addNodeWithParent(createdNode, ParentId(focusedNodeId))
      val addTags = ViewFilter.addCurrentlyFilteredTags(state, createdNode.id)
      state.eventProcessor.changes.onNext(addNode merge addTags)
    }

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add a task."

    div(
      Rx {
        inputRow(state, submitAction(state.user().id),
          preFillByShareApi = true,
          autoFocus = !BrowserDetect.isMobile && autoFocusInsert,
          placeHolderMessage = Some(placeHolder),
          submitIcon = freeSolid.faPlus
        ).apply(Styles.flexStatic)
      }
    )
  }

}
