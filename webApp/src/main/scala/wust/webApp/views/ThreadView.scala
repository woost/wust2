package wust.webApp.views

import flatland._
import fontAwesome._
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.util.collection._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{FocusState, GlobalState, Placeholder}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}

import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.js

object ThreadView {
  import SharedViewElements._
  //TODO: deselect after dragging
  //TODO: fix "remove tag" in cycles

  final case class SelectedNode(nodeId:NodeId, directParentIds: Iterable[ParentId])(val showReplyField:Var[Boolean]) extends SelectedNodeBase

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val selectedNodes:Var[Set[SelectedNode]] = Var(Set.empty[SelectedNode])

    val scrollHandler = new ScrollBottomHandler
    val inputFieldFocusTrigger = PublishSubject[Unit]


    val pageCounter = PublishSubject[Int]()
    val shouldLoadInfinite = Var[Boolean](false)

    div(
      cls := "threadview",

      keyed,
      Styles.flex,
      flexDirection.column,

      selectedNodesBar( selectedNodes, inputFieldFocusTrigger),
      div(
        // InfiniteScroll must stay outside ChatHistory (don't know why exactly...)
        InfiniteScroll.onInfiniteScrollUp(shouldLoadInfinite) --> pageCounter,
        chatHistory( focusState, selectedNodes, pageCounter, shouldLoadInfinite, scrollHandler),
      ),
      chatInput( focusState, scrollHandler, inputFieldFocusTrigger)
    )
  }

  private def selectedNodesBar(
    
    selectedNodes: Var[Set[SelectedNode]],
    inputFieldFocusTrigger: PublishSubject[Unit],
  )(implicit ctx: Ctx.Owner) = VDomModifier (
    position.relative, // for absolute positioning of selectednodes
    SelectedNodes[SelectedNode](
      
      selectedNodes,
      selectedNodeActions( selectedNodes),
      (_, _) => Nil
    ).apply(
      position.absolute,
      width := "100%"
    ),
  )

  private def chatHistory(
    
    focusState: FocusState,
    selectedNodes: Var[Set[SelectedNode]],
    externalPageCounter: Observable[Int],
    shouldLoadInfinite: Var[Boolean],
    scrollHandler: ScrollBottomHandler,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val initialPageCounter = 30
    val pageCounter = Var(initialPageCounter)

    val messages = Rx {
      val graph = GlobalState.graph()

      calculateThreadMessages(List(focusState.focusedId), graph)
    }

    var prevMessageSize = -1
    messages.foreach { messages =>
      if (prevMessageSize != messages.length) pageCounter() = initialPageCounter
      prevMessageSize = messages.length
    }

    Rx {
      shouldLoadInfinite() = !GlobalState.isLoading() && messages().length > pageCounter()
    }

    VDomModifier(
      cls := "chat-history",
      Rx {
        GlobalState.screenSize() // on screensize change, rerender whole chat history
        val pageCount = pageCounter()

        renderThreadGroups( focusState, messages().takeRight(pageCount), Set(ParentId(focusState.focusedId)), Set(focusState.focusedId), selectedNodes, true)
      },

      emitter(externalPageCounter) foreach { pageCounter.update(c => Math.min(c + initialPageCounter, messages.now.length)) },
      registerDragContainer,
      drag(target = DragItem.Workspace(focusState.focusedId)),


      // clicking on background deselects
      onClick foreach { e => if(e.currentTarget == e.target) selectedNodes() = Set.empty[SelectedNode] },
      scrollHandler.modifier,
    ),
  }

  def calculateThreadMessages(parentIds: Iterable[NodeId], graph: Graph): js.Array[Int] = {
    // most nodes don't have any children, so we skip the expensive accumulation
    if(parentIds.size == 1 && !graph.hasChildren(parentIds.head)) js.Array[Int]()
    else {
      val nodeSet = ArraySet.create(graph.nodes.length)
      //TODO: performance: depthFirstSearchMultiStartForeach which starts at multiple start points and accepts a function
      parentIds.foreach { parentId =>
        graph.idToIdxForeach(parentId) { parentIdx =>
          graph.childrenIdx.foreachElement(parentIdx) { childIdx =>
            val childNode = graph.nodes(childIdx)
            if(
              childNode.isInstanceOf[Node.Content] &&
              (childNode.role == NodeRole.Message ||
                (childNode.role.isInstanceOf[NodeRole.ContentRole] &&
                  graph.childrenIdx(childIdx).exists(idx => NodeRole.Message == graph.nodes(idx).role)
                )
              )
            ) {
              nodeSet.add(childIdx)
            }
          }
        }
      }

      val nodes = js.Array[Int]()
      nodeSet.foreach(nodes += _)

      sortByDeepCreated(nodes, graph)
      nodes
    }
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


  private def renderThreadGroups(focusState: FocusState, messages: js.Array[Int], directParentIds: Iterable[ParentId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean = false)(implicit ctx: Ctx.Data): VDomModifier = {
    val graph = GlobalState.graph()
    val groups = calculateThreadMessageGrouping(messages, graph)

    VDomModifier(
      // large padding-top to have space for selectedNodes bar
      (isTopLevel && groups.nonEmpty).ifTrue[VDomModifier](padding := "50px 0px 5px 20px"),
      groups.map { group =>
        thunkRxFun( focusState, graph, group, directParentIds, transitiveParentIds, selectedNodes, isTopLevel)
      },
    )
  }

  private def thunkRxFun(focusState: FocusState, groupGraph: Graph, group: Array[Int], directParentIds: Iterable[ParentId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean = false): VDomModifier = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.viewMap(groupGraph.nodeIds)
    val key = nodeIds.head.toString

    div.thunk(key)(nodeIds, GlobalState.screenSize.now)(Ownable { implicit ctx =>
      thunkGroup( focusState, groupGraph, group, directParentIds = directParentIds, transitiveParentIds = transitiveParentIds, selectedNodes = selectedNodes, isTopLevel = isTopLevel)
    })
  }

  private def thunkGroup(focusState: FocusState, groupGraph: Graph, group: Array[Int], directParentIds:Iterable[ParentId], transitiveParentIds: Set[NodeId], selectedNodes:Var[Set[SelectedNode]], isTopLevel:Boolean)(implicit ctx: Ctx.Owner) = {
    val groupHeadId = groupGraph.nodeIds(group(0))
    val author: Rx[Option[Node.User]] = Rx {
      val graph = GlobalState.graph()
      graph.nodeCreator(graph.idToIdxOrThrow(groupHeadId))
    }
    val creationEpochMillis = groupGraph.nodeCreated(group(0))
    val firstNodeId = groupGraph.nodeIds(group(0))
    val topLevelAndLargeScreen = isTopLevel && GlobalState.largeScreen

    VDomModifier(
      cls := "chat-group-outer-frame",
      topLevelAndLargeScreen.ifTrue[VDomModifier](author.map(_.map(user => bigAuthorAvatar(user)(onClickDirectMessage( user))))),

      div(
        cls := "chat-group-inner-frame",

        // drag(target = DragItem.Message(firstNodeId)),

        author.map(author => chatMessageHeader( author, creationEpochMillis, groupHeadId, topLevelAndLargeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar)))),
        group.map { nodeIdx =>
          val nodeId = ParentId(groupGraph.nodeIds(nodeIdx))

          div.thunk(nodeId.toStringFast)(GlobalState.screenSize.now)(Ownable { implicit ctx =>
            val nodeIdList = nodeId :: Nil

            val showReplyField = Var(false)

            val isDeletedNow =  GlobalState.graph.map(_.isDeletedNow(nodeId, directParentIds))

            val inCycle = transitiveParentIds.contains(nodeId)

            if(inCycle)
              renderMessageRow( nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, showReplyField = showReplyField, isExpanded = Rx(false), inCycle = true)
            else {
              val isExpanded = Rx {
                // we need to get the newest node content from the graph
                val graph = GlobalState.graph()
                val user = GlobalState.user()
                graph.isExpanded(user.id, nodeId).getOrElse(graph.hasChildren(nodeId))
              }

              val showExpandedThread = Rx {
                (isExpanded() || showReplyField())
              }

              VDomModifier(
                renderMessageRow( nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, isExpanded = isExpanded, showReplyField = showReplyField, inCycle = false),
                Rx {
                  showExpandedThread().ifTrue[VDomModifier] {
                    renderExpandedThread( focusState, transitiveParentIds, selectedNodes, nodeId, nodeIdList, showReplyField)
                  }
                },
              )
            }
          })
        }
      )
    )
  }

  private def renderExpandedThread(focusState: FocusState, transitiveParentIds: Set[NodeId], selectedNodes: Var[Set[SelectedNode]], nodeId: NodeId, nodeIdList: List[ParentId], showReplyField: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    val bgColor = BaseColors.pageBgLight.copy(h = NodeColor.hue(nodeId)).toHex
    VDomModifier(
      cls := "chat-expanded-thread",
      backgroundColor := bgColor,
      borderLeft := s"3px solid ${NodeColor.accentColor(nodeId).toHex}",
      marginLeft := "-3px",

      drag(target = DragItem.Thread(nodeId :: Nil)),

      expandedNodeContentWithLeftTagColor( nodeId).apply(
        cls := "chat-thread-messages-outer",
        div(
          cls := "chat-thread-messages",
          width := "100%",
          Rx {
            renderThreadGroups( focusState, calculateThreadMessages(nodeIdList, GlobalState.graph()), directParentIds = nodeIdList, transitiveParentIds = transitiveParentIds + nodeId, selectedNodes = selectedNodes)
          },
          Rx {
            if(showReplyField()) threadReplyField( focusState, nodeId, showReplyField)
            else threadReplyButton( nodeId, showReplyField)
          }
        )
      )
    )
  }

  private def threadReplyButton(nodeId:NodeId, showReplyField: Var[Boolean]) = {
    div(
      cls := "chat-replybutton",
      freeSolid.faReply,
      " reply",
      marginTop := "3px",
      marginLeft := "8px",
      onClick.stopPropagation foreach { showReplyField() = true }
    )
  }

  private def threadReplyField(focusState: FocusState, nodeId: NodeId, showReplyField: Var[Boolean])(implicit ctx:Ctx.Owner): VNode = {

    def handleInput(sub: InputRow.Submission): Future[Ack] = if (sub.text.nonEmpty) {
      val graph = GlobalState.graph.now
      val user = GlobalState.user.now
      val createdNode = Node.MarkdownMessage(sub.text)
      val addNodeChange = GraphChanges.addNodeWithParent(createdNode, ParentId(nodeId) :: Nil) merge sub.changes(createdNode.id)
      val expandChange = if(!graph.isExpanded(user.id, nodeId).getOrElse(false)) GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), user.id) else GraphChanges.empty
      val changes = addNodeChange merge expandChange
      GlobalState.submitChanges(changes)
    } else Future.successful(Continue)

    InputRow(
      
      Some(focusState),
      submitAction = handleInput,
      autoFocus = true,
      showMarkdownHelp = !BrowserDetect.isMobile,
      enforceUserName = true,
      placeholder = Placeholder.newMessage,
      enableEmojiPicker = true
    ).apply(
      margin := "3px",
      closeButton(
        padding := "15px",
        onClick.stopPropagation foreach { showReplyField() = false },
      ),
    )
  }

  private def renderMessageRow(nodeId: NodeId, directParentIds:Iterable[ParentId], selectedNodes: Var[Set[SelectedNode]], isDeletedNow: Rx[Boolean], showReplyField: Var[Boolean], isExpanded:Rx[Boolean], inCycle:Boolean)(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      selectedNodes().exists(selected => selected.nodeId == nodeId && selected.directParentIds == directParentIds)
    }

    val renderedMessage = renderMessage(
      
      nodeId,
      directParentIds,
      isDeletedNow = isDeletedNow,
      renderedMessageModifier = VDomModifier(VDomModifier.ifTrue(inCycle)(
        Styles.flex,
        alignItems.center,
        freeSolid.faSyncAlt,
        paddingRight := "3px",
        backgroundColor := "#CCC",
        color := "#666",
        boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
      ),
      messageDragOptions( nodeId, selectedNodes),
      ),
      selectedNodes = selectedNodes
    )
    val controls = msgControls( nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, replyAction = showReplyField() = !showReplyField.now)
    val checkbox = msgCheckbox( nodeId, selectedNodes, newSelectedNode = SelectedNode(_, directParentIds)(showReplyField), isSelected = isSelected)
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
      VDomModifier.ifNot(inCycle)(renderExpandCollapseButton( nodeId, isExpanded))
    }

    div(
      cls := "chat-row",
      Styles.flex,

      expandCollapseButton,

      isSelected.map(_.ifTrue[VDomModifier](backgroundColor := "rgba(65,184,255, 0.5)")),
      selectByClickingOnRow,

      renderedMessage,
      messageTags( nodeId),
      checkbox,
      controls,
    )
  }

  private def chatInput(
    
    focusState: FocusState,
    scrollHandler: ScrollBottomHandler,
    inputFieldFocusTrigger: PublishSubject[Unit],
  )(implicit ctx: Ctx.Owner) = {
    val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

    def submitAction(sub: InputRow.Submission) = {
      scrollHandler.scrollToBottomInAnimationFrame()
      val basicNode = Node.MarkdownMessage(sub.text)
      val basicGraphChanges = GraphChanges.addNodeWithParent(basicNode, ParentId(focusState.focusedId)) merge sub.changes(basicNode.id)
      fileUploadHandler.now match {
        case None => GlobalState.submitChanges(basicGraphChanges)
        case Some(uploadFile) => AWS.uploadFileAndCreateNode( uploadFile, fileId => basicGraphChanges merge GraphChanges.connect(Edge.LabeledProperty)(basicNode.id, EdgeData.LabeledProperty.attachment, PropertyId(fileId)))
      }

      fileUploadHandler() = None
    }

    InputRow(
      
      Some(focusState),
      submitAction,
      fileUploadHandler = Some(fileUploadHandler),
      scrollHandler = Some(scrollHandler),
      preFillByShareApi = true,
      autoFocus = !BrowserDetect.isMobile && !focusState.isNested,
      triggerFocus = inputFieldFocusTrigger,
      showMarkdownHelp = true,
      enableEmojiPicker = true,
      enforceUserName = true,
      placeholder = Placeholder.newMessage
    ).apply(
      Styles.flexStatic,
      margin := "3px",
    )
  }

}
