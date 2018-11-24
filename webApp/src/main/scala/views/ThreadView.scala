package wust.webApp.views

import fontAwesome._

import scala.collection.{breakOut, mutable}
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
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
import flatland._
import monix.reactive.Observable
import wust.webApp.BrowserDetect
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.collection.breakOut
import scala.concurrent.Future
import scala.scalajs.js

object ThreadView {
  import SharedViewElements._
  //TODO: deselect after dragging
  //TODO: fix "remove tag" in cycles

  final case class SelectedNode(nodeId:NodeId, directParentIds: Iterable[NodeId])(val editMode:Var[Boolean], val showReplyField:Var[Boolean]) extends SelectedNodeBase

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val selectedNodes:Var[Set[SelectedNode]] = Var(Set.empty[SelectedNode])

    val scrollHandler = new ScrollBottomHandler

    val outerDragOptions = VDomModifier(
      draggableAs(DragItem.DisableDrag), // chat history is not draggable, only its elements
      Rx { state.page().parentId.map(pageParentId => dragTarget(DragItem.Chat.Page(pageParentId))) },
      registerDraggableContainer(state),
      cursor.auto, // draggable sets cursor.move, but drag is disabled on page background
    )

    val pageCounter = PublishSubject[Int]()
    val shouldLoadInfinite = Var[Boolean](false)
    val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

    div(
      keyed,
      Styles.flex,
      flexDirection.column,
      position.relative, // for absolute positioning of selectednodes
      SelectedNodes[SelectedNode](state, selectedNodeActions(state, selectedNodes), selectedSingleNodeActions(state, selectedNodes), selectedNodes).apply(
        position.absolute,
        width := "100%"
      ),
      div(
        cls := "chat-history",
        InfiniteScroll.onInfiniteScrollUp(shouldLoadInfinite) --> pageCounter,
        backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        chatHistory(state, selectedNodes, pageCounter, shouldLoadInfinite),
        outerDragOptions,

        // clicking on background deselects
        onClick foreach { e => if(e.currentTarget == e.target) selectedNodes() = Set.empty[SelectedNode] },
        scrollHandler.modifier,
        // on page change, always scroll down
        emitterRx(state.page).foreach {
          scrollHandler.scrollToBottomInAnimationFrame()
        }
      ),
      {
        def submitAction(str:String) = {
          // we treat new chat messages as noise per default, so we set a future deletion date
          scrollHandler.scrollToBottomInAnimationFrame()
          val ack = fileUploadHandler.now match {
            case None => state.eventProcessor.changes.onNext(GraphChanges.addNodeWithDeletedParent(Node.MarkdownMessage(str), state.page.now.parentId, deletedAt = noiseFutureDeleteDate))
            case Some(uploadFile) => uploadFileAndCreateNode(state, str, state.page.now.parentId, uploadFile)
          }

          ack
        }

        val inputFieldFocusTrigger = PublishSubject[Unit]

        if(!BrowserDetect.isMobile) {
          state.page.triggerLater {
            inputFieldFocusTrigger.onNext(Unit) // re-gain focus on page-change
            ()
          }
        }

        inputRow(state, submitAction, fileUploadHandler = Some(fileUploadHandler), scrollHandler = Some(scrollHandler), preFillByShareApi = true, autoFocus = !BrowserDetect.isMobile, triggerFocus = inputFieldFocusTrigger)(ctx)(Styles.flexStatic)
      }
    )
  }

  private def chatHistory(state: GlobalState, selectedNodes: Var[Set[SelectedNode]], externalPageCounter: Observable[Int], shouldLoadInfinite: Var[Boolean])(implicit ctx: Ctx.Owner): VDomModifier = {
    val initialPageCounter = 30
    val pageCounter = Var(initialPageCounter)
    state.page.foreach { _ => pageCounter() = initialPageCounter }

    val messages = Rx {
      val page = state.page()
      val graph = state.graph()

      calculateThreadMessages(page.parentId, graph)
    }

    var prevMessageSize = -1
    messages.foreach { messages =>
      if (prevMessageSize != messages.length) pageCounter() = initialPageCounter
      prevMessageSize = messages.length
    }

    Rx {
      shouldLoadInfinite() = !state.isLoading() && messages().length > pageCounter()
    }

    VDomModifier(
      Rx {
        state.screenSize() // on screensize change, rerender whole chat history
        val page = state.page()
        val pageCount = pageCounter()

        renderThreadGroups(state, messages().takeRight(pageCount), page.parentId, page.parentId.toSet, selectedNodes, true)
      },

      emitter(externalPageCounter) foreach { pageCounter.update(c => Math.min(c + initialPageCounter, messages.now.length)) },
    )
  }

  private def renderThreadGroups(state: GlobalState, messages: js.Array[Int], directParentIds: Iterable[NodeId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean = false)(implicit ctx: Ctx.Data): VDomModifier = {
    val graph = state.graph()
    val groups = calculateThreadMessageGrouping(messages, graph)

    VDomModifier(
      // large padding-top to have space for selectedNodes bar
      (isTopLevel && groups.nonEmpty).ifTrue[VDomModifier](padding := "50px 0px 5px 20px"),
      groups.map { group =>
        thunkRxFun(state, graph, group, directParentIds, transitiveParentIds, selectedNodes, isTopLevel)
      },
    )
  }

  private def thunkRxFun(state: GlobalState, groupGraph: Graph, group: Array[Int], directParentIds: Iterable[NodeId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean = false): VDomModifier = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.map(groupGraph.nodeIds)
    val key = nodeIds.head.toString

    div.thunkRx(key)(nodeIds, state.screenSize.now)(implicit ctx =>
      thunkGroup(state, groupGraph, group, directParentIds = directParentIds, transitiveParentIds = transitiveParentIds, selectedNodes = selectedNodes, isTopLevel = isTopLevel)
    )
  }

  private def thunkGroup(state: GlobalState, groupGraph: Graph, group: Array[Int], directParentIds:Iterable[NodeId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean)(implicit ctx: Ctx.Owner) = {
    val groupHeadId = groupGraph.nodeIds(group(0))
    val author: Rx[Option[Node.User]] = Rx {
      val graph = state.graph()
      graph.nodeCreator(graph.idToIdx(groupHeadId))
    }
    val creationEpochMillis = groupGraph.nodeCreated(group(0))
    val firstNodeId = groupGraph.nodeIds(group(0))
    val topLevelAndLargeScreen = isTopLevel && state.largeScreen

    VDomModifier(
      cls := "chat-group-outer-frame",
      topLevelAndLargeScreen.ifTrue[VDomModifier](author.map(_.map(bigAuthorAvatar))),

      div(
        cls := "chat-group-inner-frame",

        draggableAs(DragItem.DisableDrag),
        cursor.auto, // draggable sets cursor.move, but drag is disabled on thread background
        dragTarget(DragItem.Chat.Message(firstNodeId)),

        author.map(author => chatMessageHeader(author, creationEpochMillis, topLevelAndLargeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar)))),
        group.map { nodeIdx =>
          val nodeId = groupGraph.nodeIds(nodeIdx)

          div.thunkRx(keyValue(nodeId))(state.screenSize.now) { implicit ctx =>
            val nodeIdList = nodeId :: Nil

            val showReplyField = Var(false)

            val isDeletedNow = Rx {
              val graph = state.graph()
              graph.isDeletedNow(nodeId, directParentIds)
            }

            val editMode = Var(false)

            val inCycle = transitiveParentIds.contains(nodeId)

            if(inCycle)
              renderMessageRow(state, nodeId, directParentIds, selectedNodes, editMode = editMode, isDeletedNow = isDeletedNow, showReplyField = showReplyField, isExpanded = Rx(false), inCycle = true)
            else {
              val isExpanded = Rx {
                // we need to get the newest node content from the graph
                val graph = state.graph()
                val user = state.user()
                graph.expandedNodes(user.id).contains(nodeId)
              }

              val showExpandedThread = Rx {
                !isDeletedNow() && (isExpanded() || showReplyField())
              }

              VDomModifier(
                renderMessageRow(state, nodeId, directParentIds, selectedNodes, editMode = editMode, isDeletedNow = isDeletedNow, isExpanded = isExpanded, showReplyField = showReplyField, inCycle = false),
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

      draggableAs(DragItem.DisableDrag),
      cursor.auto, // draggable sets cursor.move, but drag is disabled on thread background
      dragTarget(DragItem.Chat.Thread(nodeId :: Nil)),

      div(
        cls := "chat-thread-messages-outer",
        Styles.flex,
        div(
          boxSizing.borderBox,
          width := "3px",
          backgroundColor := tagColor(nodeId).toHex,
          Styles.flexStatic,

          cursor.pointer,
          UI.tooltip := "Click to collapse",
          onClick(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, nodeId)) --> state.eventProcessor.changes
        ),
        div(
          cls := "chat-thread-messages",
          Rx {
            renderThreadGroups(state, calculateThreadMessages(nodeIdList, state.graph()), directParentIds = nodeIdList, transitiveParentIds = transitiveParentIds + nodeId, selectedNodes = selectedNodes)
          },
          Rx {
            if(showReplyField()) threadReplyField(state, nodeId, showReplyField)
            else threadReplyButton(state, nodeId, showReplyField)
          }
        )
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
      onClick.stopPropagation foreach { showReplyField() = true }
    )
  }

  private def threadReplyField(state: GlobalState, nodeId: NodeId, showReplyField: Var[Boolean])(implicit ctx:Ctx.Owner): VNode = {

    def handleInput(str: String): Future[Ack] = if (str.nonEmpty) {
      // we treat new chat messages as noise per default, so we set a future deletion date
      val graph = state.graph.now
      val user = state.user.now
      val addNodeChange = GraphChanges.addNodeWithDeletedParent(Node.MarkdownMessage(str), nodeId :: Nil, deletedAt = noiseFutureDeleteDate)
      val expandChange = if(!graph.isExpanded(user.id, nodeId)) GraphChanges.connect(Edge.Expanded)(user.id, nodeId) else GraphChanges.empty
      val changes = addNodeChange merge expandChange
      state.eventProcessor.changes.onNext(changes)
    } else Future.successful(Continue)

    def blurAction(value: String):Unit = if(value.isEmpty) {
      window.setTimeout(() => showReplyField() = false, 150)
    }


    inputRow(state, submitAction = handleInput, blurAction = Some(blurAction), autoFocus = true).apply(
      closeButton(
        padding := "15px",
        onClick.stopPropagation foreach { showReplyField() = false },
      ),
    )
  }

  private def renderMessageRow(state: GlobalState, nodeId: NodeId, directParentIds:Iterable[NodeId], selectedNodes: Var[Set[SelectedNode]], isDeletedNow: Rx[Boolean], editMode: Var[Boolean], showReplyField: Var[Boolean], isExpanded:Rx[Boolean], inCycle:Boolean)(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      selectedNodes().exists(selected => selected.nodeId == nodeId && selected.directParentIds == directParentIds)
    }

    val isDeletedInFuture = Rx {
      val graph = state.graph()
      graph.isDeletedInFuture(nodeId, directParentIds)
    }

    val renderedMessage = renderMessage(state, nodeId, directParentIds, isDeletedNow = isDeletedNow, isDeletedInFuture = isDeletedInFuture, editMode = editMode, renderedMessageModifier = inCycle.ifTrue(VDomModifier(
      Styles.flex,
      alignItems.center,
      freeSolid.faSyncAlt,
      paddingRight := "3px",
      backgroundColor := "#CCC",
      color := "#666",
      boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
    )))
    val controls = msgControls(state, nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, isDeletedInFuture = isDeletedInFuture, editMode = editMode, replyAction = showReplyField() = !showReplyField.now)
    val checkbox = msgCheckbox(state, nodeId, selectedNodes, newSelectedNode = SelectedNode(_, directParentIds)(editMode, showReplyField), isSelected = isSelected)
    val selectByClickingOnRow = {
      onClickOrLongPress foreach { longPressed =>
        if(longPressed) selectedNodes.update(_ + SelectedNode(nodeId, directParentIds)(editMode, showReplyField))
        else {
          // stopPropagation prevents deselecting by clicking on background
          val selectionModeActive = selectedNodes.now.nonEmpty
          if(selectionModeActive) selectedNodes.update(_.toggle(SelectedNode(nodeId, directParentIds)(editMode, showReplyField)))
        }
      }
    }
    val expandCollapsButton = Rx{ (isDeletedNow() || inCycle).ifFalse[VDomModifier](renderExpandCollapseButton(state, nodeId, isExpanded)) }

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
      messageRowDragOptions(nodeId, selectedNodes, editMode)
    )
  }

  private def renderExpandCollapseButton(state: GlobalState, nodeId: NodeId, isExpanded: Rx[Boolean])(implicit ctx: Ctx.Owner) = {
    val childrenSize = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(nodeId))
    }
    Rx {
      if(isExpanded()) {
        div(
          cls := "collapsebutton ui mini blue label",
          "-",
          marginLeft := "10px",
          onClick.mapTo(GraphChanges.disconnect(Edge.Expanded)(state.user.now.id, nodeId)) --> state.eventProcessor.changes,
          cursor.pointer,
          UI.tooltip := "Collapse"
        )
      } else {
        if(childrenSize() == 0) VDomModifier.empty
        else div(
          cls := "collapsebutton ui mini blue label",
          "+" + childrenSize(),
          marginLeft := "10px",
          onClick.mapTo(GraphChanges.connect(Edge.Expanded)(state.user.now.id, nodeId)) --> state.eventProcessor.changes,
          cursor.pointer,
          UI.tooltip := (if (childrenSize() == 1) "Expand 1 item" else s"Expand ${childrenSize()} items")
        )
      }
    }
  }

  def calculateThreadMessages(parentIds: Iterable[NodeId], graph: Graph): js.Array[Int] = {
    // most nodes don't have any children, so we skip the expensive accumulation
    if(parentIds.size == 1 && !graph.hasChildren(parentIds.head)) return js.Array[Int]()

    val nodeSet = ArraySet.create(graph.nodes.length)
    //TODO: performance: depthFirstSearchMultiStartForeach which starts at multiple start points and accepts a function
    parentIds.foreach { parentId =>
      val parentIdx = graph.idToIdx(parentId)
      if(parentIdx != -1) {
        graph.childrenIdx.foreachElement(parentIdx) { childIdx =>
          val childNode = graph.nodes(childIdx)
          if(childNode.isInstanceOf[Node.Content] && (childNode.role == NodeRole.Message || graph.childrenIdx(childIdx).exists(idx => graph.nodes(idx).role == NodeRole.Message)))
            nodeSet.add(childIdx)
        }
      }
    }
    val nodes = js.Array[Int]()
    nodeSet.foreach(nodes += _)
    sortByCreated(nodes, graph)
    nodes
  }

  def calculateThreadMessageGrouping(messages: js.Array[Int], graph: Graph): Array[Array[Int]] = {
    if(messages.isEmpty) return Array[Array[Int]]()

    val groupsBuilder = mutable.ArrayBuilder.make[Array[Int]]
    val currentGroupBuilder = new mutable.ArrayBuilder.ofInt
    var lastAuthor: Int = -2 // to distinguish between no author and no previous group
    messages.foreach { message =>
      val author: Int = graph.nodeCreatorIdx(message) // without author, returns -1

      if(author != lastAuthor && lastAuthor != -2) {
        groupsBuilder += currentGroupBuilder.result()
        currentGroupBuilder.clear()
      }

      currentGroupBuilder += message
      lastAuthor = author
    }
    groupsBuilder += currentGroupBuilder.result()
    groupsBuilder.result()
  }


  //TODO share code with chatview?
  private def selectedSingleNodeActions(state: GlobalState, selectedNodes: Var[Set[SelectedNode]]): (SelectedNode, Boolean) => List[VNode] = (selectedNode, canWriteAll) => if(state.graph.now.contains(selectedNode.nodeId)) {
    List(
      Some(editButton(
        onClick foreach {
          selectedNode.editMode() = true
          selectedNodes() = Set.empty[SelectedNode]
        }
      )).filter(_ => canWriteAll),
      Some(zoomButton(onClick foreach {
        state.page.onNext(Page(selectedNode.nodeId))
        selectedNodes() = Set.empty[SelectedNode]
      })),
      Some(replyButton(
        onClick foreach {
          Var.set(selectedNodes.now.map(s => Var.Assignment(s.showReplyField, true))(breakOut) : _*)
          selectedNodes() = Set.empty[SelectedNode]
        }
      )).filter(_ => canWriteAll)
    )
  }.flatten else Nil
}
