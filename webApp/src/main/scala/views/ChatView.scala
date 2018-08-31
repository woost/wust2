package wust.webApp.views

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
import wust.webApp.views.ThreadView._

import scala.collection.breakOut

object ChatView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val nodeIds: Rx[Seq[NodeId]] = Rx {
      val page = state.page()
      val graph = state.graphContent()
      graph.chronologicalNodesAscending.collect {
        case n: Node.Content if !(page.parentIdSet contains n.id) => n.id
      }
    }

    val submittedNewMessage = Handler.create[Unit].unsafeRunSync()

    val currentReply = Var(None: Option[NodeId])

    def msgControls(nodeId: NodeId, meta: MessageMeta, isDeleted: Boolean, editable: Var[Boolean]): Seq[VNode] = {
      import meta._
      val state = meta.state // else import conflict
      List(
        if(isDeleted) List(undeleteButton(state, nodeId, directParentIds))
        else List(
          replyButton(nodeId, meta, action = { (nodeId, meta) => currentReply() = Some(nodeId) }),
          editButton(state, editable),
          deleteButton(state, nodeId, directParentIds)
        ),
        List(zoomButton(state, nodeId))
      ).flatten
    }

    def renderMessage(nodeId: NodeId, meta: MessageMeta): VNode = {
      import meta._
      val state = meta.state // else import conflict
      val parents = graph.parents(nodeId) -- meta.state.page.now.parentIds
      div(
        tag("mytag")(attr("myattr") := "y", style("mystyle") := "x"),
        chatMessageLine(meta, nodeId, msgControls, showTags = false, transformMessageCard = { messageCard =>
          if(parents.nonEmpty) {
            div(
              cls := "nodecard",
              div(
                Styles.flex,
                alignItems.flexStart,
                parents.map { parentId =>
                  val parent = graph.nodesById(parentId)
                  parentMessage(meta.state, parent).apply(
                    margin := "3px",
                    opacity := 0.7,
                  )
                }(breakOut): Seq[VNode],
              ),
              messageCard(boxShadow := "none")
            )
          } else messageCard
        }),
      )
    }


    def parentMessage(state: GlobalState, parent: Node) = nodeCard(state, parent).apply(
      fontSize.smaller,
      backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(parent.id)).toHex,
      boxShadow := s"0px 1px 0px 1px ${ tagColor(parent.id).toHex }",
      cursor.pointer,
      onClick.stopPropagation(state.viewConfig.now.copy(page = Page(parent.id))) --> state.viewConfig
    )

    val replyPreview = Rx {
      val graph = state.graph()
      currentReply() map { replyNodeId =>
        val node = graph.nodesById(replyNodeId)
        div(
          padding := "5px",
          backgroundColor := BaseColors.pageBg.copy(h = NodeColor.pageHue(replyNodeId :: Nil).get).toHex,
          div(
            Styles.flex,
            alignItems.flexStart,
            parentMessage(state, node).apply(alignSelf.center),
            closeButton(
              marginLeft.auto,
              onClick(None: Option[NodeId]) --> currentReply,
            ),
          )
        )
      }
    }

    div(
      Styles.flex,
      flexDirection.column,
      alignItems.stretch,
      alignContent.stretch,
      height := "100%",
      div(
        Styles.flex,
        flexDirection.row,
        height := "100%",
        position.relative,
        chatHistory(state, nodeIds, submittedNewMessage, renderMessage = renderMessage).apply(
          height := "100%",
          width := "100%",
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
      ),
      replyPreview,
      Rx {
        val replyNodes: Set[NodeId] = currentReply().fold(state.page().parentIdSet)(Set(_))
        val focusOnInsert = state.screenSize.now != ScreenSize.Small
        inputField(state, replyNodes, submittedNewMessage, focusOnInsert = focusOnInsert).apply(
          Styles.flexStatic,
          padding := "3px",
          backgroundColor := BaseColors.pageBg.copy(h = NodeColor.pageHue(replyNodes).get).toHex,
        )
      },
      registerDraggableContainer(state),
    )
  }
}
