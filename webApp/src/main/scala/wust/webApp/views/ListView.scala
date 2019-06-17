package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.dragdrop.DragContainer
import wust.webApp.state.{FocusState, GlobalState, Placeholder, TraverseState}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer

object ListView {
  import SharedViewElements._

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val marginBottomHack = VDomModifier(
      position.relative,
      div(position.absolute, top := "100%", width := "1px", height := "10px") // https://www.brunildo.org/test/overscrollback.html
    )

    fieldAndList(
      state,
      focusState,
      TraverseState(focusState.focusedId),
      inOneLine = true,
      isCompact = false,
      lastElementModifier = marginBottomHack,
    ).apply(
      overflow.auto,
      padding := "5px",
      flexGrow := 2,
    )
  }

  def fieldAndList(
    state: GlobalState,
    focusState: FocusState,
    traverseState: TraverseState,
    inOneLine: Boolean,
    isCompact:Boolean,
    lastElementModifier: VDomModifier = VDomModifier.empty,
  )(implicit ctx: Ctx.Owner):VNode = {
    div(
      keyed,

      addListItemInputField(state, focusState.focusedId, autoFocusInsert = !focusState.isNested),
      renderInboxColumn(state, focusState, traverseState, inOneLine, isCompact),
      renderToplevelColumns(state, focusState, traverseState, inOneLine, isCompact)
        .apply(lastElementModifier),
    )
  }

  private def renderNodeCard(
    state: GlobalState,
    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    inOneLine:Boolean,
    isCompact: Boolean,
    isDone: Boolean
  ): VNode = {
    TaskNodeCard.renderThunk(
      state = state,
      focusState = focusState,
      traverseState = traverseState,
      nodeId = nodeId,
      showCheckbox = true,
      isDone = isDone,
      inOneLine = inOneLine,
      isCompact = isCompact,
    )
  }

  private def renderToplevelColumns(
    state: GlobalState,
    focusState: FocusState,
    traverseState: TraverseState,
    inOneLine: Boolean,
    isCompact:Boolean,
  )(implicit ctx: Ctx.Owner): VNode = {
    val columns = Rx {
      val graph = state.graph()
      KanbanData.columns(graph, traverseState)
    }

    div(
      Rx {
        VDomModifier(
          columns().map { columnId =>
            renderColumn(state, focusState, traverseState, nodeId = columnId, inOneLine = inOneLine, isCompact = isCompact)
          },
          registerDragContainer(state, DragContainer.Kanban.ColumnArea(focusState.focusedId, columns())),
        )
      }
    )
  }

  private def renderInboxColumn(
    state: GlobalState,
    focusState: FocusState,
    traverseState: TraverseState,
    inOneLine:Boolean,
    isCompact:Boolean
  )(implicit ctx: Ctx.Owner): VNode = {
    val children = Rx {
      val graph = state.graph()
      KanbanData.inboxNodes(graph, traverseState)
    }

    //      registerDragContainer(state, DragContainer.Kanban.ColumnArea(focusState.focusedId, inboxIds)),
    div(
      cls := "tasklist",
      VDomModifier.ifTrue(isCompact)(cls := "compact"),
      flexDirection.columnReverse,

      Rx {
        VDomModifier(
          registerDragContainer(state, DragContainer.Kanban.Inbox(focusState.focusedId, children())),

          children().map { nodeId =>
            renderNodeCard(state, focusState, traverseState, nodeId = nodeId, isDone = false, inOneLine = inOneLine, isCompact = isCompact)
          }
        )
      }
    )
  }

  private def renderTaskOrStage(
    state: GlobalState,
    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    nodeRole: NodeRole,
    inOneLine: Boolean,
    isCompact: Boolean,
    parentIsDone: Boolean,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    nodeRole match {
      case NodeRole.Task =>
        renderNodeCard(
          state,
          focusState,
          traverseState,
          nodeId = nodeId,
          inOneLine = inOneLine,
          isCompact = isCompact,
          isDone = parentIsDone
        )
      case NodeRole.Stage => 
        renderColumn(
          state,
          focusState,
          traverseState,
          nodeId = nodeId,
          inOneLine = inOneLine,
          isCompact = isCompact
        )
      case _ => VDomModifier.empty
    }
  }

  private def renderColumn(
    state: GlobalState,
    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    inOneLine:Boolean,
    isCompact: Boolean
  ): VNode = {
    div.thunkStatic(nodeId.hashCode)(Ownable { implicit ctx =>
      val isExpanded = Rx {
        val graph = state.graph()
        val user = state.user()
        graph.isExpanded(user.id, nodeId).getOrElse(true)
      }

      val stage = Rx {
        state.graph().nodesByIdOrThrow(nodeId)
      }

      val isDone = Rx {
        state.graph().isDoneStage(stage())
      }

      val nextTraverseState = traverseState.step(nodeId)

      val children = Rx {
        val graph = state.graph()
        KanbanData.columnNodes(graph, nextTraverseState)
      }

      val expandCollapseStage = div(
        fontSize.larger,
        paddingLeft := "5px",
        opacity := 0.6,
        renderExpandCollapseButton(state, nodeId, isExpanded, alwaysShow = true).map(_.apply(
            Styles.flex,
            alignItems.center,
            Rx{
              renderNodeData(stage().data)
            },

          )
        ),
      )

      val tasklist = Rx {
        VDomModifier.ifTrue(isExpanded())(
          (
            div(
              cls := "tasklist",
              VDomModifier.ifTrue(isCompact)(cls := "compact"),
              flexDirection.columnReverse,

              Rx {
                VDomModifier(
                  registerDragContainer(state, DragContainer.Kanban.Column(nodeId, children().map(_._1), workspace = focusState.focusedId)),
                  children().map {
                    case (id, role) =>
                      renderTaskOrStage(
                        state,
                        focusState,
                        nextTraverseState,
                        nodeId = id,
                        nodeRole = role,
                        parentIsDone = isDone(),
                        inOneLine = inOneLine,
                        isCompact = isCompact
                      )
                  }
                )
              }
            )
          )
        )
      }

      VDomModifier(
        marginTop := "10px",
        expandCollapseStage,
        tasklist
      )
    })
  }

  private def addListItemInputField(state: GlobalState, focusedNodeId: NodeId, autoFocusInsert: Boolean)(implicit ctx: Ctx.Owner) = {
    def submitAction(userId: UserId)(str: String) = {
      val createdNode = Node.MarkdownTask(str)
      val addNode = GraphChanges.addNodeWithParent(createdNode, ParentId(focusedNodeId))
      val addTags = ViewFilter.addCurrentlyFilteredTags(state, createdNode.id)
      state.eventProcessor.changes.onNext(addNode merge addTags)
    }

    div(
      Rx {
        InputRow(state, submitAction(state.userId()),
          preFillByShareApi = true,
          autoFocus = !BrowserDetect.isMobile && autoFocusInsert,
          placeholder = Placeholder.newTask,
          submitOnEnter = true,
          showSubmitIcon = false,
        ).apply(Styles.flexStatic)
      }
    )
  }

}
