package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole, UserId}
import wust.sdk.{BaseColors, NodeColor}
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.util.collection._

object ListView {
  import SharedViewElements._

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      justifyContent.spaceBetween,

      div(
        overflow.auto,
        padding := "10px",
        flexGrow := 2,

        addListItemInputField(state),

        Rx {
          val graph = state.graph()
          state.page().parentId.map { pageParentId =>

            val kanbanData = KanbanView.KanbanData.calculate(graph, pageParentId)
            // val userTasks = graph.assignedNodesIdx(graph.idToIdx(state.user().id))

            VDomModifier(
              renderInboxColumn(state, pageParentId = pageParentId, kanbanData.inboxNodes),

              renderColumns(state, graph, parentId = pageParentId, pageParentId = pageParentId, children = kanbanData.columnTree),
            )
          }

        }
      )
    )
  }

  def renderKanbanLikeCard(state: GlobalState, parentId: NodeId, pageParentId: NodeId, node: Node, isDone: Boolean)(implicit ctx: Ctx.Owner): VNode = {
    KanbanView.renderCard(
      state = state,
      node = node,
      parentId = parentId,
      pageParentId = pageParentId,
      path = Nil,
      selectedNodeIds = Var(Set.empty),
      activeAddCardFields = Var(Set.empty),
      showCheckbox = true,
      isDone = isDone,
      inOneLine = true
    ).apply(
      margin := "4px",
    )
  }

  private def renderInboxColumn(state: GlobalState, pageParentId: NodeId, children: Seq[Node])(implicit ctx: Ctx.Owner): VNode = {
    div(
      registerDragContainer(state, DragContainer.List(pageParentId, children.map(_.id))),

      Styles.flex,
      flexDirection.columnReverse,

      children.collect { case node if node.role == NodeRole.Task =>
        renderKanbanLikeCard(state, parentId = pageParentId, pageParentId = pageParentId, node = node, isDone = false)
      }
    )
  }

  private def renderColumns(state: GlobalState, graph: Graph, parentId: NodeId, pageParentId: NodeId, children: Seq[Tree], isDone: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
    div(
      children.collect {
        case Tree.Leaf(node) =>
          node.role match {
            case NodeRole.Stage => renderStageColumn(state, pageParentId, parentId, node, VDomModifier.empty)
            case NodeRole.Task => renderKanbanLikeCard(state, parentId = parentId, pageParentId = pageParentId, node = node, isDone = isDone)
            case _ => VDomModifier.empty
          }
        case Tree.Parent(node, children) =>
          node.role match {
            case NodeRole.Stage =>
              //TODO: better? prefill tree correctly?
              val cardChildren = graph.taskChildrenIdx(graph.idToIdx(node.id)).map(idx => Tree.Leaf(graph.nodes(idx)))
              val sortedChildren = TaskOrdering.constructOrderingOf[Tree](graph, node.id, children ++ cardChildren, (t: Tree) => t.node.id)
              val nodeIsDone = graph.isDoneStage(node)
              renderStageColumn(state, parentId, pageParentId, node, VDomModifier(
                VDomModifier.ifTrue(nodeIsDone)(opacity := 0.5),
                renderColumns(state, graph, parentId = parentId, pageParentId = pageParentId, children = sortedChildren, isDone = nodeIsDone).apply(
                  Styles.flex,
                  flexDirection.columnReverse,
                  registerDragContainer(state, DragContainer.List(node.id, sortedChildren.map(_.node.id))),
                )
              ))
            case NodeRole.Task => renderKanbanLikeCard(state, parentId = parentId, pageParentId = pageParentId, node = node, isDone = isDone)
            case _ => VDomModifier.empty
          }
      }
    )
  }

  private def renderStageColumn(state: GlobalState, parentId: NodeId, pageParentId: NodeId, stage: Node, innerModifier: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {
    val isExpanded = Rx {
      val graph = state.graph()
      val user = state.user()
      graph.isExpanded(user.id, stage.id)
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
        renderExpandCollapseButton(state, stage.id, isExpanded),
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

  private def addListItemInputField(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    def submitAction(userId: UserId)(str: String) = {
      val createdNode = Node.MarkdownTask(str)
      val change = GraphChanges.addNodeWithParent(createdNode, state.page.now.parentId)
      state.eventProcessor.changes.onNext(change)
    }

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add."

    val inputFieldFocusTrigger = PublishSubject[Unit]

    if(!BrowserDetect.isMobile) {
      state.page.triggerLater {
        inputFieldFocusTrigger.onNext(Unit) // re-gain focus on page-change
        ()
      }
    }

    div(
      Rx {
        inputRow(state, submitAction(state.user().id),
          preFillByShareApi = true,
          autoFocus = !BrowserDetect.isMobile,
          triggerFocus = inputFieldFocusTrigger,
          placeHolderMessage = Some(placeHolder),
          submitIcon = freeSolid.faPlus
        )(ctx)(Styles.flexStatic)
      }
    )
  }

}
