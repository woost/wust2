package wust.webApp.views

import cats.effect.IO
import fontAwesome.freeSolid
import monix.reactive.subjects.PublishSubject
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
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.collection.{breakOut, mutable}
import scala.scalajs.js


object ChatView {
  import SharedViewElements._

  private final case class SelectedNode(nodeId: NodeId)(val editMode: Var[Boolean], val directParentIds: Iterable[NodeId]) extends SelectedNodeBase

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val selectedNodes = Var(Set.empty[SelectedNode]) //TODO move up

    val scrollHandler = new ScrollBottomHandler
    val inputFieldFocusTrigger = PublishSubject[Unit]

    val currentReply = Var(Set.empty[NodeId])
    currentReply.foreach{ _ =>
      inputFieldFocusTrigger.onNext(())
    }

    val outerDragOptions = VDomModifier(
      draggableAs(DragItem.DisableDrag), // chat history is not draggable, only its elements
      Rx { state.page().parentId.map(parentId => dragTarget(DragItem.Chat.Page(parentId))) },
      registerDraggableContainer(state),
      cursor.auto, // draggable sets cursor.move, but drag is disabled on page background
    )

    div(
      keyed,
      Styles.flex,
      flexDirection.column,
      position.relative, // for absolute positioning of selectednodes
      SelectedNodes[SelectedNode](state, selectedNodeActions(state, selectedNodes, additional = additionalNodeActions(selectedNodes,currentReply,inputFieldFocusTrigger)), selectedSingleNodeActions(state, selectedNodes), selectedNodes).apply(
        position.absolute,
        width := "100%"
      ),
      emitterRx(selectedNodes).map(_.map(_.nodeId)(breakOut): List[NodeId]) --> state.selectedNodes,
      div(
        cls := "chat-history",
        overflow.auto,
        backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        chatHistory(state, currentReply, selectedNodes, inputFieldFocusTrigger),
        outerDragOptions,

        // clicking on background deselects
        onClick foreach { e => if(e.currentTarget == e.target) selectedNodes() = Set.empty[SelectedNode] },
        scrollHandler.modifier,
        // on page change, always scroll down
        emitterRx(state.page).foreach {
          scrollHandler.scrollToBottomInAnimationFrame()
        }
      ),
      emitterRx(state.page).foreach { currentReply() = Set.empty[NodeId] },
      onGlobalEscape(Set.empty[NodeId]) --> currentReply,
      Rx {
        val graph = state.graph()
        div(
          Styles.flexStatic,

          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
          Styles.flex,
          currentReply().map { replyNodeId =>
            val isDeletedNow = graph.isDeletedNow(replyNodeId, state.page().parentId)
            val node = graph.nodesById(replyNodeId)
            div(
              padding := "5px",
              minWidth := "0", // fixes overflow-wrap for parent preview
              backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(replyNodeId)).toHex,
              div(
                Styles.flex,
                renderParentMessage(state, node.id, isDeletedNow, selectedNodes, currentReply),
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

        def submitAction(str:String) = {

          val replyNodes: Set[NodeId] = {
            if(currentReply.now.nonEmpty) currentReply.now
            else state.page.now.parentId.toSet
          }

          currentReply() = Set.empty[NodeId]
          scrollHandler.scrollToBottomInAnimationFrame()
          // we treat new chat messages as noise per default, so we set a future deletion date
          val changes = GraphChanges.addNodeWithDeletedParent(Node.MarkdownMessage(str), replyNodes, deletedAt = noiseFutureDeleteDate)
          state.eventProcessor.changes.onNext(changes)

        }

        if(!BrowserDetect.isMobile) {
          state.page.triggerLater {
            inputFieldFocusTrigger.onNext(Unit) // re-gain focus on page-change
            ()
          }
        }

        inputField(state, submitAction, scrollHandler = Some(scrollHandler), preFillByShareApi = true, autoFocus = !BrowserDetect.isMobile, triggerFocus = inputFieldFocusTrigger)(ctx)(
          Styles.flexStatic,
          Rx{ backgroundColor :=? bgColor()}
        )
      }
    )
  }

  private def chatHistory(state: GlobalState, currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger: PublishSubject[Unit])(implicit ctx: Ctx.Owner): Rx[VDomModifier] = {
    Rx {
      state.screenSize() // on screensize change, rerender whole chat history
      val page = state.page()
      val graph = state.graph()
      withLoadingAnimation(state) {
        val pageParentArraySet = graph.createArraySet(page.parentId) //TODO: remove. It only conntains max one element
        val groups = calculateMessageGrouping(selectChatMessages(page.parentId, graph), graph, pageParentArraySet)

        VDomModifier(
          groups.nonEmpty.ifTrue[VDomModifier](
            if(state.screenSize.now == ScreenSize.Small) padding := "50px 0px 5px 5px"
            else padding := "50px 0px 5px 20px"
          ),
          groups.map { group =>
            thunkRxFun(state, graph, group, pageParentArraySet, currentReply, selectedNodes, inputFieldFocusTrigger)
          }
        )
      }
    }
  }

  private def thunkRxFun(state:GlobalState, graph:Graph, group: Array[Int], pageParentArraySet:ArraySet, currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger: PublishSubject[Unit]): ThunkVNode = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.map(graph.nodeIds)
    val key = nodeIds.head.toString
    val commonParentIds: Seq[NodeId] = graph.parentsIdx(group(0)).map(graph.nodeIds)
    div.thunkRx(key)(nodeIds, state.screenSize.now, commonParentIds)(implicit ctx => thunkGroup(state, graph, group, pageParentArraySet, currentReply, selectedNodes, inputFieldFocusTrigger = inputFieldFocusTrigger))
  }

  private def thunkGroup(state: GlobalState, groupGraph: Graph, group: Array[Int], pageParentArraySet: ArraySet, currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger:PublishSubject[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {

    val groupHeadId = groupGraph.nodeIds(group(0))
    val author: Rx[Option[Node.User]] = Rx {
      val graph = state.graph()
      graph.nodeCreator(graph.idToIdx(groupHeadId))
    }
    val creationEpochMillis = groupGraph.nodeCreated(group(0))

    val commonParentsIdx = groupGraph.parentsIdx(group(0)).filter(pageParentArraySet.containsNot).sortBy(idx => groupGraph.nodeCreated(idx))
    @inline def inReplyGroup = commonParentsIdx.nonEmpty
    val commonParentIds = commonParentsIdx.map(groupGraph.nodeIds).filterNot(state.page.now.parentId.contains)

    def renderCommonParents(implicit ctx: Ctx.Owner) = div(
      cls := "chat-common-parents",
      Styles.flex,
      flexWrap.wrap,
      commonParentsIdx.map { parentIdx =>
        renderParentMessage(state, groupGraph.nodeIds(parentIdx), isDeletedNow = false, selectedNodes = selectedNodes, currentReply = currentReply)
      }
    )

    val bgColor = NodeColor.mixHues(commonParentIds).map(hue => BaseColors.pageBgLight.copy(h = hue).toHex)
    val lineColor = NodeColor.mixHues(commonParentIds).map(hue => BaseColors.tag.copy(h = hue).toHex)
    VDomModifier(
      cls := "chat-group-outer-frame",
      state.largeScreen.ifTrue[VDomModifier](if(inReplyGroup) paddingLeft := "40px" else author.map(_.map(bigAuthorAvatar))),
      div(
        cls := "chat-group-inner-frame",
        inReplyGroup.ifFalse[VDomModifier](author.map(author => chatMessageHeader(author, creationEpochMillis, state.largeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar)))(
          state.smallScreen.ifTrue[VDomModifier](paddingLeft := "0")
        ))),

        div(
          cls := "chat-expanded-thread",
          backgroundColor :=? bgColor,

          inReplyGroup.ifTrue[VDomModifier](renderCommonParents),

          draggableAs(DragItem.DisableDrag),
          cursor.auto, // draggable sets cursor.move, but drag is disabled on thread background
          dragTarget(DragItem.Chat.Thread(commonParentIds)),

          div(
            cls := "chat-thread-messages",
            state.smallScreen.ifTrue[VDomModifier](marginLeft := "0px"),
            lineColor.map(lineColor => borderLeft := s"3px solid ${ lineColor }"),

            group.map { nodeIdx =>
              val nodeId = groupGraph.nodeIds(nodeIdx)
              val parentIds = groupGraph.parentsByIndex(nodeIdx)

              div.thunkRx(keyValue(nodeId))(state.screenSize.now) { implicit ctx =>

                val isDeletedNow = Rx {
                  val graph = state.graph()
                  graph.isDeletedNow(nodeId, parentIds)
                }

                val editMode = Var(false)

                renderMessageRow(state, nodeId, parentIds, inReplyGroup = inReplyGroup, selectedNodes, editMode = editMode, isDeletedNow = isDeletedNow, currentReply = currentReply, inputFieldFocusTrigger = inputFieldFocusTrigger)
              }
            },
          )
        )
      )
    )
  }

  private def renderMessageRow(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], inReplyGroup:Boolean, selectedNodes: Var[Set[SelectedNode]], isDeletedNow: Rx[Boolean], editMode: Var[Boolean], currentReply: Var[Set[NodeId]], inputFieldFocusTrigger:PublishSubject[Unit])(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      selectedNodes().exists(_.nodeId == nodeId)
    }

    val isDeletedInFuture = Rx {
      val graph = state.graph()
      val nodeIdx = graph.idToIdx(nodeId)
      if(nodeIdx == -1) true
      else graph.combinedDeletedAt(nodeIdx) match {
        case Some(deletedAt) => deletedAt isAfter EpochMilli.now
        case None => false
      }
    }

    def replyAction = {
      currentReply.update(_ ++ Set(nodeId))
      inputFieldFocusTrigger.onNext(Unit)
    }

    def messageHeader = Rx {
      val graph = state.graph()
      val idx = graph.idToIdx(nodeId)
      val author: Option[Node.User] = graph.nodeCreator(idx)
      chatMessageHeader(author, graph.nodeCreated(idx), author.map(smallAuthorAvatar))
    }

    val renderedMessage = renderMessage(state, nodeId, isDeletedNow = isDeletedNow, isDeletedInFuture = isDeletedInFuture, editMode = editMode)
    val controls = msgControls(state, nodeId, directParentIds, selectedNodes, isDeletedNow = isDeletedNow, isDeletedInFuture = isDeletedInFuture, editMode = editMode, replyAction = replyAction) //TODO reply action
    val checkbox = msgCheckbox(state, nodeId, selectedNodes, newSelectedNode = SelectedNode(_)(editMode, directParentIds), isSelected = isSelected)
    val selectByClickingOnRow = {
      onClickOrLongPress foreach { longPressed =>
        if(longPressed) selectedNodes.update(_ + SelectedNode(nodeId)(editMode, directParentIds))
        else {
          // stopPropagation prevents deselecting by clicking on background
          val selectionModeActive = selectedNodes.now.nonEmpty
          if(selectionModeActive) selectedNodes.update(_.toggle(SelectedNode(nodeId)(editMode, directParentIds)))
        }
      }
    }

    div(
      inReplyGroup.ifTrue[VDomModifier](messageHeader),
      div(
        cls := "chat-row",
        Styles.flex,

        isSelected.map(_.ifTrue[VDomModifier](backgroundColor := "rgba(65,184,255, 0.5)")),
        selectByClickingOnRow,
        checkbox,
        Rx { renderedMessage() },
        controls,
        messageRowDragOptions(nodeId, selectedNodes, editMode)
      )
    )
  }

  private def renderParentMessage(state: GlobalState, parentId: NodeId, isDeletedNow: Boolean, selectedNodes: Var[Set[SelectedNode]], currentReply: Var[Set[NodeId]])(implicit ctx: Ctx.Owner) = {
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
            chatMessageHeader(author, author.map(smallAuthorAvatar))(
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
                cls := "enable-text-selection",
                fontSize.smaller,
                renderNodeData(node.data, maxLength = Some(100))((node.role == NodeRole.Task).ifTrue[VDomModifier](backgroundColor := NodeColor.eulerBgColor(node.id).toHex)),
              ),
              div(cls := "fa-fw", freeSolid.faReply, padding := "3px 20px 3px 5px", onClick foreach { currentReply.update(_ ++ Set(parentId)) }, cursor.pointer),
              div(cls := "fa-fw", Icons.zoom, padding := "3px 20px 3px 5px", onClick foreach {
                Var.set(
                  state.page -> Page(parentId),
                  selectedNodes -> Set.empty[SelectedNode]
                )
              }, cursor.pointer),
            )
          )
        )
      },
      isDeletedNow.ifTrue[VDomModifier](opacity := 0.5)
    )
  }

  private def selectChatMessages(parentIds: Iterable[NodeId], graph: Graph): js.Array[Int] = {
    val nodeSet = ArraySet.create(graph.nodes.length)
    //TODO: performance: depthFirstSearchMultiStartForeach which starts at multiple start points and accepts a function
    val parentsIdx:Array[Int] = (parentIds.map(graph.idToIdx)(breakOut):Array[Int]).filterNot(_ == -1)
    algorithm.depthFirstSearchAfterStartsWithContinue(starts = parentsIdx, graph.childrenIdx, { childIdx =>
      val childNode = graph.nodes(childIdx)
      if(childNode.isInstanceOf[Node.Content]) {
        if (childNode.role == NodeRole.Message) {
          nodeSet.add(childIdx)
          true
        } else graph.childrenIdx(childIdx).exists(idx => graph.nodes(idx).role == NodeRole.Message)
      }
      else false // don't go further down
    })
    val nodes = js.Array[Int]()
    nodeSet.foreach(nodes += _)
    sortByCreated(nodes, graph)
    nodes
  }

  private def calculateMessageGrouping(messages: js.Array[Int], graph: Graph, pageParentArraySet: ArraySet): Array[Array[Int]] = {
    if(messages.isEmpty) return Array[Array[Int]]()

    val groupsBuilder = mutable.ArrayBuilder.make[Array[Int]]
    val currentGroupBuilder = new mutable.ArrayBuilder.ofInt
    var lastAuthor: Int = -2 // to distinguish between no author and no previous group
    var lastParents: SliceInt = null
    messages.foreach { message =>
      val author: Int = graph.nodeCreatorIdx(message) // without author, returns -1
      val parents: SliceInt = graph.parentsIdx(message)

      @inline def differentParents = lastParents != null && parents != lastParents
      @inline def differentAuthors = lastAuthor != -2 && author != lastAuthor
      @inline def noParents = lastParents != null && parents.forall(pageParentArraySet.contains)
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

  private def additionalNodeActions(selectedNodes: Var[Set[SelectedNode]], currentReply: Var[Set[NodeId]], inputFieldTriggerFocus:PublishSubject[Unit]) = List(
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
  private def selectedSingleNodeActions(state: GlobalState, selectedNodes: Var[Set[SelectedNode]]): SelectedNode => List[VNode] = selectedNode => if(state.graph.now.contains(selectedNode.nodeId)) {
    List(
      editButton(
        onClick foreach {
          selectedNode.editMode() = true
          selectedNodes() = Set.empty[SelectedNode]
        }
      ),
      zoomButton(onClick foreach {
        state.page.onNext(Page(selectedNode.nodeId))
        selectedNodes() = Set.empty[SelectedNode]
      }),
    )
  } else Nil
}
