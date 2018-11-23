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
import org.scalajs.dom.{window}

/// Provides a node with the context that it resides in (i.e. its relevant parents)
case class NodeContext(val nodeId: NodeId, val directParentIds : Set[NodeId])
object NodeContext
{
  implicit def toNodeId(node : NodeContext) = node.nodeId
}

object Options {
  /// Whether to show a checkbox for strikethrough behaviour
  val showCheckbox = false
  /// Whether to list already deleted nodes
  val showDeleted = false
  /// Whether to select entire contents of a just-focused node
  val selectAllOnEditFocus = false
  /// Text to fill new (non-saved) nodes with
  val defaultNewNodeText = ""
}

/// All state manipulations go through this class.
/** Mostly used as a safety-abstraction over the ever-changing GlobalState. */
class WorkflowState(
  val globalState : GlobalState
)
{
  //////////////////////////////////////////////////
  // State
  val markedNodeIds : Var[Set[NodeId]] = Var(Set.empty)
  /// Local store of checked node ids
  /** Will have to be saved in the graph at some point */
  val checkedNodeIds : Var[Set[NodeId]] = Var(Set.empty)

  /// The only selected node id (where selection also allows editing)
  val selectedNodeId : Var[Option[NodeId]] = Var(None)


  //////////////////////////////////////////////////
  // Queries
  def parentIds(implicit ctx: Ctx.Owner) = Rx {
    globalState.page().parentIdSet
  }

  def page = globalState.page
  def user = globalState.user

  def pageStyle = globalState.pageStyle

  def nodeFromId(nodeId : NodeId)(implicit ctx: Ctx.Owner) = Rx {
    globalState.graph().nodesById(nodeId)
  }


  def add(node : Node)(implicit ctx: Ctx.Owner) = {
    val parents = parentIds.now
    globalState.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(node, parents))
  }

  def addAfter(nodeId : NodeId, node : Node)(implicit ctx: Ctx.Owner) = {
    val parents = parentIds.now
    globalState.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(node, parents))
    parents.map { p =>
      addBeforeEdge(nodeId, node.id, p)
    }
  }

  def makeNode(text : String = Options.defaultNewNodeText) = {
    Node.MarkdownTask(text)
  }


  def addBeforeEdge(before : NodeId, after : NodeId, parent : NodeId) {
    globalState.eventProcessor.changes.onNext(GraphChanges(addEdges = Set(Edge.Before(before, after, parent))))
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

  /// Checks if the given node is marked by the user
  /** Marking allows operations on multiple entries */
  def isMarked(nodeId : NodeId)(implicit ctx: Ctx.Owner) = markedNodeIds.map(_ contains nodeId)

  def isChecked(node : NodeContext)(implicit ctx: Ctx.Owner) = checkedNodeIds.map(_ contains node.nodeId)

  /// zooms on a node, effectively changing the page
  def zoomTo(nodeId : NodeId)(implicit ctx: Ctx.Owner) = {
    globalState.viewConfig.onNext(globalState.viewConfig.now.copy(page = Page(nodeId)))
  }

  def nodes(implicit ctx: Ctx.Owner) = Rx {
    val page = globalState.page()
    val fullGraph = globalState.graph()
    val nodes = fullGraph.chronologicalNodesAscending.collect {
      case n: Node.Content if (fullGraph.isChildOfAny(n.id, page.parentIds)
                                 && (!Options.showDeleted && !fullGraph.isDeletedNow(n.id, page.parentIds))) =>
        NodeContext(n.id, parentIds(ctx)())
    }
    val sorted = fullGraph.topologicalSortBy(nodes, (n : NodeContext) => n.nodeId)
    sorted
  }
}


object WorkflowView {
  // -- display options --
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
          state.markedNodeIds.update(_ -- nodeId)
        },
        cursor.pointer,
        )

    def taskCheckbox(state: WorkflowState, nodeCtx: NodeContext)(implicit ctx: Ctx.Owner) = {
      val isMarked = state.isMarked(nodeCtx)
      val isChecked = state.isChecked(nodeCtx)
      div(
        cls := "ui checkbox fitted",
        // isMarked.map(_.ifTrueOption(visibility.visible)),
        input(
          tpe := "checkbox",
          checked <-- isChecked,
          onChange.checked --> sideEffect { checked =>
            if(checked) state.checkedNodeIds.update(_ + nodeCtx.nodeId)
            else state.checkedNodeIds.update(_ - nodeCtx.nodeId)
          }
        ),
        label()
      )
    }

    def taskContent(state: WorkflowState, nodeCtx: NodeContext)(implicit ctx: Ctx.Owner) = {
      val nodeRef = state.nodeFromId(nodeCtx)
      Rx {
        val node = nodeRef().asInstanceOf[Node.Content]

        div(
          keyed(node.id),
          VDomModifier(taskContentEditable(state, node)),
          onClick --> sideEffect {
            state.selectedNodeId() = Some(nodeCtx.nodeId)
          },
        )
      }
    }

    def taskContentEditable(state: WorkflowState,
                            node: Node.Content,
                            focusOnMount : Boolean = false)(
      implicit ctx: Ctx.Owner
    ): VNode = {
      import org.scalajs.dom.document
      import org.scalajs.dom.raw.{HTMLElement}
      import wust.webApp.BrowserDetect

      def save(contentEditable:HTMLElement): Unit = {
          val text = contentEditable.textContent
          val updatedNode = node.copy(data = NodeData.Markdown(text))
          state.add(updatedNode)
      }

      def discardChanges(): Unit = {
      }

      def deleteNode(): Unit = {
        discardChanges()
        println(s"Deleting ${node.id}")
        // FIXME: this will only work for top-level nodes, no?
        state.delete(node.id, state.parentIds.now)
      }

      object KeyCode {
        val Backspace = 8
        val Enter = 13
      }

      def isAtBeginningOfEditable = {
        val range = window.getSelection.getRangeAt(0)
        range.endOffset == 0
      }

      def isAtEndOfEditable(inputElement: HTMLElement) = {
        val range = window.getSelection.getRangeAt(0)
        val contentLen = inputElement.innerHTML.length
        val selectionEnd = range.endOffset
        selectionEnd == contentLen
      }

      val backspacePressedAtBeginning = onKeyDown
        .filter(_.keyCode == KeyCode.Backspace)
        .filter { _ => isAtBeginningOfEditable }

      val enterPressedAtEnd = onKeyDown
        .filter(_.keyCode == KeyCode.Enter)
        .filter { _.target match {
                   case textArea : HTMLElement => isAtEndOfEditable(textArea)
                   case _ => false
                 } }

      val deletionEvents = backspacePressedAtBeginning
      val isSelected = state.selectedNodeId.now == Some(node.id)

      p( // has different line-height than div and is used for text by markdown
        outline := "none", // hides contenteditable outline
        keyed, // when updates come in, don't disturb current editing session
        VDomModifier(
          node.data.str, // Markdown source code
          contentEditable := true,
          whiteSpace.preWrap, // preserve white space in Markdown code
          backgroundColor := "#FFF",
          color := "#000",
          minWidth := "10px",
          cursor.auto,

          if(BrowserDetect.isMobile) VDomModifier(
            onBlur foreach { e => save(e.target.asInstanceOf[HTMLElement]) },
            ) else VDomModifier(
            onEnter foreach { e => save(e.target.asInstanceOf[HTMLElement]) },
            onBlur foreach { discardChanges() },
            ),
          // FIXME: we need the parents of the current node and have to use the same parents on the new node
          // FIXME: the new node should come after the current node
          enterPressedAtEnd foreach { _ =>
            val newNode = state.makeNode()
            state.addAfter(node.id, newNode)
            state.selectedNodeId() = Some(newNode.id)
          },
          onClick.stopPropagation foreach {} // prevent e.g. selecting node, but only when editing
        ),

        deletionEvents foreach { deleteNode() },
        (focusOnMount || isSelected).ifTrue[VDomModifier](onDomMount.asHtml --> inNextAnimationFrame { _.focus() }),
        Options.selectAllOnEditFocus.ifTrue[VDomModifier](
          onFocus foreach { e =>
            document.execCommand("selectAll", false, null)
          }
        ),
      ),

    }
  }

  val showDebugInfo = true

  /// Controls to e.g indent or outdent the last edited entry
  /** TODO: indent/outdent requires an order between entries. */
  def activeEditableControls(state: WorkflowState)(implicit ctx: Ctx.Owner) = Rx {
    val nodes : Set[NodeId] = state.markedNodeIds()
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
                 (implicit ctx: Ctx.Owner): Seq[VDomModifier] = Seq(
    Rx {
      val nodes = state.nodes
      if(nodes().isEmpty)
        Seq(ghostEntry(state))
      else
        nodes().map { nodeCtx =>
          renderTask(state, nodeCtx)
        }
    })

  def ghostEntry(state: WorkflowState)(implicit ctx: Ctx.Owner) = Rx[Seq[VDomModifier]] {
    Seq(
      components.taskContentEditable(state, state.makeNode(), focusOnMount = true)
    )
  }

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
                        (implicit ctx: Ctx.Owner) = Rx[Seq[VDomModifier]] {
    val isMarked = state.isMarked(nodeCtx)
    val isChecked = state.isChecked(nodeCtx)
    val editable = Var(false)

    Seq(
      li(
        isChecked().ifTrue[VDomModifier](
          textDecoration := "line-through"
        ),
        isMarked.map(_.ifTrueOption(backgroundColor := "rgba(65,184,255, 0.5)")),
        cls := "chatmsg-line",
        Styles.flex,
        onClick.stopPropagation(!editable.now) --> editable,

        Options.showCheckbox.ifTrue[VDomModifier](components.taskCheckbox(state, nodeCtx)),
        components.taskContent(state, nodeCtx)
      ))
  }

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
