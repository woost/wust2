package wust.webApp.views

import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.sdk.NodeColor._
import wust.util._
import wust.util.collection._
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.dateFns
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.outwatchHelpers._

import scala.collection.breakOut
import scala.scalajs.js
import ThreadView._

object ChatView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val nodeIds: Rx[Seq[NodeId]] = Rx{
      val page = state.page()
      val graph = state.graphContent()
      graph.chronologicalNodesAscending.collect {
        case n: Node.Content if !(page.parentIdSet contains n.id) => n.id
      }
    }

    val currentReply = Var(None:Option[NodeId])

    def msgControls(nodeId:NodeId, meta:MessageMeta, isDeleted:Boolean, editable:Var[Boolean]):Seq[VNode] = {
      import meta._
      val state = meta.state
      List(
        if(isDeleted) List(undeleteButton(state, nodeId, directParentIds))
        else List(
          replyButton(nodeId, meta, action = { (nodeId, meta) => currentReply() = Some(nodeId)}),
          editButton(state, editable),
          deleteButton(state, nodeId, directParentIds)
        ),
        List(zoomButton(state, nodeId))
      ).flatten
    }

    def renderMessage(nodeId: NodeId, meta: MessageMeta):VNode = chatMessageLine(meta, nodeId, msgControls)

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
            nodeCard(state, node).apply(width := "100%", alignSelf.center),
            closeButton(
              onClick(None:Option[NodeId]) --> currentReply,
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
        chatHistory(state, nodeIds, renderMessage = renderMessage).apply(
          height := "100%",
          width := "100%",
          overflow.auto,
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
      ),
      replyPreview,
      Rx {
        val replyNodes:Set[NodeId] = currentReply().fold(state.page().parentIdSet)(Set(_))
        val focusOnInsert = state.screenSize.now != ScreenSize.Small
        inputField(state, replyNodes, focusOnInsert = focusOnInsert).apply(
          Styles.flexStatic,
          padding := "3px",
          backgroundColor := BaseColors.pageBg.copy(h = NodeColor.pageHue(replyNodes).get).toHex,
        )
      },
      registerDraggableContainer(state),
    )
  }
}
