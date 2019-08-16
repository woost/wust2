package wust.webApp.views

import wust.webApp.state.FeatureState
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}
import wust.css.Styles
import wust.graph._
import wust.ids.{Feature, _}
import wust.webApp.dragdrop.DragContainer
import wust.webApp.state.{FocusState, GlobalState, Placeholder, TraverseState}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer

object ListView {
  import SharedViewElements._

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val marginBottomHack = VDomModifier(
      position.relative,
      div(position.absolute, top := "100%", width := "1px", height := "10px") // https://www.brunildo.org/test/overscrollback.html
    )

    fieldAndList(
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
    focusState: FocusState,
    traverseState: TraverseState,
    inOneLine: Boolean,
    isCompact:Boolean,
    lastElementModifier: VDomModifier = VDomModifier.empty,
  )(implicit ctx: Ctx.Owner):VNode = {
    div(
      keyed,

      addListItemInputField( focusState, autoFocusInsert = !focusState.isNested),
      renderInboxColumn( focusState, traverseState, inOneLine, isCompact),
      renderToplevelColumns( focusState, traverseState, inOneLine, isCompact)
        .apply(lastElementModifier),
    )
  }

  private def renderNodeCard(
    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    inOneLine:Boolean,
    isCompact: Boolean,
    isDone: Boolean
  ): VNode = {
    TaskNodeCard.renderThunk(
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
    focusState: FocusState,
    traverseState: TraverseState,
    inOneLine: Boolean,
    isCompact:Boolean,
  )(implicit ctx: Ctx.Owner): VNode = {
    val columns = Rx {
      val graph = GlobalState.graph()
      KanbanData.columns(graph, traverseState)
    }

    div(
      Rx {
        VDomModifier(
          columns().map { columnId =>
            renderColumn( focusState, traverseState, nodeId = columnId, inOneLine = inOneLine, isCompact = isCompact)
          },
          registerDragContainer( DragContainer.Kanban.ColumnArea(focusState.focusedId, columns())),
        )
      }
    )
  }

  def renderInboxColumn(
    focusState: FocusState,
    traverseState: TraverseState,
    inOneLine:Boolean,
    isCompact:Boolean
  )(implicit ctx: Ctx.Owner): VNode = {
    val children = Rx {
      val graph = GlobalState.graph()
      KanbanData.inboxNodes(graph, traverseState)
    }

    //      registerDragContainer( DragContainer.Kanban.ColumnArea(focusState.focusedId, inboxIds)),
    div(
      cls := "tasklist",
      VDomModifier.ifTrue(isCompact)(cls := "compact"),
      flexDirection.columnReverse,

      Rx {
        VDomModifier(
          registerDragContainer( DragContainer.Kanban.Inbox(focusState.focusedId, children())),

          children().map { nodeId =>
            renderNodeCard( focusState, traverseState, nodeId = nodeId, isDone = false, inOneLine = inOneLine, isCompact = isCompact)
          }
        )
      }
    )
  }

  private def renderTaskOrStage(
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

          focusState,
          traverseState,
          nodeId = nodeId,
          inOneLine = inOneLine,
          isCompact = isCompact,
          isDone = parentIsDone
        )
      case NodeRole.Stage =>
        renderColumn(

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

    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    inOneLine:Boolean,
    isCompact: Boolean
  ): VNode = {
    div.thunkStatic(nodeId.hashCode)(Ownable { implicit ctx =>
      val isExpanded = Rx {
        val graph = GlobalState.graph()
        val user = GlobalState.user()
        graph.isExpanded(user.id, nodeId).getOrElse(true)
      }

      val stage = Rx {
        GlobalState.graph().nodesByIdOrThrow(nodeId)
      }

      val isDone = Rx {
        GlobalState.graph().isDoneStage(stage())
      }

      val nextTraverseState = traverseState.step(nodeId)

      val children = Rx {
        val graph = GlobalState.graph()
        KanbanData.columnNodes(graph, nextTraverseState)
      }

      val expandCollapseStage = div(
        fontSize.larger,
        paddingLeft := "5px",
        opacity := 0.6,
        renderExpandCollapseButton( nodeId, isExpanded, alwaysShow = true).map(_.apply(
            Styles.flex,
            alignItems.center,
            Rx{
              renderNodeData( stage())
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
                  registerDragContainer( DragContainer.Kanban.Column(nodeId, children().map(_._1), workspace = focusState.focusedId)),
                  children().map {
                    case (id, role) =>
                      renderTaskOrStage(

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

  private def addListItemInputField(focusState: FocusState, autoFocusInsert: Boolean)(implicit ctx: Ctx.Owner) = {
    def submitAction(userId: UserId)(sub: InputRow.Submission) = {
      val createdNode = Node.MarkdownTask(sub.text)
      val addNode = GraphChanges.addNodeWithParent(createdNode, ParentId(focusState.focusedId))
      val addTags = ViewFilter.addCurrentlyFilteredTags( createdNode.id)
      GlobalState.submitChanges(addNode merge addTags merge sub.changes(createdNode.id))
      focusState.view match {
        case View.List =>
          val parentIsTask = GlobalState.graph.now.nodesById(focusState.focusedId).exists(_.role == NodeRole.Task)
          val parentIsPage = focusState.focusedId == focusState.contextParentId
          val creatingNestedTask = parentIsTask && !parentIsPage
          if(creatingNestedTask)
            FeatureState.use(Feature.CreateNestedTaskInChecklist)
          else
            FeatureState.use(Feature.CreateTaskInChecklist)

        case View.Kanban =>
          val parentIsTask = GlobalState.graph.now.nodesById(focusState.focusedId).exists(_.role == NodeRole.Task)
          val parentIsPage = focusState.focusedId == focusState.contextParentId
          val creatingNestedTask = parentIsTask && !parentIsPage
          if(creatingNestedTask)
            FeatureState.use(Feature.CreateNestedTaskInKanban)
          else {
            // in the current implementation this case wouldn't happen,
            // since kanban columns have their own input field.
            // ListView is not used for Columns, only inside expanded tasks.
            FeatureState.use(Feature.CreateTaskInKanban) 
          }

        case _ =>
      }
    }

    div(
      Rx {
        InputRow( Some(focusState), submitAction(GlobalState.userId()),
          preFillByShareApi = true,
          autoFocus = !BrowserDetect.isMobile && autoFocusInsert,
          placeholder = Placeholder.newTask,
          submitOnEnter = true,
          showSubmitIcon = false,
        ).apply(Styles.flexStatic, margin := "3px")
      }
    )
  }

}
