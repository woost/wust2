package wust.webApp.views

import cats.effect.IO
import outwatch.dom._
import outwatch.dom.dsl._
import wust.sdk.NodeColor._
import wust.util._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.ThreadOld._
import wust.webApp.BrowserDetect

import scala.collection.{breakOut, mutable}
import wust.util.algorithm._

import scala.util.Sorting

object ChatOld {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val nodeIds: Rx[Seq[Int]] = Rx {
      val page = state.page()
      val graph = state.graph()
      val builder = new mutable.ArrayBuilder.ofInt
      builder.sizeHint(page.parentIds.size)
      page.parentIds.foreach { parentId =>
        val idx = graph.lookup.idToIdx.getOrElse(parentId, -1)
        if (idx != -1) {
          builder += idx
        }
      }

      val pageChildren = depthFirstSearchWithoutStarts(builder.result(), graph.lookup.childrenIdx)
      Sorting.quickSort[Int](pageChildren)(Ordering[Long] on (graph.lookup.nodeCreated(_)))
      pageChildren.collect {
        case idx if graph.lookup.nodes(idx).isInstanceOf[Node.Content] => idx
      }
    }

    val submittedNewMessage = Handler.unsafe[Unit]

    val currentReply = Var(Set.empty[NodeId])
    val currentlyEditable = Var(Option.empty[List[NodeId]])

    def shouldGroup(graph:Graph, nodes: Seq[Int]):Boolean = {
      grouping && // grouping enabled
        graph.lookup.authorsIdx(nodes.head).headOption.fold(false) { authorId =>
          nodes.forall(node => graph.lookup.authorsIdx(node).head == authorId)
        }
    }

    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])
    def clearSelectedNodeIds(): Unit = selectedNodeIds() = Set.empty[NodeId]

    val selectedSingleNodeActions:NodeId => List[VNode] = nodeId => List(
      editButton.apply(
        onTap foreach {
          currentlyEditable() = Some(nodeId :: Nil)
          selectedNodeIds() = Set.empty[NodeId]
        }
      ),
    )

    val selectedNodeActions:List[NodeId] => List[VNode] =  nodeIds => List(
      replyButton.apply(onTap foreach { currentReply() = nodeIds.toSet; clearSelectedNodeIds() }),
      zoomButton(state, nodeIds),
      // SelectedNodes.deleteAllButton(state, nodeIds, selectedNodeIds),
      ???
    )



    def msgControls(nodeId: NodeId, meta: MessageMeta, isDeleted: Boolean, editable: Var[Boolean]): Seq[VNode] = {
      import meta._
      val state = meta.state // else import conflict
      val directParentIds:Array[NodeId] = directParentIndices.map(graph.lookup.nodeIds)(breakOut)
        if(isDeleted) List(undeleteButton(state, nodeId, directParentIds))
        else List(
          replyButton.apply(onTap foreach { currentReply() = Set(nodeId) }),
          editButton.apply(onTap foreach {
            editable() = true
            selectedNodeIds() = Set.empty[NodeId]
          }),
        deleteButton(state, nodeId, meta.graph.parents(nodeId).toSet, selectedNodeIds),
        zoomButton(state, nodeId :: Nil)
      )
    }

    def renderMessage(nodeIdx: Int, meta: MessageMeta)(implicit ctx: Ctx.Owner): VNode = {
      import meta._
      @deprecated("","")
      val nodeId = graph.lookup.nodeIds(nodeIdx)
      val state = meta.state // else import conflict
      val parents = graph.parents(nodeId) -- meta.state.page.now.parentIds
      div(
        keyed(nodeId),
        chatMessageLine(meta, nodeIdx, msgControls, currentlyEditable, selectedNodeIds, ThreadVisibility.Plain, showTags = false, transformMessageCard = { messageCard =>
          if(parents.nonEmpty) {
            val isDeleted = graph.lookup.isDeletedNowIdx(nodeIdx, directParentIndices)
            val bgColor = BaseColors.pageBgLight.copy(h = NodeColor.pageHue(parents).get).toHex
            div(
              cls := "nodecard",
              backgroundColor := (if(isDeleted) bgColor + "88" else bgColor), //TODO: rgba hex notation is not supported yet in Edge: https://caniuse.com/#feat=css-rrggbbaa
              div(
                keyed(nodeId),
                Styles.flex,
                alignItems.flexStart,
                parents.map { parentId =>
                  val parent = graph.nodesById(parentId)
                  parentMessage(meta.state, graph, parent).apply(
                    margin := "3px",
                    isDeleted.ifTrue[VDomModifier](opacity := 0.5),
                  )
                }(breakOut): Seq[VNode],
              ),
              messageCard(boxShadow := "none", backgroundColor := bgColor)
            )
          } else messageCard
        }),
      )
    }

    def parentMessage(state: GlobalState, graph:Graph, parent: Node) = div(
      padding := "1px",
      borderTopLeftRadius := "2px",
      borderTopRightRadius := "2px",
      chatMessageHeader(false, graph.lookup.idToIdx(parent.id), graph, AvatarSize.Small, showDate = false).apply(
        padding := "2px"
      ),
      nodeCard(parent).apply(
        fontSize.xSmall,
        backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(parent.id)).toHex,
        boxShadow := s"0px 1px 0px 1px ${ tagColor(parent.id).toHex }",
         cursor.pointer,
         onTap foreach {currentReply.update(_ + parent.id)},
      )
    )

    val replyPreview = Rx {
      val graph = state.graph()
      div(
        Styles.flexStatic,

        backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        Styles.flex,
        alignItems.flexStart,
        currentReply().map { replyNodeId =>
          val node = graph.nodesById(replyNodeId)
          div(
            padding := "5px",
            backgroundColor := BaseColors.pageBg.copy(h = NodeColor.pageHue(replyNodeId :: Nil).get).toHex,
            div(
              Styles.flex,
              alignItems.flexStart,
              parentMessage(state, graph, node).apply(alignSelf.center),
              closeButton(
                marginLeft.auto,
                onTap foreach { currentReply.update(_ - replyNodeId) }
              ),
            )
          )
        }(breakOut):Seq[VDomModifier]
      )
    }

    div(
      Styles.flex,
      flexDirection.column,
      alignItems.stretch,
      alignContent.stretch,
      height := "100%",

      // clear on page change
      managed(IO { state.page.foreach {_ => currentReply() = Set.empty[NodeId]} }),
      managed(IO { submittedNewMessage.foreach {_ => currentReply() = Set.empty[NodeId]} }),

      div(
        Styles.flex,
        flexDirection.row,
        height := "100%",
        position.relative,
        SelectedNodes[NodeId](state, nodeActions = selectedNodeActions, singleNodeActions = selectedSingleNodeActions, getNodeId = identity, selected = selectedNodeIds).apply(Styles.flexStatic, position.absolute, width := "100%"),
        chatHistory(state, nodeIds, submittedNewMessage, renderMessage = c => (a,b) => renderMessage(a,b)(c), shouldGroup = shouldGroup, selectedNodeIds).apply(
          height := "100%",
          width := "100%",
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
      ),
      onGlobalEscape(Set.empty[NodeId]) --> currentReply,
      replyPreview,
      Rx {
        val replyNodes: Set[NodeId] = {
          if(currentReply().nonEmpty) currentReply()
          else state.page().parentIdSet
        }
        inputField(state, replyNodes, submittedNewMessage, focusOnInsert = !BrowserDetect.isMobile).apply(
          Styles.flexStatic,
          padding := "3px",
          backgroundColor := BaseColors.pageBg.copy(h = NodeColor.pageHue(replyNodes).get).toHex,
        )
      },
      registerDraggableContainer(state),
    )
  }
}
