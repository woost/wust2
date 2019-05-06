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
import wust.webApp.{BrowserDetect, Icons}
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

      Rx {
        val graph = state.graph()

        val kanbanData = KanbanView.KanbanData.calculate(graph, focusState.focusedId)
        // val userTasks = graph.assignedNodesIdx(graph.idToIdx(state.user().id))

        val inboxNodes = kanbanData.inboxNodes.reverse
        val inboxIds = inboxNodes.map(_.id)
        VDomModifier(
          registerDragContainer(state, DragContainer.Kanban.ColumnArea(focusState.focusedId, inboxIds)),
          renderInboxColumn(state, focusState, inboxNodes, inboxIds),

          renderColumns(state, graph, focusState, parentId = focusState.focusedId, children = kanbanData.columnTree),
        )
      }
    )
  }

  def renderNodeCard(state: GlobalState, focusState: FocusState, parentId: NodeId, node: Node, isDone: Boolean)(implicit ctx: Ctx.Owner): VNode = {
    TaskNodeCard.render(
      state = state,
      node = node,
      parentId = parentId,
      focusState = focusState,
      showCheckbox = true,
      isDone = isDone,
      inOneLine = true
    ).apply(
      margin := "2px 4px",
    )
  }

  private def renderInboxColumn(state: GlobalState, focusState: FocusState, children: Seq[Node], childrenIds: Seq[NodeId])(implicit ctx: Ctx.Owner): VNode = {
    div(
      registerDragContainer(state, DragContainer.Kanban.Inbox(focusState.focusedId, childrenIds)),
      minHeight := KanbanView.sortableAreaMinHeight,

      Styles.flex,
      flexDirection.column,

      children.map { node =>
        renderNodeCard(state, focusState = focusState, parentId = focusState.focusedId, node = node, isDone = false)
      }
    )
  }

  private def renderColumns(state: GlobalState, graph: Graph, focusState: FocusState, parentId: NodeId, children: Seq[Tree], isDone: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
    div(
      children.collect {
        case Tree.Leaf(node) =>
          node.role match {
            case NodeRole.Stage => renderStageColumn(state, parentId, node, VDomModifier.empty)
            case NodeRole.Task => renderNodeCard(state, focusState, parentId = parentId, node = node, isDone = isDone)
            case _ => VDomModifier.empty
          }
        case Tree.Parent(node, children) =>
          node.role match {
            case NodeRole.Stage =>
              //TODO: better? prefill tree correctly?
              val cardChildren = graph.taskChildrenIdx(graph.idToIdx(node.id)).map(idx => Tree.Leaf(graph.nodes(idx)))
              val sortedChildren = TaskOrdering.constructOrderingOf[Tree](graph, node.id, children ++ cardChildren, (t: Tree) => t.node.id)
              val nodeIsDone = graph.isDoneStage(node)
              renderStageColumn(state, parentId, node, VDomModifier(
                VDomModifier.ifTrue(nodeIsDone)(opacity := 0.5),
                renderColumns(state, graph, focusState, parentId = node.id, children = sortedChildren, isDone = nodeIsDone).apply(
                  Styles.flex,
                  flexDirection.columnReverse,
                  minHeight := KanbanView.sortableAreaMinHeight,
                  registerDragContainer(state, DragContainer.Kanban.Column(node.id, sortedChildren.map(_.node.id), focusState.focusedId)),
                )
              ))
            case NodeRole.Task => renderNodeCard(state, focusState, parentId = parentId, node = node, isDone = isDone)
            case _ => VDomModifier.empty
          }
      }
    )
  }

  private def renderStageColumn(state: GlobalState, parentId: NodeId, stage: Node, innerModifier: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {
    val isExpanded = Rx {
      val graph = state.graph()
      val user = state.user()
      graph.isExpanded(user.id, stage.id).getOrElse(true)
    }
    div(
      paddingTop := "5px",
      div(
        height := "1px",
        backgroundColor := NodeColor.tagColor(stage.id).toHex,
        margin  := "15px 5px 3px 5px",
      ),
      div(
        Styles.flex,
        renderExpandCollapseButton(state, stage.id, isExpanded, alwaysShow = true),
        renderNodeData(stage.data).apply(paddingLeft := "5px")
      ),

      Rx {
        VDomModifier.ifTrue(isExpanded())(
          expandedNodeContentWithLeftTagColor(state, stage.id).apply(
            div(
              flexGrow := 2,
              paddingLeft := "5px",
              innerModifier,
            )
          )
        )
      }
    )
  }

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
        )(ctx)(Styles.flexStatic)
      }
    )
  }

}
