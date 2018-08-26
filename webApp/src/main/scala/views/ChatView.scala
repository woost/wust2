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
        chatHistory(state, nodeIds, activeThreadNesting = false).apply(
          height := "100%",
          width := "100%",
          overflow.auto,
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
      ),
      Rx { inputField(state, state.page().parentIdSet).apply(keyed, Styles.flexStatic, padding := "3px") },
      registerDraggableContainer(state),
    )
  }
}
