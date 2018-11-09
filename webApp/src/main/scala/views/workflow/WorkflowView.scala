package wust.webApp.views.workflow

import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util._
import wust.util.collection._
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.dateFns
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.{Placeholders}

import scala.collection.breakOut
import scala.scalajs.js

/// Provides a node with the context that it resides in (i.e. its relevant parents)
case class NodeContext(val nodeId: NodeId, val directParentIds : Set[NodeId])
object NodeContext
{
  implicit def toNodeId(node : NodeContext) = node.nodeId
}

/// All state manipulations go through this class.
/** Mostly used as a safety-abstraction over the ever-changing GlobalState. */
class WorkflowState(
  val globalState : GlobalState
)
{
  val selectedNodeIds : Var[Set[NodeId]] = Var(Set.empty)
  def parentIds(implicit ctx: Ctx.Owner) = Rx {
    globalState.page().parentIdSet
  }

  def page = globalState.page
  def user = globalState.user

  def pageStyle = globalState.pageStyle

  def nodeFromId(nodeId : NodeId)(implicit ctx: Ctx.Owner) = Rx {
    globalState.graph().nodesById(nodeId)
  }

  /// deletes the node as a child from the passed parents
  def delete(nodeId : NodeId, from : Set[NodeId]) = {
    globalState.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, from))
  }
  def delete(node : NodeContext) = {
    globalState.eventProcessor.changes.onNext(GraphChanges.delete(node.nodeId, node.directParentIds))
  }
  /// undeletes the node as a child from the passed parents
  def undelete(nodeId : NodeId, from : Set[NodeId]) = {
    globalState.eventProcessor.changes.onNext(GraphChanges.undelete(nodeId, from))
  }
  def undelete(node : NodeContext) = {
    globalState.eventProcessor.changes.onNext(GraphChanges.undelete(node.nodeId, node.directParentIds))
  }
  def isDeleted(node : NodeContext)(implicit ctx: Ctx.Owner) = Rx {
    globalState.graph().isDeletedNow(node.nodeId, node.directParentIds)
  }
  def isSelected(nodeId : NodeId)(implicit ctx: Ctx.Owner) = selectedNodeIds.map(_ contains nodeId)

  /// zooms on a node, effectively changing the page
  def zoomTo(nodeId : NodeId)(implicit ctx: Ctx.Owner) = {
    globalState.viewConfig.onNext(globalState.viewConfig.now.copy(page = Page(nodeId)))
  }

  def nodes(implicit ctx: Ctx.Owner) = Rx {
    val page = globalState.page()
    val fullGraph = globalState.graph()
    fullGraph.chronologicalNodesAscending.collect {
      case n: Node.Content if (fullGraph.isChildOfAny(n.id, page.parentIds)
                                 || fullGraph.isDeletedNow(n.id, page.parentIds)) =>
        n.id
    }
  }
}


object WorkflowView {
  // -- display options --
  val grouping = false
  val lastActiveEditable : Var[Option[NodeId]] = Var(None)

  def apply(globalState: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val state = new WorkflowState(globalState)
    val stylesOuterDiv = Seq[VDomModifier](
      cls := "workflow",
      Styles.flex,
      flexDirection.column,
      height := "100%",
      alignItems.stretch,
      alignContent.stretch,
    )
    div(
      stylesOuterDiv,
      activeEditableControls(state),
      renderTasksWrapper(state, innerRender = { renderTasks(state) }),
      Rx { inputField(state, state.page().parentIdSet).apply(keyed, Styles.flexStatic, padding := "3px") },
      // registerDraggableContainer(state),
    )
  }

  /// contains only simple html generating functions
  object components {
    val listWrapper = ul()
    val entryWrapper = li()

    def checkButton(nodeId : Set[NodeId], wrapper: VNode = entryWrapper) =
      wrapper("\u2713",
              cursor.pointer,
              onClick.stopPropagation --> sideEffect {println(s"checking node: ${nodeId}")})

    def indentButton(nodeId : Set[NodeId], wrapper: VNode = entryWrapper) =
      wrapper("\u2192",
              cursor.pointer,
              onClick.stopPropagation --> sideEffect {println(s"Indenting node: ${nodeId}")})

    def outdentButton(nodeId : Set[NodeId], wrapper: VNode = entryWrapper) =
      wrapper("\u2190",
              cursor.pointer,
              onClick.stopPropagation --> sideEffect {println(s"Outdenting node: ${nodeId}")})

    def deleteButton(state: WorkflowState, nodeId: Set[NodeId], directParentIds: Set[NodeId],
                     wrapper: VNode = entryWrapper)
                    (implicit ctx: Ctx.Owner) =
      wrapper(
        span(cls := "fa-fw", freeRegular.faTrashAlt),
        onClick.stopPropagation --> sideEffect {
          println(s"deleting ${nodeId} with parents: ${directParentIds}")
          nodeId.foreach { nodeId =>
            state.delete(nodeId, from = directParentIds)
          }
          state.selectedNodeIds.update(_ -- nodeId)
        },
        cursor.pointer,
        )

  }

  val showDebugInfo = true

  /// Controls to e.g indent or outdent the last edited entry
  /** TODO: indent/outdent requires an order between entries. */
  def activeEditableControls(state: WorkflowState)(implicit ctx: Ctx.Owner) = Rx {
    val nodes : Set[NodeId] = state.selectedNodeIds()
    (!nodes.isEmpty).ifTrueSeq[VDomModifier](
      Seq(components.listWrapper(
            cls := "activeEditableControls",
            showDebugInfo.ifTrue[VDomModifier](s"selected ${nodes.size}"),
            components.outdentButton(nodes),
            components.indentButton(nodes),
            components.deleteButton(state, nodes, state.parentIds(ctx)())),
          )
    )
  }


  /// wraps around the innerRender method
  def renderTasksWrapper(state: WorkflowState,
                         innerRender: => Seq[VDomModifier])(implicit ctx: Ctx.Owner): Seq[VDomModifier] = {
    val scrolledToBottom = PublishSubject[Boolean]

    Seq(
      padding := "20px 0 20px 20px",
      Rx {
        val nodes = state.nodes(ctx)()
        if(nodes.isEmpty)
          VDomModifier(emptyChatNotice)
        else
          VDomModifier(innerRender)
      },
      onUpdate --> sideEffect { (prev, _) =>
        scrolledToBottom
          .onNext(prev.scrollHeight - prev.clientHeight <= prev.scrollTop + 11) // at bottom + 10 px tolerance
      },
      onPostPatch.transform(_.withLatestFrom(scrolledToBottom) {
                              case ((_, elem), atBottom) => (elem, atBottom)
                            }) --> sideEffect { (elem, atBottom) =>
        if(atBottom) scrollToBottom(elem)
      },
      backgroundColor <-- state.pageStyle.map(_.bgLightColor),
    )
  }

  /// Renders all tasks of the current page via the renderTask fn
  def renderTasks(state: WorkflowState)
                 (implicit ctx: Ctx.Owner): Seq[VDomModifier] = Seq(Rx {
    val nodes = state.nodes
    nodes().map { nodeId =>
      val nodeCtx = NodeContext(nodeId, state.parentIds(ctx)())
      renderTask(state, nodeCtx)
    }
  })

  private def emptyChatNotice: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  def replyField(state: WorkflowState, nodeCtx: NodeContext,
                 path: List[NodeId])
                (implicit ctx: Ctx.Owner) = {
    div(
      Rx {
        div(
          cls := "chat-replybutton",
          freeSolid.faReply,
          " reply",
          marginTop := "3px",
          marginLeft := "8px",
          // not onClick, because if another reply-field is already open, the click first triggers the blur-event of
          // the active field. If the field was empty it disappears, and shifts the reply-field away from the cursor
          // before the click was finished. This does not happen with onMouseDown.
          //onMouseDown.stopPropagation --> sideEffect { activeReplyFields.update(_ + fullPath) }
        )
      }
    )
  }

  /// @return the actual body of a chat message
  private def renderTask(state: WorkflowState, nodeCtx : NodeContext)
                        (implicit ctx: Ctx.Owner) : Seq[VDomModifier] = {
    val isDeleted = state.isDeleted(nodeCtx)
    val isSelected = state.isSelected(nodeCtx)

    val editable = Var(false)

    val checkbox = div(
      cls := "ui checkbox fitted",
      isSelected.map(_.ifTrueOption(visibility.visible)),
      input(
        tpe := "checkbox",
        checked <-- isSelected,
        onChange.checked --> sideEffect { checked =>
          if(checked) state.selectedNodeIds.update(_ + nodeCtx.nodeId)
          else state.selectedNodeIds.update(_ - nodeCtx.nodeId)
        }
      ),
      label()
    )

    // val messageCard = workflowEntryEditable(state, node, editable = editable,
    //                                         state.eventProcessor.changes,
    //                                         newTagParentIds = directParentIds)(ctx)(
    //   isDeleted.ifTrueOption(cls := "node-deleted"), // TODO: outwatch: switch classes on and off via Boolean or Rx[Boolean]
    //   cls := "drag-feedback",
    //   messageCardInjected,
    //   onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    // )


    Seq(
      li(
        isSelected.map(_.ifTrueOption(backgroundColor := "rgba(65,184,255, 0.5)")),
        cls := "chatmsg-line",
        Styles.flex,
        onClick.stopPropagation(!editable.now) --> editable,
        onClick --> sideEffect { state.selectedNodeIds.update(_.toggle(nodeCtx.nodeId)) },

        Rx {
          val node = state.nodeFromId(nodeCtx)
          // TODO: workflowEntryEditable
          nodeCard(node(), checkbox)
        }
      ))
  }

  private def editButton(state: GlobalState, editable: Var[Boolean])(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", freeRegular.faEdit),
      onClick.stopPropagation(!editable.now) --> editable,
      cursor.pointer,
    )

  private def undeleteButton(state: WorkflowState, nodeId: NodeId, directParentIds: Set[NodeId])
                            (implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", fontawesome.layered(
        fontawesome.icon(freeRegular.faTrashAlt),
        fontawesome.icon(freeSolid.faMinus, new Params {
          transform = new Transform {
            rotate = 45.0
          }

        })
      )),
      onClick.stopPropagation --> sideEffect { _ => state.undelete(nodeId, directParentIds) },
      cursor.pointer,
    )

  private def zoomButton(state: WorkflowState, nodeId: NodeId)(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", freeRegular.faArrowAltCircleRight),
      onClick.stopPropagation --> sideEffect { _ => state.zoomTo(nodeId) },
      cursor.pointer,
    )

  private def inputField(state: WorkflowState, directParentIds: Set[NodeId], blurAction: String => Unit = _ => ())
                        (implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "ui form",
      keyed(directParentIds),
      textArea(
        keyed,
        cls := "field",
        onInsert.asHtml --> sideEffect { e => e.focus() },
        onBlur.value --> sideEffect { value => blurAction(value) },
        //disabled <-- disableUserInput,
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        style("resize") := "none", //TODO: add resize style to scala-dom-types
        Placeholders.newNode
      )
    )
  }
}
