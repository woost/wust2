package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{Feature, _}
import wust.webApp.dragdrop.DragContainer
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}

object ListView {
  import SharedViewElements._

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val marginBottomHack = VDomModifier(
      position.relative,
      div(position.absolute, top := "100%", width := "1px", height := "10px") // https://www.brunildo.org/test/overscrollback.html
    )

    div(
      overflow.auto,
      padding := "5px",
      id := "tutorial-checklist",
      fieldAndList(
        focusState,
        TraverseState(focusState.focusedId),
        inOneLine = true,
        isCompact = false,
        lastElementModifier = marginBottomHack,
      ),
      newSectionArea(focusState)
    )
  }

  def fieldAndList(
    focusState: FocusState,
    traverseState: TraverseState,
    inOneLine: Boolean,
    isCompact:Boolean,
    lastElementModifier: VDomModifier = VDomModifier.empty,
    showInputField: Boolean = true,
  )(implicit ctx: Ctx.Owner):VNode = {
    div(
      keyed,

      VDomModifier.ifTrue(showInputField)(addListItemInputField( focusState, autoFocusInsert = !focusState.isNested)),
      renderInboxColumn( focusState, traverseState, inOneLine, isCompact),
      renderToplevelColumns( focusState, traverseState, inOneLine, isCompact, showInputField = showInputField)
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
    showInputField: Boolean,
  )(implicit ctx: Ctx.Owner): VNode = {
    val columns = Rx {
      val graph = GlobalState.graph()
      KanbanData.columns(graph, traverseState)
    }

    div(
      Rx {
        VDomModifier(
          columns().map { columnId =>
            renderColumn( focusState, traverseState, columnId = columnId, inOneLine = inOneLine, isCompact = isCompact, showInputField = showInputField)
          },
          registerDragContainer( DragContainer.Kanban.ColumnArea(focusState.focusedId, columns())),
        )
      }
    )
  }

  private def renderInboxColumn(
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
          columnId = nodeId,
          inOneLine = inOneLine,
          isCompact = isCompact,
          showInputField = false,
        )
      case _ => VDomModifier.empty
    }
  }

  private def renderColumn(
    focusState: FocusState,
    traverseState: TraverseState,
    columnId: NodeId,
    inOneLine:Boolean,
    isCompact: Boolean,
    showInputField: Boolean,
  ): VNode = {
    div.thunkStatic(columnId.hashCode)(Ownable { implicit ctx =>
      val isExpanded = Rx {
        val graph = GlobalState.graph()
        val userId = GlobalState.userId()
        graph.isExpanded(userId, columnId).getOrElse(true)
      }

      val stage = Rx {
        GlobalState.graph().nodesByIdOrThrow(columnId)
      }

      val isDone = Rx {
        GlobalState.graph().isDoneStage(stage())
      }

      val nextTraverseState = traverseState.step(columnId)

      val children = Rx {
        val graph = GlobalState.graph()
        KanbanData.columnNodes(graph, nextTraverseState)
      }

      val expandCollapseStage = div(
        cls := "listview-expand-collapse-stage",
        renderExpandCollapseButton( columnId, isExpanded, alwaysShow = true).map(_.apply(
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
          VDomModifier.ifTrue(showInputField)(addListItemInputField( focusState, autoFocusInsert = false, targetSection = Some(columnId))),
          div(
            cls := "tasklist",
            VDomModifier.ifTrue(isCompact)(cls := "compact"),
            flexDirection.columnReverse,

            Rx {
              VDomModifier(
                registerDragContainer( DragContainer.Kanban.Column(columnId, children().map(_._1), workspace = focusState.focusedId)),
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
      }

      VDomModifier(
        marginTop := "10px",
        expandCollapseStage,
        tasklist
      )
    })
  }

  private def addListItemInputField(focusState: FocusState, autoFocusInsert: Boolean, targetSection:Option[NodeId] = None)(implicit ctx: Ctx.Owner) = {
    def submitAction(userId: UserId)(sub: InputRow.Submission) = {
      val createdNode = Node.MarkdownTask(sub.text)
      val addSectionParent = GraphChanges.addToParents(ChildId(createdNode.id), ParentId(targetSection))
      val addNode = GraphChanges.addNodeWithParent(createdNode, ParentId(focusState.focusedId))
      val addTags = ViewFilter.addCurrentlyFilteredTags( createdNode.id)
      GlobalState.submitChanges(addNode merge addTags merge addSectionParent merge sub.changes(createdNode.id))
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

  val addSectionText = "Add Section"
  private def newSectionArea(focusState: FocusState)(implicit ctx: Ctx.Owner) = {
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

    div(
      Styles.flex,
      justifyContent.flexEnd,
      paddingTop := "10px",
      paddingRight := "7px",
      keyed,
      Rx {
        if(fieldActive()) {
          InputRow(
            Some(focusState),
            submitAction,
            autoFocus = true,
            blurAction = Some(blurAction),
            placeholder = Placeholder.newSection,
            showSubmitIcon = false,
            submitOnEnter = true,
            showMarkdownHelp = false
          ).apply(
            width := "300px",
          )
        } else div(
          onClick.stopPropagation.use(true) --> fieldActive,
          cls := "listviewaddsectiontext",
          color := "rgba(0,0,0,0.62)",
          s"+ $addSectionText",
        )
      },
    )
  }
}
