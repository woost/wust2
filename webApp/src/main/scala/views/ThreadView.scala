package wust.webApp.views

import fontAwesome._
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.util.collection._
import wust.webApp.BrowserDetect
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.collection.breakOut
import scala.scalajs.js

object ThreadView {
  import SharedViewElements._
  //TODO: show less on small screen sizes, word-break
  //TODO: reply submit button on mobile
  //TODO: smaller input field placeholder on small screens / mobile. Enter cannot be pressed on mobile
  //TODO: deselect after dragging
  //TODO: fix "remove tag" in cycles

  final case class SelectedNode(nodeId:NodeId)(val editMode:Var[Boolean], val showReplyField:Var[Boolean]) extends SelectedNodeBase

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val selectedNodes:Var[Set[SelectedNode]] = Var(Set.empty[SelectedNode])

    val scrollHandler = ScrollHandler(Var(None: Option[HTMLElement]), Var(true))

    val outerDragOptions = VDomModifier(
      draggableAs(state, DragItem.DisableDrag), // chat history is not draggable, only its elements
      Rx { dragTarget(DragItem.Chat.Page(state.page().parentIds)) },
      registerDraggableContainer(state),
      cursor.auto, // draggable sets cursor.move, but drag is disabled on page background
    )

    div(
      keyed,
      Styles.flex,
      flexDirection.column,
      position.relative, // for absolute positioning of selectednodes
      SelectedNodes[SelectedNode](state, _.nodeId, selectedNodeActions(state, selectedNodes), selectedSingleNodeActions(state, selectedNodes), selectedNodes).apply(
        position.absolute,
        width := "100%"
      ),
      div(
        cls := "chat-history",
        overflow.auto,
        backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        chatHistory(state, selectedNodes),
        outerDragOptions,

        // clicking on background deselects
        onClick handleWith { selectedNodes() = Set.empty[SelectedNode] },
        scrollHandler.scrollOptions(state)

      ),
      inputField(state, state.page.now.parentIds)(ctx)(Styles.flexStatic)
    )
  }

  private def chatHistory(state: GlobalState, selectedNodes: Var[Set[SelectedNode]])(implicit ctx: Ctx.Owner): Rx[Array[ThunkVNode]] = {
    Rx {
      val page = state.page()
      renderThreadGroups(state, directParentIds = page.parentIds, transitiveParentIds = page.parentIdSet, selectedNodes, isTopLevel = true)
    }
  }

  private def renderThreadGroups(state: GlobalState, directParentIds: Iterable[NodeId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean = false)(implicit ctx: Ctx.Data): Array[ThunkVNode] = {
    val graph = state.graph()
    val groups = calculateMessageGrouping(calculateThreadMessages(directParentIds, graph), graph)
    groups.map { group =>
      // because of equals check in thunk, we implicitly generate a wrapped array
      val nodeIds: Seq[NodeId] = group.map(graph.lookup.nodeIds)
      val key = nodeIds.head.toString

      div.thunk(key)(nodeIds)(thunkGroup(state, graph, group, directParentIds = directParentIds, transitiveParentIds = transitiveParentIds, selectedNodes = selectedNodes, isTopLevel = isTopLevel))
    }
  }

  private def thunkGroup(state: GlobalState, groupGraph: Graph, group: Array[Int], directParentIds:Iterable[NodeId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean) = {
    val author:Option[Node.User] = groupGraph.lookup.authorsIdx.get(group(0), 0).map(authorIdx => groupGraph.lookup.nodes(authorIdx).asInstanceOf[Node.User])
    val creationEpochMillis = groupGraph.lookup.nodeCreated(group(0))

    VDomModifier(
      cls := "chat-group-outer-frame",
      isTopLevel.ifTrue[VDomModifier](author.map(bigAuthorAvatar)),
      div(
        cls := "chat-group-inner-frame",
        chatMessageHeader(author, creationEpochMillis, isTopLevel.ifFalse[VDomModifier](author.map(smallAuthorAvatar))),
        group.map { groupIdx =>
          val nodeId = groupGraph.lookup.nodeIds(groupIdx)

          div.staticRx(keyValue(nodeId)) { implicit ctx =>
            val nodeIdList = nodeId :: Nil

            val showReplyField = Var(false)

            val isDeleted = Rx {
              val graph = state.graph()
              graph.lookup.isDeletedNow(nodeId, directParentIds)
            }

            val editMode = Var(false)

            val inCycle = transitiveParentIds.contains(nodeId)

            if(inCycle)
              renderMessageRow(state, nodeId, directParentIds, selectedNodes, editMode = editMode, isDeleted = isDeleted, showReplyField = showReplyField, isExpanded = Rx(false), inCycle = true)
            else {
              val isExpanded = Rx {
                // we need to get the newest node content from the graph
                val graph = state.graph()
                val user = state.user()
                graph.lookup.expandedNodes(user.id).contains(nodeId)
              }

              val showExpandedThread = Rx {
                !isDeleted() && (isExpanded() || showReplyField())
              }

              VDomModifier(
                renderMessageRow(state, nodeId, directParentIds, selectedNodes, editMode = editMode, isDeleted = isDeleted, isExpanded = isExpanded, showReplyField = showReplyField),
                Rx {
                  showExpandedThread().ifTrue[VDomModifier] {
                    renderExpandedThread(state, transitiveParentIds, selectedNodes, nodeId, nodeIdList, showReplyField)
                  }
                },
              )
            }
          }
        }
      )
    )
  }

  private def renderExpandedThread(state: GlobalState, transitiveParentIds: Set[NodeId], selectedNodes: Var[Set[SelectedNode]], nodeId: NodeId, nodeIdList: List[NodeId], showReplyField: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    val bgColor = BaseColors.pageBgLight.copy(h = NodeColor.hue(nodeId)).toHex
    VDomModifier(
      cls := "chat-expanded-thread",
      backgroundColor := bgColor,

      draggableAs(state, DragItem.DisableDrag),
      cursor.auto, // draggable sets cursor.move, but drag is disabled on thread background
      dragTarget(DragItem.Chat.Thread(nodeId)),

      div(
        cls := "chat-thread-messages",
        borderLeft := s"3px solid ${ tagColor(nodeId).toHex }",
        Rx { renderThreadGroups(state, directParentIds = nodeIdList, transitiveParentIds = transitiveParentIds + nodeId, selectedNodes = selectedNodes) },
        Rx {
          if(showReplyField()) threadReplyField(state, nodeId, showReplyField)
          else threadReplyButton(state, nodeId, showReplyField)
        }
      )
    )
  }

  private def threadReplyButton(state: GlobalState, nodeId:NodeId, showReplyField: Var[Boolean]) = {
    div(
      cls := "chat-replybutton",
      freeSolid.faReply,
      " reply",
      marginTop := "3px",
      marginLeft := "8px",
      onClick.stopPropagation handleWith { showReplyField() = true }
    )
  }

  private def threadReplyField(state: GlobalState, nodeId: NodeId, showReplyField: Var[Boolean]): VNode = {
    div(
      Styles.flex,
      alignItems.center,

      div(
        cls := "ui form",
        textArea(
          cls := "field",
          valueWithEnter handleWith { str =>
            val changes = {
              GraphChanges.addNodeWithParent(Node.Content(NodeData.Markdown(str)), nodeId :: Nil)
            }

            state.eventProcessor.changes.onNext(changes)
          },
          rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
          resize := "none",
          placeholder := (if(BrowserDetect.isMobile) "Write a message" else "Write a message and press Enter to submit."),
          onDomMount.asHtml --> inNextAnimationFrame(_.focus()), // immediately focus
          //TODO: outwatch: Emitterbuilder.timeOut
          onBlur.value --> sideEffect {value => if(value.isEmpty) window.setTimeout(() => showReplyField() = false, 150)},
        ),
        padding := "3px",
        width := "100%"
      ),
      closeButton(
        onClick handleWith { showReplyField() = false },
      ),
    )
  }

  private def renderMessageRow(state: GlobalState, nodeId: NodeId, directParentIds:Iterable[NodeId], selectedNodes: Var[Set[SelectedNode]], isDeleted: Rx[Boolean], editMode: Var[Boolean], showReplyField: Var[Boolean], isExpanded:Rx[Boolean], inCycle:Boolean = false)(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      selectedNodes().exists(_.nodeId == nodeId)
    }

    val renderedMessage = renderMessage(state, nodeId, isDeleted = isDeleted, editMode = editMode, renderedMessageModifier = inCycle.ifTrue(VDomModifier(
      Styles.flex,
      alignItems.center,
      freeSolid.faSyncAlt,
      paddingRight := "3px",
      backgroundColor := "#CCC",
      color := "#666",
      boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
    )))
    val controls = msgControls(state, nodeId, directParentIds, selectedNodes, isDeleted = isDeleted, editMode = editMode, replyAction = showReplyField() = !showReplyField.now)
    val checkbox = msgCheckbox(state, nodeId, selectedNodes, newSelectedNode = SelectedNode(_)(editMode, showReplyField), isSelected = isSelected)
    val selectByClickingOnRow = {
      onClickOrLongPress handleWith { longPressed =>
        if(longPressed) selectedNodes.update(_ + SelectedNode(nodeId)(editMode, showReplyField))
        else {
          // stopPropagation prevents deselecting by clicking on background
          val selectionModeActive = selectedNodes.now.nonEmpty
          if(selectionModeActive) selectedNodes.update(_.toggle(SelectedNode(nodeId)(editMode, showReplyField)))
        }
      }
    }
    val expandCollapsButton = Rx{ (isDeleted() || inCycle).ifFalse[VDomModifier](renderExpandCollapseButton(state, nodeId, isExpanded)) }

    div(
      cls := "chat-row",
      Styles.flex,

      isSelected.map(_.ifTrue[VDomModifier](backgroundColor := "rgba(65,184,255, 0.5)")),
      selectByClickingOnRow,
      checkbox,
      renderedMessage,
      expandCollapsButton,
      messageTags(state, nodeId, directParentIds),
      controls,
      messageRowDragOptions(state, nodeId, selectedNodes, editMode)
    )
  }

  private def renderExpandCollapseButton(state: GlobalState, nodeId: NodeId, isExpanded: Rx[Boolean])(implicit ctx: Ctx.Owner) = {
    val childrenSize = Rx {
      val graph = state.graph()
      graph.children(nodeId).size
    }
    Rx {
      if(isExpanded()) {
        div(
          cls := "collapsebutton ui mini blue label",
          "-",
          marginLeft := "10px",
          onClick.mapTo(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, nodeId)) --> state.eventProcessor.changes,
          cursor.pointer)
      } else {
        if(childrenSize() == 0) VDomModifier.empty
        else div(
          cls := "collapsebutton ui mini blue label",
          "+" + childrenSize(),
          marginLeft := "10px",
          onClick.mapTo(GraphChanges.connect(Edge.Expanded)(state.user.now.id, nodeId)) --> state.eventProcessor.changes,
          cursor.pointer
        )
      }
    }
  }

  def calculateThreadMessages(parentIds: Iterable[NodeId], graph: Graph): js.Array[Int] = {
    // most nodes don't have any children, so we skip the expensive accumulation
    if(parentIds.size == 1 && !graph.lookup.hasChildren(parentIds.head)) return js.Array[Int]()

    val nodeSet = ArraySet.create(graph.nodes.length)
    parentIds.foreach { parentId =>
      val parentIdx = graph.lookup.idToIdx(parentId)
      if(parentIdx != -1) {
        graph.lookup.childrenIdx.foreachElement(parentIdx) { childIdx =>
          val childNode = graph.lookup.nodes(childIdx)
          if(childNode.isInstanceOf[Node.Content])
            nodeSet.add(childIdx)
        }
      }
    }
    val nodes = js.Array[Int]()
    nodeSet.foreachAdded(nodes += _)
    sortByCreated(nodes, graph)
    nodes
  }

  //TODO share code with threadview?
  private def selectedSingleNodeActions(state: GlobalState, selectedNodes: Var[Set[SelectedNode]]): SelectedNode => List[VNode] = selectedNode => if(state.graph.now.lookup.contains(selectedNode.nodeId)) {
    List(
      editButton(
        onClick handleWith {
          selectedNodes.now.head.editMode() = true
          selectedNodes() = Set.empty[SelectedNode]
        }
      ),
      replyButton(
        onClick handleWith {
          selectedNodes.now.head.showReplyField() = true
          selectedNodes() = Set.empty[SelectedNode]
        }
      ) //TODO: scroll to focused field?
    )
  } else Nil
}
