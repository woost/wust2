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
import wust.webApp.{BrowserDetect, Icons, Ownable}
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState, ScreenSize, Placeholder}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.collection.breakOut
import scala.concurrent.Future
import scala.scalajs.js

object ThreadView {
  import SharedViewElements._
  //TODO: deselect after dragging
  //TODO: fix "remove tag" in cycles

  final case class SelectedNode(nodeId:NodeId, directParentIds: Iterable[ParentId])(val showReplyField:Var[Boolean]) extends SelectedNodeBase

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val selectedNodes:Var[Set[SelectedNode]] = Var(Set.empty[SelectedNode])

    val scrollHandler = new ScrollBottomHandler

    val outerDragOptions = VDomModifier(
      registerDragContainer(state),
      drag(target = DragItem.Workspace(focusState.focusedId))
    )

    val pageCounter = PublishSubject[Int]()
    val shouldLoadInfinite = Var[Boolean](false)
    val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

    div(
      keyed,
      Styles.flex,
      flexDirection.column,
      position.relative, // for absolute positioning of selectednodes
      SelectedNodes[SelectedNode](
        state,
        selectedNodes,
        selectedNodeActions(state, selectedNodes),
        (_, _) => Nil
      ).apply(
        position.absolute,
        width := "100%"
      ),
      div(
        cls := "chat-history",
        InfiniteScroll.onInfiniteScrollUp(shouldLoadInfinite) --> pageCounter,
        chatHistory(state, focusState, selectedNodes, pageCounter, shouldLoadInfinite),
        outerDragOptions,

        // clicking on background deselects
        onClick foreach { e => if(e.currentTarget == e.target) selectedNodes() = Set.empty[SelectedNode] },
        scrollHandler.modifier,
      ),
      {
        def submitAction(str:String) = {
          scrollHandler.scrollToBottomInAnimationFrame()
          val basicNode = Node.MarkdownMessage(str)
          val basicGraphChanges = GraphChanges.addNodeWithParent(basicNode, ParentId(focusState.focusedId))
          fileUploadHandler.now match {
            case None => state.eventProcessor.changes.onNext(basicGraphChanges)
            case Some(uploadFile) => AWS.uploadFileAndCreateNode(state, uploadFile, fileId => basicGraphChanges merge GraphChanges.connect(Edge.LabeledProperty)(basicNode.id, EdgeData.LabeledProperty.attachment, PropertyId(fileId)))
          }

          fileUploadHandler() = None
        }

        val inputFieldFocusTrigger = PublishSubject[Unit]

        inputRow(state, submitAction, fileUploadHandler = Some(fileUploadHandler), scrollHandler = Some(scrollHandler), preFillByShareApi = true, autoFocus = !BrowserDetect.isMobile && !focusState.isNested, triggerFocus = inputFieldFocusTrigger, showMarkdownHelp = true, enforceUserName = true, placeholder = Placeholder.newMessage)(ctx)(Styles.flexStatic)
      }
    )
  }

  private def chatHistory(state: GlobalState, focusState: FocusState, selectedNodes: Var[Set[SelectedNode]], externalPageCounter: Observable[Int], shouldLoadInfinite: Var[Boolean])(implicit ctx: Ctx.Owner): VDomModifier = {
    val initialPageCounter = 30
    val pageCounter = Var(initialPageCounter)

    val messages = Rx {
      val graph = state.graph()

      calculateThreadMessages(List(focusState.focusedId), graph)
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
        val pageCount = pageCounter()

        renderThreadGroups(state, messages().takeRight(pageCount), Set(ParentId(focusState.focusedId)), Set(focusState.focusedId), selectedNodes, true)
      },

      emitter(externalPageCounter) foreach { pageCounter.update(c => Math.min(c + initialPageCounter, messages.now.length)) },
    )
  }

  private def renderThreadGroups(state: GlobalState, messages: js.Array[Int], directParentIds: Iterable[ParentId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean = false)(implicit ctx: Ctx.Data): VDomModifier = {
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

  private def thunkRxFun(state: GlobalState, groupGraph: Graph, group: Array[Int], directParentIds: Iterable[ParentId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean = false): VDomModifier = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.viewMap(groupGraph.nodeIds)
    val key = nodeIds.head.toString

    div.thunk(key)(nodeIds, state.screenSize.now)(Ownable { implicit ctx =>
      thunkGroup(state, groupGraph, group, directParentIds = directParentIds, transitiveParentIds = transitiveParentIds, selectedNodes = selectedNodes, isTopLevel = isTopLevel)
    })
  }

  private def thunkGroup(state: GlobalState, groupGraph: Graph, group: Array[Int], directParentIds:Iterable[ParentId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean)(implicit ctx: Ctx.Owner) = {
    val groupHeadId = groupGraph.nodeIds(group(0))
    val author: Rx[Option[Node.User]] = Rx {
      val graph = state.graph()
      graph.nodeCreator(graph.idToIdxOrThrow(groupHeadId))
    }
    val creationEpochMillis = groupGraph.nodeCreated(group(0))
    val firstNodeId = groupGraph.nodeIds(group(0))
    val topLevelAndLargeScreen = isTopLevel && state.largeScreen

    VDomModifier(
      cls := "chat-group-outer-frame",
      topLevelAndLargeScreen.ifTrue[VDomModifier](author.map(_.map(user => bigAuthorAvatar(user)(onClickDirectMessage(state, user))))),

      div(
        cls := "chat-group-inner-frame",

        // drag(target = DragItem.Message(firstNodeId)),

        author.map(author => chatMessageHeader(state, author, creationEpochMillis, groupHeadId, topLevelAndLargeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar)))),
        group.map { nodeIdx =>
          val nodeId = ParentId(groupGraph.nodeIds(nodeIdx))

          div.thunk(nodeId.toStringFast)(state.screenSize.now)(Ownable { implicit ctx =>
            val nodeIdList = nodeId :: Nil

            val showReplyField = Var(false)

            val isDeletedNow =  state.graph.map(_.isDeletedNow(nodeId, directParentIds))

            val inCycle = transitiveParentIds.contains(nodeId)

            if(inCycle)
              renderMessageRow(state, nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, showReplyField = showReplyField, isExpanded = Rx(false), inCycle = true)
            else {
              val isExpanded = Rx {
                // we need to get the newest node content from the graph
                val graph = state.graph()
                val user = state.user()
                graph.isExpanded(user.id, nodeId).getOrElse(false)
              }

              val showExpandedThread = Rx {
                (isExpanded() || showReplyField())
              }

              VDomModifier(
                renderMessageRow(state, nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, isExpanded = isExpanded, showReplyField = showReplyField, inCycle = false),
                Rx {
                  showExpandedThread().ifTrue[VDomModifier] {
                    renderExpandedThread(state, transitiveParentIds, selectedNodes, nodeId, nodeIdList, showReplyField)
                  }
                },
              )
            }
          })
        }
      )
    )
  }

  private def renderExpandedThread(state: GlobalState, transitiveParentIds: Set[NodeId], selectedNodes: Var[Set[SelectedNode]], nodeId: NodeId, nodeIdList: List[ParentId], showReplyField: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    val bgColor = BaseColors.pageBgLight.copy(h = NodeColor.hue(nodeId)).toHex
    VDomModifier(
      cls := "chat-expanded-thread",
      backgroundColor := bgColor,

      drag(target = DragItem.Thread(nodeId :: Nil)),

      expandedNodeContentWithLeftTagColor(state, nodeId).apply(
        cls := "chat-thread-messages-outer",
        div(
          cls := "chat-thread-messages",
          width := "100%",
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
      val graph = state.graph.now
      val user = state.user.now
      val addNodeChange = GraphChanges.addNodeWithParent(Node.MarkdownMessage(str), ParentId(nodeId) :: Nil)
      val expandChange = if(!graph.isExpanded(user.id, nodeId).getOrElse(false)) GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), user.id) else GraphChanges.empty
      val changes = addNodeChange merge expandChange
      state.eventProcessor.changes.onNext(changes)
    } else Future.successful(Continue)

    def blurAction(value: String):Unit = if(value.isEmpty) {
      window.setTimeout(() => showReplyField() = false, 150)
    }

    inputRow(state, submitAction = handleInput, blurAction = Some(blurAction), autoFocus = true, showMarkdownHelp = true, enforceUserName = true, placeholder = Placeholder.newMessage).apply(
      closeButton(
        padding := "15px",
        onClick.stopPropagation foreach { showReplyField() = false },
      ),
    )
  }

  private def renderMessageRow(state: GlobalState, nodeId: NodeId, directParentIds:Iterable[ParentId], selectedNodes: Var[Set[SelectedNode]], isDeletedNow: Rx[Boolean], showReplyField: Var[Boolean], isExpanded:Rx[Boolean], inCycle:Boolean)(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      selectedNodes().exists(selected => selected.nodeId == nodeId && selected.directParentIds == directParentIds)
    }

    val renderedMessage = renderMessage(state, nodeId, directParentIds, isDeletedNow = isDeletedNow, renderedMessageModifier = VDomModifier(VDomModifier.ifTrue(inCycle)(
        Styles.flex,
        alignItems.center,
        freeSolid.faSyncAlt,
        paddingRight := "3px",
        backgroundColor := "#CCC",
        color := "#666",
        boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
      ),
      messageDragOptions(state, nodeId, selectedNodes),
    ))
    val controls = msgControls(state, nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, replyAction = showReplyField() = !showReplyField.now)
    val checkbox = msgCheckbox(state, nodeId, selectedNodes, newSelectedNode = SelectedNode(_, directParentIds)(showReplyField), isSelected = isSelected)
    val selectByClickingOnRow = {
      onClickOrLongPress foreach { longPressed =>
        if(longPressed) selectedNodes.update(_ + SelectedNode(nodeId, directParentIds)(showReplyField))
        else {
          // stopPropagation prevents deselecting by clicking on background
          val selectionModeActive = selectedNodes.now.nonEmpty
          if(selectionModeActive) selectedNodes.update(_.toggle(SelectedNode(nodeId, directParentIds)(showReplyField)))
        }
      }
    }
    val expandCollapseButton = Rx{
      VDomModifier.ifNot(inCycle)(renderExpandCollapseButton(state, nodeId, isExpanded))
    }

    div(
      cls := "chat-row",
      Styles.flex,

      expandCollapseButton,

      isSelected.map(_.ifTrue[VDomModifier](backgroundColor := "rgba(65,184,255, 0.5)")),
      selectByClickingOnRow,
      checkbox,

      renderedMessage,
      messageTags(state, nodeId),
      controls,
    )
  }

  def calculateThreadMessages(parentIds: Iterable[NodeId], graph: Graph): js.Array[Int] = {
    // most nodes don't have any children, so we skip the expensive accumulation
    if(parentIds.size == 1 && !graph.hasChildren(parentIds.head)) return js.Array[Int]()

    val nodeSet = ArraySet.create(graph.nodes.length)
    //TODO: performance: depthFirstSearchMultiStartForeach which starts at multiple start points and accepts a function
    parentIds.foreach { parentId =>
      graph.idToIdxForeach(parentId) { parentIdx =>
        graph.childrenIdx.foreachElement(parentIdx) { childIdx =>
          val childNode = graph.nodes(childIdx)
          if(childNode.isInstanceOf[Node.Content] && (childNode.role == NodeRole.Message || (childNode.role.isInstanceOf[NodeRole.ContentRole] && graph.childrenIdx(childIdx).exists(idx => NodeRole.Message == graph.nodes(idx).role))))
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
}
