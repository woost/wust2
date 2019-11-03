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

object ListWithChatView {

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val chatFocus = Var(focusState.focusedId)

    val listViewFocusState = focusState.copy(
      onItemSingleClick = chatFocus() = _,
      itemIsFocused = nodeId => chatFocus.map(_ == nodeId)
    )

    div(
      Styles.flex,

      ListView(listViewFocusState).apply(
        flexGrow := 1,
        height := "100%",
      ),
      div(
        flexGrow := 1,
        Styles.flex,
        flexDirection.column,
        Rx{
          ChatView(focusState.copy(focusedId = chatFocus())).apply(
            height := "100%",
          )
        }
      )
    )
  }
}
