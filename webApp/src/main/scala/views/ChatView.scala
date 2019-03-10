package wust.webApp.views

import wust.webApp.dragdrop._
import cats.effect.IO
import fontAwesome.freeSolid
import monix.reactive.Observable
import monix.reactive.subjects.{BehaviorSubject, PublishSubject}
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.util.collection._
import wust.webApp.{BrowserDetect, Client, Icons, Ownable}
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import flatland._
import monix.eval.Task
import monix.execution.Ack
import org.scalajs.dom
import wust.api.ApiEvent
import wust.util.macros.InlineList
import wust.webApp
import wust.webApp.views.UI.ToastLevel.Success

import scala.collection.immutable
import scala.collection.{breakOut, mutable}
import scala.concurrent.Future
import scala.scalajs.js
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object ChatView {
  import SharedViewElements._

  final case class SelectedNode(nodeId: NodeId)(val directParentIds: Iterable[NodeId]) extends SelectedNodeBase

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val selectedNodes = Var(Set.empty[SelectedNode]) //TODO move up, initialize with state.selectednode. also in sync with threadview

    val scrollHandler = new ScrollBottomHandler
    val inputFieldFocusTrigger = PublishSubject[Unit]

    val currentReply = Var(Set.empty[NodeId])
    currentReply.foreach{ _ =>
      inputFieldFocusTrigger.onNext(())
    }
    val pinReply = Var(false)

    def outerDragOptions(pageId: NodeId) = VDomModifier(
      drag(target = DragItem.Workspace(pageId)),
      registerDragContainer(state, DragContainer.Chat),
    )

    val pageCounter = PublishSubject[Int]()
    val shouldLoadInfinite = Var[Boolean](false)

    div(
      keyed,
      Styles.flex,
      flexDirection.column,
      position.relative, // for absolute positioning of selectednodes
      SelectedNodes[SelectedNode](state, selectedNodeActions(state, selectedNodes, prependActions = additionalNodeActions(selectedNodes,currentReply,inputFieldFocusTrigger)), selectedSingleNodeActions(state, selectedNodes), selectedNodes).apply(
        position.absolute,
        width := "100%"
      ),
      div(
        cls := "chat-history",
        InfiniteScroll.onInfiniteScrollUp(shouldLoadInfinite) --> pageCounter,

        chatHistory(state, focusState.focusedId, currentReply, selectedNodes, inputFieldFocusTrigger, pageCounter, shouldLoadInfinite),
        outerDragOptions(focusState.focusedId),

        // clicking on background deselects
        onClick foreach { e => if(e.currentTarget == e.target) selectedNodes() = Set.empty[SelectedNode] },
        scrollHandler.modifier,
      ),
      onGlobalEscape(Set.empty[NodeId]) --> currentReply,
      Rx {
        val graph = state.graph()
        div(
          Styles.flexStatic,

          Styles.flex,
          currentReply().map { replyNodeId =>
            val isDeletedNow = graph.isDeletedNow(replyNodeId, focusState.focusedId)
            val node = graph.nodesById(replyNodeId)
            div(
              padding := "5px",
              minWidth := "0", // fixes overflow-wrap for parent preview
              backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(replyNodeId)).toHex,
              div(
                Styles.flex,
                renderParentMessage(state, node.id, isDeletedNow, selectedNodes, currentReply, inputFieldFocusTrigger, Some(pinReply)),
                closeButton(
                  marginLeft.auto,
                  onTap foreach { currentReply.update(_ - replyNodeId) }
                ),
              )
            )
          }(breakOut): Seq[VDomModifier]
        )
      },
      {
        val bgColor = Rx{ NodeColor.mixHues(currentReply()).map(hue => BaseColors.pageBgLight.copy(h = hue).toHex) }
        val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

        def submitAction(str:String): Unit = {

          val replyNodes: Set[NodeId] = {
            if(currentReply.now.nonEmpty) currentReply.now
            else Set(focusState.focusedId)
          }

          //TODO: share code with threadview
          val basicNode = Node.MarkdownMessage(str)
          val basicGraphChanges = GraphChanges.addNodeWithParent(basicNode, ParentId(replyNodes))
          fileUploadHandler.now match {
            case None => state.eventProcessor.changes.onNext(basicGraphChanges)
            case Some(uploadFile) => AWS.uploadFileAndCreateNode(state, uploadFile, fileId => basicGraphChanges merge GraphChanges.connect(Edge.LabeledProperty)(basicNode.id, EdgeData.LabeledProperty.attachment, PropertyId(fileId)))
          }

          if(!pinReply.now) currentReply() = Set.empty[NodeId]
          fileUploadHandler() = None
          scrollHandler.scrollToBottomInAnimationFrame()
        }

        inputRow(state, submitAction, fileUploadHandler = Some(fileUploadHandler), scrollHandler = Some(scrollHandler), preFillByShareApi = true, autoFocus = !BrowserDetect.isMobile && !focusState.isNested, triggerFocus = inputFieldFocusTrigger, showMarkdownHelp = true, enforceUserName = true)(ctx)(
          Styles.flexStatic,
          Rx{ backgroundColor :=? bgColor()}
        )
      }
    )
  }

  private def chatHistory(state: GlobalState, pageParentId:NodeId, currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger: PublishSubject[Unit], externalPageCounter: Observable[Int], shouldLoadInfinite: Var[Boolean])(implicit ctx: Ctx.Owner): VDomModifier = {
    val initialPageCounter = 30
    val pageCounter = Var(initialPageCounter)

    val messages = Rx {
      state.screenSize() // on screensize change, rerender whole chat history
      val graph = state.graph()

      selectChatMessages(pageParentId, graph)
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
        val graph = state.graph()
        val pageCount = pageCounter()
        val groups = calculateMessageGrouping(messages().takeRight(pageCount), graph, pageParentId)

        VDomModifier(
          groups.nonEmpty.ifTrue[VDomModifier](
            if(state.screenSize.now == ScreenSize.Small) padding := "50px 0px 5px 5px"
            else padding := "50px 0px 5px 20px"
          ),
          groups.map { group =>
            thunkRxFun(state, graph, group, pageParentId, currentReply, selectedNodes, inputFieldFocusTrigger)
          }
        )
      },

      emitter(externalPageCounter) foreach { pageCounter.update(c => Math.min(c + initialPageCounter, messages.now.length)) },
    )
  }

  private def thunkRxFun(state:GlobalState, graph:Graph, group: Array[Int], pageParentId:NodeId, currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger: PublishSubject[Unit]): ThunkVNode = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.map(graph.nodeIds)
    val key = nodeIds.head.toString
    val commonParentIds: Seq[NodeId] = graph.parentsIdx(group(0)).filter{parentIdx =>
      val parentNode = graph.nodes(parentIdx)

      InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task)(parentNode.role)
    }.map(graph.nodeIds)
    div.thunk(key)(nodeIds, state.screenSize.now, commonParentIds, pageParentId)(Ownable(implicit ctx => thunkGroup(state, graph, group, pageParentId, currentReply, selectedNodes, inputFieldFocusTrigger = inputFieldFocusTrigger)))
  }

  private def thunkGroup(state: GlobalState, groupGraph: Graph, group: Array[Int], pageParentId: NodeId, currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger:PublishSubject[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {

    val groupHeadId = groupGraph.nodeIds(group(0))
    val author: Rx[Option[Node.User]] = Rx {
      val graph = state.graph()
      graph.nodeCreator(graph.idToIdx(groupHeadId))
    }
    val creationEpochMillis = groupGraph.nodeCreated(group(0))

    val pageParentIdx = groupGraph.idToIdx(pageParentId)
    val commonParentsIdx = groupGraph.parentsIdx(group(0)).filter{ idx =>
      val role = groupGraph.nodes(idx).role
      idx != pageParentIdx && role != NodeRole.Stage && role != NodeRole.Tag
    }.sortBy(idx => groupGraph.nodeCreated(idx))
    @inline def inReplyGroup = commonParentsIdx.nonEmpty
    val commonParentIds = commonParentsIdx.map(groupGraph.nodeIds)

    def renderCommonParents(implicit ctx: Ctx.Owner) = div(
      cls := "chat-common-parents",
      Styles.flex,
      flexWrap.wrap,
      commonParentsIdx.map { parentIdx =>
        renderParentMessage(state, groupGraph.nodeIds(parentIdx), isDeletedNow = false, selectedNodes = selectedNodes, currentReply = currentReply, inputFieldFocusTrigger)
      }
    )

    val bgColor = NodeColor.mixHues(commonParentIds).map(hue => BaseColors.pageBgLight.copy(h = hue).toHex)
    val lineColor = NodeColor.mixHues(commonParentIds).map(hue => BaseColors.tag.copy(h = hue).toHex)


    var _previousNodeId: Option[NodeId] = None

    VDomModifier(
      cls := "chat-group-outer-frame",
      state.largeScreen.ifTrue[VDomModifier](if(inReplyGroup) paddingLeft := "40px" else author.map(_.map(user => bigAuthorAvatar(user)(onClickDirectMessage(state, user))))),
      div(
        cls := "chat-group-inner-frame",
        inReplyGroup.ifFalse[VDomModifier](author.map{ author =>
          val header = chatMessageHeader(state, author, creationEpochMillis, groupHeadId, state.largeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar)))
          header(state.smallScreen.ifTrue[VDomModifier](VDomModifier(paddingLeft := "0")))
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
            if(state.smallScreen) marginLeft := "0px"
            else marginLeft := "5px",
            lineColor.map(lineColor => borderLeft := s"3px solid ${ lineColor }"),


            group.map { nodeIdx =>
              val nodeId = groupGraph.nodeIds(nodeIdx)
              val parentIds = groupGraph.parentsByIndex(nodeIdx)

              val previousNodeId = _previousNodeId
              _previousNodeId = Some(nodeId)

              div.thunk(keyValue(nodeId))(state.screenSize.now)(Ownable { implicit ctx =>

                val isDeletedNow = state.graph.map(_.isDeletedNow(nodeId, parentIds))

                renderMessageRow(state, pageParentId, nodeId, parentIds, inReplyGroup = inReplyGroup, selectedNodes, isDeletedNow = isDeletedNow, currentReply = currentReply, inputFieldFocusTrigger = inputFieldFocusTrigger, previousNodeId = previousNodeId)
              })
            },
          )
        )
      )
    )
  }

  private def renderMessageRow(state: GlobalState, pageParentId: NodeId, nodeId: NodeId, directParentIds: Iterable[NodeId], inReplyGroup:Boolean, selectedNodes: Var[Set[SelectedNode]], isDeletedNow: Rx[Boolean], currentReply: Var[Set[NodeId]], inputFieldFocusTrigger:PublishSubject[Unit], previousNodeId: Option[NodeId])(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      selectedNodes().exists(_.nodeId == nodeId)
    }

    def replyAction = {
      currentReply.update(_ ++ Set(nodeId))
      inputFieldFocusTrigger.onNext(Unit)
    }

    def messageHeader: VDomModifier = if (inReplyGroup) Rx {
      val graph = state.graph()
      val idx = graph.idToIdx(nodeId)
      val author: Option[Node.User] = graph.nodeCreator(idx)
      if (previousNodeId.fold(true)(id => graph.nodeCreator(graph.idToIdx(id)).map(_.id) != author.map(_.id))) chatMessageHeader(state, author, graph.nodeCreated(idx), nodeId, author.map(smallAuthorAvatar))
      else VDomModifier.empty
    } else VDomModifier.empty

    val renderedMessage = renderMessage(state, nodeId, directParentIds, isDeletedNow = isDeletedNow, renderedMessageModifier = messageDragOptions(state, nodeId, selectedNodes))
    val controls = msgControls(state, nodeId, directParentIds.map(ParentId(_)), selectedNodes, isDeletedNow = isDeletedNow, replyAction = replyAction)
    val checkbox = msgCheckbox(state, nodeId, selectedNodes, newSelectedNode = SelectedNode(_)(directParentIds), isSelected = isSelected)
    val selectByClickingOnRow = {
      onClickOrLongPress foreach { longPressed =>
        if(longPressed) selectedNodes.update(_ + SelectedNode(nodeId)(directParentIds))
        else {
          // stopPropagation prevents deselecting by clicking on background
          val selectionModeActive = selectedNodes.now.nonEmpty
          if(selectionModeActive) selectedNodes.update(_.toggle(SelectedNode(nodeId)(directParentIds)))
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
        checkbox,
        renderedMessage,
        messageTags(state, nodeId),
        controls,
      )
    )
  }

  private def renderParentMessage(
    state: GlobalState,
    parentId: NodeId,
    isDeletedNow: Boolean,
    selectedNodes: Var[Set[SelectedNode]],
    currentReply: Var[Set[NodeId]],
    inputFieldFocusTrigger:PublishSubject[Unit],
    pinReply: Option[Var[Boolean]] = None
  )(implicit ctx: Ctx.Owner) = {
    val authorAndCreated = Rx {
      val graph = state.graph()
      val idx = graph.idToIdx(parentId)
      val authors = graph.authors(parentId)
      val creationEpochMillis = if(idx == -1) None else Some(graph.nodeCreated(idx))
      (authors.headOption, creationEpochMillis)
    }

    val parent = Rx{
      val graph = state.graph()
      graph.nodesByIdGet(parentId)
    }

    div(
      minWidth := "0", // fixes word-break in flexbox
      Rx {
        val tuple = authorAndCreated()
        val (author, creationEpochMillis) = tuple
        parent().map(node =>
          div(
            keyed(node.id),
            chatMessageHeader(state, author, author.map(smallAuthorAvatar))(
              padding := "2px",
              opacity := 0.7,
            ),
            state.largeScreen.ifTrue[VDomModifier](marginLeft := "5px"),
            borderLeft := s"3px solid ${ tagColor(parentId).toHex }",
            paddingRight := "5px",
            paddingBottom := "3px",
            backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(parentId)).toHex,
            div(
              opacity := 0.7,
              Styles.flex,
              paddingLeft := "0.5em",
              div(
                cls := "nodecard-content",
                Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id),
                fontSize.smaller,
                node.role match {
                  case NodeRole.Task => VDomModifier(
                      Styles.flex,
                      "Task: ",
                      renderNodeData(node.data, maxLength = Some(100))
                    )
                  case _ => VDomModifier(
                      renderNodeData(node.data, maxLength = Some(100))
                    )
                }
              ),
              div(cls := "fa-fw", freeSolid.faReply, padding := "3px 20px 3px 5px", onClick foreach { currentReply.update(_ ++ Set(parentId)) }, cursor.pointer),
              div(cls := "fa-fw", Icons.zoom, padding := "3px 20px 3px 5px", onClick foreach {
                  state.urlConfig.update(_.focus(Page(node.id)))
                  selectedNodes() = Set.empty[SelectedNode]
              }, cursor.pointer),
              pinReply.map{pinReply => div(cls := "fa-fw", freeSolid.faThumbtack, Rx { pinReply().ifFalse[VDomModifier](opacity := 0.4)}, padding := "3px 20px 3px 5px", onClick foreach { pinReply() = !pinReply.now; inputFieldFocusTrigger.onNext(()); () }, cursor.pointer)},
            )
          )
        )
      },
      isDeletedNow.ifTrue[VDomModifier](opacity := 0.5)
    )
  }

  private def selectChatMessages(pageParentId: NodeId, graph: Graph): js.Array[Int] = {
    val pageParentIdx = graph.idToIdx(pageParentId)
    val nodeSet = ArraySet.create(graph.nodes.length)
    var nodeCount = 0
    if(pageParentIdx == -1) return js.Array[Int]()

    algorithm.depthFirstSearchAfterStartsWithContinue(starts = Array(pageParentIdx), graph.childrenIdx, continue = {nodeIdx =>
      val node = graph.nodes(nodeIdx)
      node.role match {
        case NodeRole.Message =>
          nodeSet.add(nodeIdx)
          nodeCount += 1
        case _ =>
      }
      true // always continue traversal
    })

    val nodes = new js.Array[Int](nodeCount)
    nodeSet.foreachIndexAndElement( (i,nodeIdx) => nodes(i) = nodeIdx)
    sortByCreated(nodes, graph)
    nodes
  }

  private def calculateMessageGrouping(messages: js.Array[Int], graph: Graph, pageParentId: NodeId): Array[Array[Int]] = {
    if(messages.isEmpty) return Array[Array[Int]]()

    val pageParentIdx = graph.idToIdx(pageParentId)
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

      if(differentParents) {
        introduceGroupSplit()
      } else if(differentAuthors && noParents) {
        introduceGroupSplit()
      }

      currentGroupBuilder += message
      lastAuthor = author
      lastParents = parents
    }
    groupsBuilder += currentGroupBuilder.result()
    groupsBuilder.result()
  }

  //TODO share code with threadview?
  private def additionalNodeActions(selectedNodes: Var[Set[SelectedNode]], currentReply: Var[Set[NodeId]], inputFieldTriggerFocus:PublishSubject[Unit]): Boolean => List[VNode] = canWriteAll => List(
    replyButton(
      onClick foreach {
        currentReply() = selectedNodes.now.map(_.nodeId)
        selectedNodes() = Set.empty[SelectedNode]
        inputFieldTriggerFocus.onNext(Unit)
        ()
      }
    )
  )

  //TODO share code with threadview?
  private def selectedSingleNodeActions(state: GlobalState, selectedNodes: Var[Set[SelectedNode]]): (SelectedNode, Boolean) => List[VNode] = (selectedNode, canWriteAll) => if(state.graph.now.contains(selectedNode.nodeId)) {
    List(
      Some(editButton(
        onClick foreach {
          selectedNodes() = Set.empty[SelectedNode]
        }
      )).filter(_ => canWriteAll),
      Some(zoomButton(onClick foreach {
        state.urlConfig.update(_.focus(Page(selectedNode.nodeId)))
        selectedNodes() = Set.empty[SelectedNode]
      })),
    ).flatten
  } else Nil
}
