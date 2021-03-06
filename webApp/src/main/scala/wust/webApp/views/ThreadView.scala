package wust.webApp.views

import flatland._
import fontAwesome._
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import colibri._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor
import wust.util._
import wust.util.collection._
import wust.webApp.Icons
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.GlobalState.SelectedNode
import wust.webApp.state.{FocusState, GlobalState, Placeholder, TraverseState}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}

import scala.collection.mutable
import scala.scalajs.js

object ThreadView {
  import SharedViewElements._
  //TODO: deselect after dragging
  //TODO: fix "remove tag" in cycles

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val scrollHandler = new ScrollBottomHandler
    val inputFieldFocusTrigger = Subject.publish[Unit]


    val pageCounter = Subject.publish[Int]
    val shouldLoadInfinite = Var[Boolean](false)

    div(
      cls := "threadview",

      keyed,
      Styles.flex,
      flexDirection.column,

      selectedNodesBar(inputFieldFocusTrigger),
      div(
        // InfiniteScroll must stay outside ChatHistory (don't know why exactly...)
        InfiniteScroll.onInfiniteScrollUp(shouldLoadInfinite) --> pageCounter,
        chatHistory( focusState, TraverseState(focusState.focusedId), pageCounter, shouldLoadInfinite, scrollHandler),
      ),
      chatInput( focusState, scrollHandler, inputFieldFocusTrigger)
    )
  }

  private def selectedNodesBar(
    inputFieldFocusTrigger: Subject[Unit],
  )(implicit ctx: Ctx.Owner) = VDomModifier (
    position.relative, // for absolute positioning of selectednodes
    SelectedNodes(
      selectedNodeActions(),
      (_, _) => Nil
    ).apply(
      position.absolute,
      width := "100%"
    ),
  )

  private def chatHistory(
    focusState: FocusState,
    traverseState: TraverseState,
    externalPageCounter: Observable[Int],
    shouldLoadInfinite: Var[Boolean],
    scrollHandler: ScrollBottomHandler,
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val initialPageCounter = 35
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

        renderThreadGroups( focusState, traverseState, messages().takeRight(pageCount), Set(ParentId(focusState.focusedId)), Set(focusState.focusedId), true)
      },

      emitter(externalPageCounter) foreach { pageCounter.update(c => Math.min(c + initialPageCounter, messages.now.length)) },
      registerDragContainer,
      drag(target = DragItem.Workspace(focusState.focusedId)),


      // clicking on background deselects
      onClick foreach { e => if(e.currentTarget == e.target) GlobalState.clearSelectedNodes() },
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
              (childNode.role == NodeRole.Message || childNode.role == NodeRole.Task ||
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


  private def renderThreadGroups(focusState: FocusState, traverseState: TraverseState, messages: js.Array[Int], directParentIds: Iterable[ParentId], transitiveParentIds: Set[NodeId], isTopLevel:Boolean = false)(implicit ctx: Ctx.Data): VDomModifier = {
    val graph = GlobalState.graph()
    val groups = calculateThreadMessageGrouping(messages, graph)

    VDomModifier(
      // large padding-top to have space for selectedNodes bar
      (isTopLevel && groups.nonEmpty).ifTrue[VDomModifier](padding := "50px 0px 5px 20px"),
      groups.map { group =>
        thunkRxFun( focusState, traverseState, graph, group, directParentIds, transitiveParentIds, isTopLevel)
      },
    )
  }

  private def thunkRxFun(focusState: FocusState, traverseState: TraverseState, groupGraph: Graph, group: Array[Int], directParentIds: Iterable[ParentId], transitiveParentIds: Set[NodeId], isTopLevel:Boolean = false): VDomModifier = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.viewMap(groupGraph.nodeIds)
    val key = nodeIds.head.toString

    div.thunk(key)(nodeIds, GlobalState.screenSize.now)(Ownable { implicit ctx =>
      thunkGroup( focusState, traverseState, groupGraph, group, directParentIds = directParentIds, transitiveParentIds = transitiveParentIds, isTopLevel = isTopLevel)
    })
  }

  private def thunkGroup(focusState: FocusState, traverseState: TraverseState, groupGraph: Graph, group: Array[Int], directParentIds:Iterable[ParentId], transitiveParentIds: Set[NodeId], isTopLevel:Boolean)(implicit ctx: Ctx.Owner) = {
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
      topLevelAndLargeScreen.ifTrue[VDomModifier](author.map(_.map(user => bigAuthorAvatar(user)))),

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
              renderMessageRow( focusState, traverseState, nodeId, directParentIds, isDeletedNow = isDeletedNow, showReplyField = showReplyField, isExpanded = Rx(false), inCycle = true)
            else {
              val isExpanded = Rx {
                // we need to get the newest node content from the graph
                val graph = GlobalState.graph()
                val userId = GlobalState.userId()
                graph.isExpanded(userId, nodeId).getOrElse(graph.hasChildren(nodeId))
              }

              val showExpandedThread = Rx {
                (isExpanded() || showReplyField())
              }

              VDomModifier(
                renderMessageRow( focusState, traverseState, nodeId, directParentIds, isDeletedNow = isDeletedNow, isExpanded = isExpanded, showReplyField = showReplyField, inCycle = false),
                Rx {
                  showExpandedThread().ifTrue[VDomModifier] {
                    renderExpandedThread( focusState, traverseState, transitiveParentIds, nodeId, nodeIdList, showReplyField)
                  }
                },
              )
            }
          })
        }
      )
    )
  }

  private def renderExpandedThread(focusState: FocusState, traverseState: TraverseState, transitiveParentIds: Set[NodeId], nodeId: NodeId, nodeIdList: List[ParentId], showReplyField: Var[Boolean])(implicit ctx: Ctx.Owner) = {
    VDomModifier(
      cls := "chat-expanded-thread",
      Rx{ 
        VDomModifier(
          backgroundColor :=? NodeColor.pageBgLight.of(nodeId, GlobalState.graph()),
          borderLeft := s"3px solid ${NodeColor.accent.of(nodeId, GlobalState.graph())}",
        )
      },
      marginLeft := "-3px",

      drag(target = DragItem.Thread(nodeId :: Nil)),

      expandedNodeContentWithLeftTagColor( nodeId).apply(
        cls := "chat-thread-messages-outer",
        div(
          cls := "chat-thread-messages",
          width := "100%",
          Rx {
            renderThreadGroups( focusState, traverseState.step(nodeId), calculateThreadMessages(nodeIdList, GlobalState.graph()), directParentIds = nodeIdList, transitiveParentIds = transitiveParentIds + nodeId)
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
      Icons.reply,
      " reply",
      marginTop := "3px",
      marginLeft := "8px",
      onClickDefault foreach { showReplyField() = true }
    )
  }

  private def threadReplyField(focusState: FocusState, nodeId: NodeId, showReplyField: Var[Boolean])(implicit ctx:Ctx.Owner): VNode = {

    def handleInput(sub: InputRow.Submission): Unit = if (sub.text.nonEmpty) {
      val graph = GlobalState.graph.now
      val user = GlobalState.user.now
      val createdNode = Node.MarkdownMessage(sub.text)
      val addNodeChange = GraphChanges.addNodeWithParent(createdNode, ParentId(nodeId) :: Nil) merge sub.changes(createdNode.id)
      val expandChange = if(!graph.isExpanded(user.id, nodeId).getOrElse(false)) GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), user.id) else GraphChanges.empty
      val changes = addNodeChange merge expandChange
      GlobalState.submitChanges(changes)
      ()
    }

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
        onClickDefault foreach { showReplyField() = false },
      ),
    )
  }

  private def renderMessageRow(focusState: FocusState, traverseState: TraverseState, nodeId: NodeId, directParentIds:Iterable[ParentId], isDeletedNow: Rx[Boolean], showReplyField: Var[Boolean], isExpanded:Rx[Boolean], inCycle:Boolean)(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      GlobalState.selectedNodes().exists(selected => selected.nodeId == nodeId && selected.directParentIds == directParentIds)
    }

    val renderedMessage = renderMessage(
      focusState,
      traverseState,
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
      messageDragOptions( nodeId),
      ),
    )
    val controls = msgControls( nodeId, directParentIds, isDeletedNow = isDeletedNow, replyAction = showReplyField() = !showReplyField.now)
    val checkbox = msgCheckbox( nodeId, newSelectedNode = SelectedNode(_, directParentIds), isSelected = isSelected)
    val selectByClickingOnRow = {
      onClickOrLongPress foreach { longPressed =>
        if(longPressed) GlobalState.addSelectedNode(SelectedNode(nodeId, directParentIds))
        else {
          // stopPropagation prevents deselecting by clicking on background
          val selectionModeActive = GlobalState.selectedNodes.now.nonEmpty
          if(selectionModeActive) GlobalState.toggleSelectedNode(SelectedNode(nodeId, directParentIds))
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
      checkbox,
      controls,
    )
  }

  private def chatInput(
    focusState: FocusState,
    scrollHandler: ScrollBottomHandler,
    inputFieldFocusTrigger: Subject[Unit],
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
