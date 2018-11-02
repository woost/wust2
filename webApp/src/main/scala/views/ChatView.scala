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
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.util.collection._
import wust.webApp.BrowserDetect
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

    val scrollHandler = ScrollHandler(Var(None: Option[HTMLElement]), Var(true))
    val inputFieldFocusTrigger = PublishSubject[Unit]

    val currentReply = Var(Set.empty[NodeId])
    currentReply.foreach{ _ =>
      inputFieldFocusTrigger.onNext(())
    }

    val outerDragOptions = VDomModifier(
      draggableAs(DragItem.DisableDrag), // chat history is not draggable, only its elements
      Rx { dragTarget(DragItem.Chat.Page(state.page().parentIds)) },
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
      div(
        cls := "chat-history",
        overflow.auto,
        backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        chatHistory(state, currentReply, selectedNodes, inputFieldFocusTrigger),
        outerDragOptions,

        // clicking on background deselects
        onClick foreach { e => if(e.currentTarget == e.target) selectedNodes() = Set.empty[SelectedNode] },
        scrollHandler.scrollOptions,
        managed { () =>
          // on page change, always scroll down
          state.page.foreach { _ =>
            scrollHandler.scrollToBottomInAnimationFrame()
          }
        }
      ),
      managed(() => state.page.foreach { _ => currentReply() = Set.empty[NodeId] }),
      onGlobalEscape(Set.empty[NodeId]) --> currentReply,
      Rx {
        val graph = state.graph()
        div(
          Styles.flexStatic,

          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
          Styles.flex,
          currentReply().map { replyNodeId =>
            val isDeletedNow = graph.isDeletedNow(replyNodeId, state.page.now.parentIds)
            val node = graph.nodesById(replyNodeId)
            div(
              padding := "5px",
              backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(replyNodeId)).toHex,
              div(
                Styles.flex,
                renderParentMessage(state, node.id, isDeletedNow, currentReply),
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
        def replyNodes: Set[NodeId] = {
          if(currentReply.now.nonEmpty) currentReply.now
          else state.page.now.parentIdSet
        }

        val bgColor = Rx{ NodeColor.pageHue(currentReply()).map(hue => BaseColors.pageBgLight.copy(h = hue).toHex) }

        def submitAction(str:String) = {
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
        val groups = calculateMessageGrouping(chatMessages(page.parentIds, graph), graph)

        VDomModifier(
          groups.nonEmpty.ifTrue[VDomModifier](padding := "50px 0px 5px 20px"),
          groups.map { group =>
            thunkRxFun(state, graph, group, currentReply, selectedNodes, inputFieldFocusTrigger)
          }
        )
      }
    }
  }

  private def thunkRxFun(state:GlobalState, graph:Graph, group: Array[Int], currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger: PublishSubject[Unit]): ThunkVNode = {
    // because of equals check in thunk, we implicitly generate a wrapped array
    val nodeIds: Seq[NodeId] = group.map(graph.nodeIds)
    val key = nodeIds.head.toString
    val commonParentIds: Seq[NodeId] = graph.parentsIdx(group(0)).map(graph.nodeIds)
    div.thunkRx(key)(nodeIds, state.screenSize.now, commonParentIds)(implicit ctx => thunkGroup(state, graph, group, currentReply, selectedNodes, inputFieldFocusTrigger = inputFieldFocusTrigger))
  }

  private def thunkGroup(state: GlobalState, groupGraph: Graph, group: Array[Int], currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger:PublishSubject[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {

    val author: Option[Node.User] = groupGraph.authorsIdx.get(group(0), 0).map(authorIdx => groupGraph.nodes(authorIdx).asInstanceOf[Node.User])
    val creationEpochMillis = groupGraph.nodeCreated(group(0))
    val isLargeScreen = state.screenSize.now == ScreenSize.Large
    val commonParentsIdx = groupGraph.parentsIdx(group(0)).sortBy(idx => groupGraph.nodeCreated(idx))
    val commonParentIds = commonParentsIdx.map(groupGraph.nodeIds).filterNot(state.page.now.parentIdSet)

    val commonParents = div(
      cls := "chat-common-parents",
      Styles.flex,
      flexWrap.wrap,
      commonParentsIdx.map { parentIdx =>
        state.page.now.parentIdSet.contains(groupGraph.nodeIds(parentIdx)).ifFalse[VDomModifier](
          renderParentMessage(state, groupGraph.nodeIds(parentIdx), isDeletedNow = false, currentReply = currentReply)
        )
      }
    )

    val bgColor = NodeColor.pageHue(commonParentIds).map(hue => BaseColors.pageBgLight.copy(h = hue).toHex)
    val lineColor = NodeColor.pageHue(commonParentIds).map(hue => BaseColors.tag.copy(h = hue).toHex)
    VDomModifier(
      cls := "chat-group-outer-frame",
      isLargeScreen.ifTrue[VDomModifier](author.map(bigAuthorAvatar)),
      div(
        cls := "chat-group-inner-frame",
        chatMessageHeader(author, creationEpochMillis, isLargeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar))),

        div(
          cls := "chat-expanded-thread",
          backgroundColor :=? bgColor,
          commonParents,

          draggableAs(DragItem.DisableDrag),
          cursor.auto, // draggable sets cursor.move, but drag is disabled on thread background
          dragTarget(DragItem.Chat.Thread(commonParentIds)),

          div(
            cls := "chat-thread-messages",
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

                renderMessageRow(state, nodeId, parentIds, selectedNodes, editMode = editMode, isDeletedNow = isDeletedNow, currentReply = currentReply, inputFieldFocusTrigger = inputFieldFocusTrigger)
              }
            },
            commonParentIds.nonEmpty.ifTrue[VDomModifier](threadReplyButton(state, commonParentIds.toSet, currentReply))
          )
        )
      )
    )
  }

  private def threadReplyButton(state: GlobalState, commonParentIds:Set[NodeId], currentReply: Var[Set[NodeId]]) = {
    div(
      cls := "chat-replybutton",
      freeSolid.faReply,
      " reply",
      marginTop := "3px",
      marginLeft := "8px",
      onClick.stopPropagation foreach { currentReply() = commonParentIds }
    )
  }

  private def renderMessageRow(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], selectedNodes: Var[Set[SelectedNode]], isDeletedNow: Rx[Boolean], editMode: Var[Boolean], currentReply: Var[Set[NodeId]], inputFieldFocusTrigger:PublishSubject[Unit])(implicit ctx: Ctx.Owner): VNode = {

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
      cls := "chat-row",
      Styles.flex,

      isSelected.map(_.ifTrue[VDomModifier](backgroundColor := "rgba(65,184,255, 0.5)")),
      selectByClickingOnRow,
      checkbox,
      Rx { renderedMessage() },
      controls,
      messageRowDragOptions(nodeId, selectedNodes, editMode)
    )
  }

  private def renderParentMessage(state: GlobalState, parentId: NodeId, isDeletedNow: Boolean, currentReply: Var[Set[NodeId]])(implicit ctx: Ctx.Owner) = {
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
            chatMessageHeader(author, creationEpochMillis.getOrElse(EpochMilli.min), author.map(smallAuthorAvatar))(
              padding := "2px",
              opacity := 0.7,
            ),
            marginLeft := "5px",
            borderLeft := s"3px solid ${ tagColor(parentId).toHex }",
            paddingRight := "5px",
            paddingBottom := "3px",
            backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(parentId)).toHex,
            cursor.pointer,
            onClick foreach { currentReply.update(_ ++ Set(parentId)) },
            div(
              opacity := 0.7,
              Styles.flex,
              paddingLeft := "0.5em",
              div(
                cls := "nodecard-content",
                renderNodeData(node.data, maxLength = Some(200)),
              ),
              div(cls := "fa-fw", freeSolid.faReply, padding := "3px 20px 3px 5px"),
            )
          )
        )
      },
      isDeletedNow.ifTrue[VDomModifier](opacity := 0.5)
    )
  }

  private def chatMessages(parentIds: Iterable[NodeId], graph: Graph): js.Array[Int] = {
    val nodeSet = ArraySet.create(graph.nodes.length)
    parentIds.foreach { parentId =>
      val parentIdx = graph.idToIdx(parentId)
      if(parentIdx != -1) {
        graph.descendantsIdx(parentIdx).foreachElement { childIdx =>
          val childNode = graph.nodes(childIdx)
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

  private def calculateMessageGrouping(messages: js.Array[Int], graph: Graph): Array[Array[Int]] = {
    if(messages.isEmpty) return Array[Array[Int]]()

    val groupsBuilder = mutable.ArrayBuilder.make[Array[Int]]
    val currentGroupBuilder = new mutable.ArrayBuilder.ofInt
    var lastAuthor: Int = -1
    var lastParents: SliceInt = null
    messages.foreach { message =>
      val author: Int = graph.authorsIdx(message, 0)
      val parents: SliceInt = graph.parentsIdx(message)

      if((lastAuthor != -1 && author != lastAuthor) ||
        (lastParents != null && parents != lastParents)
      ) {
        groupsBuilder += currentGroupBuilder.result()
        currentGroupBuilder.clear()
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
          selectedNodes.now.head.editMode() = true
          selectedNodes() = Set.empty[SelectedNode]
        }
      )
    )
  } else Nil
}
