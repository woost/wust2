package wust.webApp.views

import cats.effect.IO
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
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.collection.breakOut
import scala.scalajs.js


object ChatView {
  import SharedViewElements._

  private final case class SelectedNode(nodeId: NodeId)(val editMode: Var[Boolean], val directParentIds: Iterable[NodeId]) extends SelectedNodeBase

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val selectedNodes = Var(Set.empty[SelectedNode]) //TODO move up

    val scrollHandler = ScrollHandler(Var(None: Option[HTMLElement]), Var(true))
    val inputFieldFocusTrigger = PublishSubject[Unit]

    val currentReply = Var(Set.empty[NodeId])

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
        scrollHandler.scrollOptions(state)
      ),
      managed(IO { state.page.foreach { _ => currentReply() = Set.empty[NodeId] } }),
      onGlobalEscape(Set.empty[NodeId]) --> currentReply,
      Rx {
        val graph = state.graph()
        div(
          Styles.flexStatic,

          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
          Styles.flex,
          alignItems.flexStart,
          currentReply().map { replyNodeId =>
            val isDeletedNow = graph.isDeletedNow(replyNodeId, state.page.now.parentIds)
            val node = graph.nodesById(replyNodeId)
            div(
              padding := "5px",
              backgroundColor := BaseColors.pageBg.copy(h = NodeColor.pageHue(replyNodeId :: Nil).get).toHex,
              div(
                Styles.flex,
                alignItems.flexStart,
                parentMessage(state, node, isDeletedNow, currentReply).apply(alignSelf.center),
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

        inputField(state, replyNodes, scrollHandler, inputFieldFocusTrigger)(ctx)(Styles.flexStatic)
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
            // because of equals check in thunk, we implicitly generate a wrapped array
            val nodeIds: Seq[NodeId] = group.map(graph.nodeIds)
            val key = nodeIds.head.toString

            div.thunk(key)(nodeIds, state.screenSize.now)(thunkGroup(state, graph, group, currentReply, selectedNodes, inputFieldFocusTrigger = inputFieldFocusTrigger))
          }
        )
      }
    }
  }

  private def thunkGroup(state: GlobalState, groupGraph: Graph, group: Array[Int], currentReply: Var[Set[NodeId]], selectedNodes: Var[Set[SelectedNode]], inputFieldFocusTrigger:PublishSubject[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {
    val author: Option[Node.User] = groupGraph.authorsIdx.get(group(0), 0).map(authorIdx => groupGraph.nodes(authorIdx).asInstanceOf[Node.User])
    val creationEpochMillis = groupGraph.nodeCreated(group(0))
    val isLargeScreen = state.screenSize.now == ScreenSize.Large

    VDomModifier(
      cls := "chat-group-outer-frame",
      isLargeScreen.ifTrue[VDomModifier](author.map(bigAuthorAvatar)),
      div(
        cls := "chat-group-inner-frame",
        chatMessageHeader(author, creationEpochMillis, isLargeScreen.ifFalse[VDomModifier](author.map(smallAuthorAvatar))),

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
        }
      )
    )
  }


  private def renderMessageRow(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], selectedNodes: Var[Set[SelectedNode]], isDeletedNow: Rx[Boolean], editMode: Var[Boolean], currentReply: Var[Set[NodeId]], inputFieldFocusTrigger:PublishSubject[Unit])(implicit ctx: Ctx.Owner): VNode = {

    val isSelected = Rx {
      selectedNodes().exists(_.nodeId == nodeId)
    }

    val isDeletedInFuture = Rx {
      val graph = state.graph()
      graph.isDeletedInFuture(nodeId, directParentIds)
    }

    val parentNodes: Rx[Seq[Node]] = Rx {
      val graph = state.graph()
      (graph.parents(nodeId) -- state.page.now.parentIds)
        .map(id => graph.nodes(graph.idToIdx(id)))(breakOut)
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
      Rx {
        val messageCard = renderedMessage()
        val parents = parentNodes()
        if(parents.nonEmpty) {
          val importanceIndicator = Rx {
            val isImportant = !(editMode() || isDeletedNow() || isDeletedInFuture())
            isImportant.ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px #fbbd08"))
          }
          val bgColor = BaseColors.pageBgLight.copy(h = NodeColor.pageHue(parents.map(_.id)).get).toHex
          div(
            cls := "nodecard",
            backgroundColor := (if(isDeletedNow()) bgColor + "88" else bgColor), //TODO: rgbia hex notation is not supported yet in Edge: https://caniuse.com/#feat=css-rrggbbaa
            div(
              Styles.flex,
              alignItems.flexStart,
              parents.map { parent =>
                parentMessage(state, parent, isDeletedNow(), currentReply)
              }
            ),
            messageCard.map(_ (boxShadow := "none", backgroundColor := bgColor)),
            importanceIndicator,
          )
        } else messageCard: VDomModifier
      },
      controls,
      messageRowDragOptions(nodeId, selectedNodes, editMode)
    )
  }

  private def parentMessage(state: GlobalState, parent: Node, isDeletedNow: Boolean, currentReply: Var[Set[NodeId]])(implicit ctx: Ctx.Owner) = {
    val authorAndCreated = Rx {
      val graph = state.graph()
      val idx = graph.idToIdx(parent.id)
      val authors = graph.authors(parent.id)
      val creationEpochMillis = if(idx == -1) None else Some(graph.nodeCreated(idx))
      (authors.headOption, creationEpochMillis)
    }

    div(
      padding := "1px",
      borderTopLeftRadius := "2px",
      borderTopRightRadius := "2px",
      Rx {
        val tuple = authorAndCreated()
        val (author, creationEpochMillis) = tuple
        chatMessageHeader(author, creationEpochMillis.getOrElse(EpochMilli.min), author.map(smallAuthorAvatar))(
          padding := "2px",
        )
      },
      nodeCard(parent)(
        fontSize.xSmall,
        backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(parent.id)).toHex,
        boxShadow := s"0px 1px 0px 1px ${ tagColor(parent.id).toHex }",
        cursor.pointer,
        onTap foreach { currentReply.update(_ ++ Set(parent.id)) },
      ),
      margin := "3px",
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
