package wust.webApp.views

import flatland._
import fontAwesome.freeSolid
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.util.algorithm.dfs
import wust.util.collection._
import wust.util.macros.InlineList
import wust.webApp.Icons
import wust.webApp.dragdrop.{DragItem, _}
import wust.webApp.state.GlobalState.SelectedNode
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}

import scala.collection.{breakOut, mutable}
import scala.scalajs.js

object ChatView {
  import SharedViewElements._

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val scrollHandler = new ScrollBottomHandler
    val inputFieldFocusTrigger = SinkSourceHandler.publish[Unit]

    val currentReply = Var(Set.empty[NodeId])
    currentReply.foreach{ _ =>
      inputFieldFocusTrigger.onNext(())
    }
    val pinReply = Var(false)

    val pageCounter = SinkSourceHandler.publish[Int]
    val shouldLoadInfinite = Var[Boolean](false)

    div(
      keyed,
      Styles.flex,
      flexDirection.column,

      selectedNodesBar(currentReply, inputFieldFocusTrigger),

      div(
        // InfiniteScroll must stay outside ChatHistory (don't know why exactly...)
        InfiniteScroll.onInfiniteScrollUp(shouldLoadInfinite) --> pageCounter,
        chatHistory(
          focusState,
          currentReply,
          scrollHandler,
          inputFieldFocusTrigger,
          pageCounter,
          shouldLoadInfinite,
        ),
      ),
      onGlobalEscape.useLazy(Set.empty[NodeId]) --> currentReply,
      renderCurrentReply( focusState, inputFieldFocusTrigger, currentReply, pinReply),
      chatInput( focusState, currentReply, pinReply, scrollHandler, inputFieldFocusTrigger)
    )
  }

  private def selectedNodesBar(
    currentReply: Var[Set[NodeId]],
    inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit],
  )(implicit ctx: Ctx.Owner) = VDomModifier (
    position.relative, // for absolute positioning of selectednodes
    SelectedNodes(
      selectedNodeActions(prependActions = additionalNodeActions(currentReply, inputFieldFocusTrigger)),
      (_, _) => Nil
      ).apply(
        position.absolute,
        width := "100%"
      )
    )

  private def chatHistory(

    focusState: FocusState,
    currentReply: Var[Set[NodeId]],

    scrollHandler: ScrollBottomHandler,
    inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit],
    externalPageCounter: SourceStream[Int],
    shouldLoadInfinite: Var[Boolean]
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val pageParentId = focusState.focusedId
    val initialPageCounter = 35
    val pageCounter = Var(initialPageCounter)

    val messages = Rx {
      GlobalState.screenSize() // on screensize change, rerender whole chat history
      val graph = GlobalState.graph()

      selectChatMessages(pageParentId, graph)
    }

    var prevMessageSize = -1
    messages.foreach { messages =>
      if (prevMessageSize != messages.length) pageCounter() = initialPageCounter
      prevMessageSize = messages.length
    }

    Rx {
      shouldLoadInfinite() = !GlobalState.isLoading() && messages().length > pageCounter()
    }

    def outerDragOptions(pageId: NodeId) = VDomModifier(
      drag(target = DragItem.Workspace(pageId)),
      registerDragContainer( DragContainer.Chat),
    )

    VDomModifier(
      cls := "chat-history",

      Rx {
        val graph = GlobalState.graph()
        val pageCount = pageCounter()
        val groups = calculateMessageGrouping(messages().takeRight(pageCount), graph, pageParentId)

        VDomModifier(
          groups.nonEmpty.ifTrue[VDomModifier](
            if (GlobalState.screenSize.now == ScreenSize.Small) padding := "50px 0px 5px 5px"
            else padding := "50px 0px 5px 20px"
          ),
          groups.map { group =>
            thunkRxFun( graph, group, pageParentId, currentReply, inputFieldFocusTrigger)
          }
        )
      },

      emitter(externalPageCounter) foreach { pageCounter.update(c => Math.min(c + initialPageCounter, messages.now.length)) },
      outerDragOptions(focusState.focusedId),

      // clicking on background deselects
      onClick.onlyOwnEvents.foreach { e =>
        GlobalState.clearSelectedNodes()
      },
      scrollHandler.modifier,
    )
  }

  private def selectChatMessages(pageParentId: NodeId, graph: Graph): js.Array[Int] = {
    graph.idToIdxFold(pageParentId)(js.Array[Int]()) { pageParentIdx =>
      val nodeSet = ArraySet.create(graph.nodes.length)
      var nodeCount = 0

      dfs.foreach(_ (pageParentIdx), dfs.afterStart, graph.childrenIdx, append = { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        node.role match {
          case NodeRole.Message =>
            nodeSet.add(nodeIdx)
            nodeCount += 1
          case _ =>
        }
      })

      val nodes = new js.Array[Int](nodeCount)
      nodeSet.foreachIndexAndElement((i, nodeIdx) => nodes(i) = nodeIdx)
      sortByCreated(nodes, graph)
      nodes
    }
  }

  private def calculateMessageGrouping(messages: js.Array[Int], graph: Graph, pageParentId: NodeId): Array[Array[Int]] = {
    if (messages.isEmpty) Array[Array[Int]]()
    else {
      val pageParentIdx = graph.idToIdxOrThrow(pageParentId)
      val groupsBuilder = mutable.ArrayBuilder.make[Array[Int]]
      val currentGroupBuilder = new mutable.ArrayBuilder.ofInt
      var lastAuthor: Int = -2 // to distinguish between no author and no previous group
      var lastParents: IndexedSeq[Int] = null
      messages.foreach { message =>
        val author: Int = graph.nodeCreatorIdx(message) // without author, returns -1
        val parents: IndexedSeq[Int] = graph.parentsIdx(message).filter{ idx =>
          val role = graph.nodes(idx).role
          role != NodeRole.Stage && role != NodeRole.Tag
        } // is there a more efficient way to ignore certain kinds of parent roles?

        @inline def differentParents = lastParents != null && parents != lastParents
        @inline def differentAuthors = lastAuthor != -2 && author != lastAuthor
        @inline def noParents = lastParents != null && parents.forall(_ == pageParentIdx)
        @inline def introduceGroupSplit(): Unit = {
          groupsBuilder += currentGroupBuilder.result()
          currentGroupBuilder.clear()
        }

        if (differentParents) {
          introduceGroupSplit()
        } else if (differentAuthors && noParents) {
          introduceGroupSplit()
        }

        currentGroupBuilder += message
        lastAuthor = author
        lastParents = parents
      }
      groupsBuilder += currentGroupBuilder.result()
      groupsBuilder.result()
    }
  }


  private def thunkRxFun(graph: Graph, group: Array[Int], pageParentId: NodeId, currentReply: Var[Set[NodeId]], inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit]): ThunkVNode = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.viewMap(graph.nodeIds)
    val key = nodeIds.head.toString
    val commonParentIds: Seq[NodeId] = graph.parentsIdx(group(0)).filter{ parentIdx =>
      val parentNode = graph.nodes(parentIdx)

      InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task)(parentNode.role)
    }.viewMap(graph.nodeIds)
    div.thunk(key)(nodeIds, GlobalState.screenSize.now, commonParentIds, pageParentId)(Ownable(implicit ctx => thunkGroup( graph, group, pageParentId, currentReply, inputFieldFocusTrigger = inputFieldFocusTrigger)))
  }

  private def thunkGroup(groupGraph: Graph, group: Array[Int], pageParentId: NodeId, currentReply: Var[Set[NodeId]], inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {

    val groupHeadId = groupGraph.nodeIds(group(0))
    val author: Rx[Option[Node.User]] = Rx {
      val graph = GlobalState.graph()
      graph.idToIdx(groupHeadId).flatMap(graph.nodeCreator)
    }
    val creationEpochMillis = groupGraph.nodeCreated(group(0))

    val pageParentIdx = groupGraph.idToIdx(pageParentId)
    val commonParentsIdx = pageParentIdx.fold(IndexedSeq.empty[Int]){ pageParentIdx =>
      groupGraph.parentsIdx(group(0)).filter{ idx =>
        val role = groupGraph.nodes(idx).role
        idx != pageParentIdx && role != NodeRole.Stage && role != NodeRole.Tag
      }.sortBy(idx => groupGraph.nodeCreated(idx))
    }
    @inline def inReplyGroup = commonParentsIdx.nonEmpty
    val commonParentIds = commonParentsIdx.viewMap(groupGraph.nodeIds)

    def renderCommonParents(implicit ctx: Ctx.Owner) = div(
      cls := "chat-common-parents",
      Styles.flex,
      flexWrap.wrap,
      commonParentsIdx.map { parentIdx =>
        renderParentMessage( groupGraph.nodeIds(parentIdx), isDeletedNow = false, currentReply = currentReply, inputFieldFocusTrigger)
      }
    )

    val bgColor = NodeColor.mixHues(commonParentIds).map(hue => BaseColors.pageBgLight.copy(h = hue).toHex)
    val lineColor = NodeColor.mixHues(commonParentIds).map(hue => BaseColors.tag.copy(h = hue).toHex)

    var _previousNodeId: Option[NodeId] = None

    VDomModifier(
      cls := "chat-group-outer-frame",
      GlobalState.largeScreen.ifTrue[VDomModifier](if (inReplyGroup) paddingLeft := "40px" else author.map(_.map(user => bigAuthorAvatar(user)(onClickDirectMessage( user))))),
      div(
        cls := "chat-group-inner-frame",
        inReplyGroup.ifFalse[VDomModifier](author.map{ author =>
          val header = chatMessageHeader( author, creationEpochMillis, groupHeadId, GlobalState.largeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar)))
          header(GlobalState.smallScreen.ifTrue[VDomModifier](VDomModifier(paddingLeft := "0")))
        }),

        div(
          cls := "chat-expanded-thread",
          backgroundColor :=? bgColor,

          VDomModifier.ifTrue(inReplyGroup)(
            renderCommonParents,
            drag(target = DragItem.Thread(commonParentIds)),
          ),

          div(
            cls := "chat-thread-messages-outer chat-thread-messages",
            lineColor.map(lineColor => borderLeft := s"3px solid ${lineColor}"),

            group.map { nodeIdx =>
              val nodeId = groupGraph.nodeIds(nodeIdx)
              val previousNodeId = _previousNodeId
              _previousNodeId = Some(nodeId)

              div.thunk(nodeId.toStringFast)(GlobalState.screenSize.now)(Ownable { implicit ctx =>
                // the parent ids of this node are a dependency of the thunk above us thunkRxFun
                // therefore we know they will never change and we can use the groupGraph
                // and statically calculate the parentIds and use inReplyGroup here.
                // isDeletedNow on the other hand needs to be dynamically calculated.
                val parentIdxs = groupGraph.parentsIdx(nodeIdx)
                val parentIds: Set[ParentId] = parentIdxs.map(idx => ParentId(groupGraph.nodeIds(idx)))(breakOut)

                val isDeletedNow = GlobalState.graph.map(g => g.idToIdx(nodeId).exists(idx => g.isDeletedNowIdx(idx, g.parentsIdx(idx))))

                renderMessageRow( pageParentId, nodeId, parentIds, inReplyGroup = inReplyGroup, isDeletedNow = isDeletedNow, currentReply = currentReply, inputFieldFocusTrigger = inputFieldFocusTrigger, previousNodeId = previousNodeId)
              })
            },
          )
        )
      )
    )
  }

  private def renderMessageRow(pageParentId: NodeId, nodeId: NodeId, directParentIds: Iterable[ParentId], inReplyGroup: Boolean, isDeletedNow: Rx[Boolean], currentReply: Var[Set[NodeId]], inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit], previousNodeId: Option[NodeId])(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      GlobalState.selectedNodes().exists(_.nodeId == nodeId)
    }

    def replyAction = {
      currentReply.update(_ ++ Set(nodeId))
      inputFieldFocusTrigger.onNext(Unit)
    }

    def messageHeader: VDomModifier = if (inReplyGroup) Rx {
      val graph = GlobalState.graph()
      val idx = graph.idToIdxOrThrow(nodeId)
      val author: Option[Node.User] = graph.nodeCreator(idx)
      if (previousNodeId.fold(true)(id => graph.nodeCreator(graph.idToIdxOrThrow(id)).map(_.id) != author.map(_.id))) chatMessageHeader( author, graph.nodeCreated(idx), nodeId, author.map(smallAuthorAvatar))
      else VDomModifier.empty
    }
    else VDomModifier.empty

    val renderedMessage = renderMessage( nodeId, directParentIds, isDeletedNow = isDeletedNow, renderedMessageModifier = messageDragOptions( nodeId))
    val controls = msgControls( nodeId, directParentIds, isDeletedNow = isDeletedNow, replyAction = replyAction)
    val checkbox = msgCheckbox( nodeId, newSelectedNode = SelectedNode(_, directParentIds), isSelected = isSelected)
    val selectByClickingOnRow = {
      onClickOrLongPress foreach { longPressed =>
        if (longPressed) GlobalState.addSelectedNode(SelectedNode(nodeId, directParentIds))
        else {
          // stopPropagation prevents deselecting by clicking on background
          val selectionModeActive = GlobalState.selectedNodes.now.nonEmpty
          if (selectionModeActive) GlobalState.toggleSelectedNode(SelectedNode(nodeId, directParentIds))
        }
      }
    }

    div(
      messageHeader,
      div(
        cls := "chat-row",
        Styles.flex,

        isSelected.map(_.ifTrue[VDomModifier](backgroundColor := "rgba(65,184,255, 0.5)")),
        selectByClickingOnRow,
        renderedMessage,
        checkbox,
        controls,
      )
    )
  }

  private def renderParentMessage(
    nodeId: NodeId,
    isDeletedNow: Boolean,
    currentReply: Var[Set[NodeId]],
    inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit],
    pinReply: Option[Var[Boolean]] = None
  )(implicit ctx: Ctx.Owner) = {
    val authorAndCreated = Rx {
      val graph = GlobalState.graph()
      graph.idToIdxFold(nodeId)((Option.empty[Node.User], Option.empty[EpochMilli])) { nodeIdx =>
        (graph.authorsByIndex(nodeIdx).headOption, Some(graph.nodeCreated(nodeIdx)))
      }
    }

    val node = Rx{
      val graph = GlobalState.graph()
      graph.nodesById(nodeId)
    }

    val parentIds = Rx {
      val graph = GlobalState.graph()
      graph.parents(nodeId)
    }

    div(
      minWidth := "0", // fixes word-break in flexbox
      Rx {
        val tuple = authorAndCreated()
        val (author, creationEpochMillis) = tuple
        node().map(node =>
          div(
            keyed(node.id),
            chatMessageHeader( author)( padding := "2px"),
            borderLeft := s"3px solid ${accentColor(nodeId).toHex}",
            paddingRight := "5px",
            paddingBottom := "3px",
            backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(nodeId)).toHex,
            div(
              Styles.flex,
              paddingLeft := "0.5em",
              div(
                cls := "nodecard-content",
                Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, node.id),
                fontSize.smaller,
                node.role match {
                  case NodeRole.Task => VDomModifier(
                    renderMessage(nodeId, parentIds(), Rx(isDeletedNow) )
                  )
                  case _ => VDomModifier(
                    renderNodeData( node, maxLength = Some(100))
                  )
                }
              ),
              div(cls := "fa-fw", Icons.reply, padding := "3px 20px 3px 5px", onClick.stopPropagation foreach { currentReply.update(_ ++ Set(nodeId)) }, cursor.pointer),
              div(cls := "fa-fw", Icons.zoom, padding := "3px 20px 3px 5px", onClick.stopPropagation foreach {
                GlobalState.focus(node.id)
                GlobalState.clearSelectedNodes()
                FeatureState.use(Feature.ZoomIntoMessage)
              }, cursor.pointer),
              pinReply.map{ pinReply => div(cls := "fa-fw", freeSolid.faThumbtack, Rx { pinReply().ifFalse[VDomModifier](opacity := 0.4) }, padding := "3px 20px 3px 5px", onClick.stopPropagation foreach { pinReply() = !pinReply.now; inputFieldFocusTrigger.onNext(()); () }, cursor.pointer) },
            )
          ))
      },
      // VDomModifier.ifTrue(isDeletedNow)(opacity := 0.5, "(archived)") // TODO: does not work correctly. Always shows nested messages as deleted
    )
  }

  //TODO share code with threadview?
  private def additionalNodeActions(currentReply: Var[Set[NodeId]], inputFieldTriggerFocus: SinkSourceHandler.Simple[Unit])(implicit ctx: Ctx.Owner): Boolean => List[VNode] = canWriteAll => List(
    replyButton(
      onClick foreach {
        currentReply() = GlobalState.selectedNodes.now.map(_.nodeId)(breakOut):Set[NodeId]
        GlobalState.clearSelectedNodes()
        inputFieldTriggerFocus.onNext(Unit)
        ()
      }
    ),
    SharedViewElements.createNewButton()
  )

  private def renderCurrentReply(
    focusState: FocusState,
    inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit],
    currentReply: Var[Set[NodeId]],
    pinReply: Var[Boolean]
  )(implicit ctx: Ctx.Owner): Rx[BasicVNode] = {
    Rx {
      val graph = GlobalState.graph()
      div(
        Styles.flexStatic,

        Styles.flex,
        currentReply().map { replyNodeId =>
          val isDeletedNow = graph.isDeletedNow(replyNodeId, focusState.focusedId)
          div(
            padding := "5px",
            minWidth := "0", // fixes overflow-wrap for parent preview
            backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(replyNodeId)).toHex,
            div(
              Styles.flex,
              renderParentMessage(replyNodeId, isDeletedNow, currentReply, inputFieldFocusTrigger, Some(pinReply)),
              closeButton(
                marginLeft.auto,
                onTap foreach { currentReply.update(_ - replyNodeId) }
              ),
            )
          )
        }(breakOut): Seq[VDomModifier]
      )
    }
  }

  private def chatInput(

    focusState: FocusState,
    currentReply: Var[Set[NodeId]],
    pinReply: Var[Boolean],
    scrollHandler: ScrollBottomHandler,
    inputFieldFocusTrigger: SinkSourceHandler.Simple[Unit],
  )(implicit ctx: Ctx.Owner) = {
    val bgColor = Rx{ NodeColor.mixHues(currentReply()).map(hue => BaseColors.pageBgLight.copy(h = hue).toHex) }
    val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

    def submitAction(sub: InputRow.Submission): Unit = {

      val replyNodes: Set[NodeId] = {
        if (currentReply.now.nonEmpty) currentReply.now
        else Set(focusState.focusedId)
      }

      //TODO: share code with threadview
      val basicNode = Node.MarkdownMessage(sub.text)
      val basicGraphChanges = GraphChanges.addNodeWithParent(basicNode, ParentId(replyNodes)) merge sub.changes(basicNode.id)
      fileUploadHandler.now match {
        case None             => GlobalState.submitChanges(basicGraphChanges)
        case Some(uploadFile) => AWS.uploadFileAndCreateNode( uploadFile, fileId => basicGraphChanges merge GraphChanges.connect(Edge.LabeledProperty)(basicNode.id, EdgeData.LabeledProperty.attachment, PropertyId(fileId)))
      }

      FeatureState.use(Feature.CreateMessageInChat)
      if(currentReply.now.nonEmpty) FeatureState.use(Feature.ReplyToMessageInChat)

      if (!pinReply.now) currentReply() = Set.empty[NodeId]
      fileUploadHandler() = None
      scrollHandler.scrollToBottomInAnimationFrame()
    }

    InputRow(

      Some(focusState),
      submitAction,
      fileUploadHandler = Some(fileUploadHandler),
      scrollHandler = Some(scrollHandler),
      preFillByShareApi = true,
      autoFocus = !BrowserDetect.isMobile && !focusState.isNested,
      triggerFocus = inputFieldFocusTrigger,
      showMarkdownHelp = !BrowserDetect.isMobile,
      enforceUserName = true,
      placeholder = Placeholder.newMessage,
      enableEmojiPicker = true,
    ).apply(
        Styles.flexStatic,
        margin := "3px",
        Rx{ backgroundColor :=? bgColor() }
      )
  }


}
