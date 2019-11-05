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
import wust.sdk.{ BaseColors, NodeColor }
import wust.util._
import wust.util.algorithm.dfs
import wust.util.collection._
import wust.util.macros.InlineList
import wust.webApp.Icons
import wust.webApp.dragdrop.{ DragItem, _ }
import wust.webApp.state.GlobalState.SelectedNode
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{ drag, registerDragContainer }
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, Ownable }

import scala.collection.{ breakOut, mutable }
import scala.scalajs.js

object ListWithChatView {

  def apply(originalFocusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val chatFocus = Var(originalFocusState.focusedId)

    val focusState = originalFocusState.copy(
      onItemSingleClick = { nodeId =>
        chatFocus() = if (chatFocus.now == nodeId) originalFocusState.focusedId else nodeId
      },
      itemIsFocused = nodeId => chatFocus.map(_ == nodeId)
    )

    div(
      cls := "listwithchat-view",
      Styles.flex,

      ListView(focusState).apply(
        flex := "1",
        maxWidth := "300px",
        height := "100%",
      ),
      div(
        flex := "3",
        Styles.flex,
        flexDirection.column,
        Rx{
          div(
            Styles.flexStatic,
            Styles.flex,
            alignItems.center,

            padding := "8px 10px",
            BreadCrumbs(
              GlobalState.rawGraph(),
              start = BreadCrumbs.EndPoint.Node(focusState.focusedId, inclusive = true),
              end = BreadCrumbs.EndPoint.Node(chatFocus()),
              nodeId => chatFocus() = nodeId,
              hideIfSingle = false
            ).apply(paddingBottom := "3px"),
            div(MembersModal.settingsButton(chatFocus()), Styles.flexStatic, marginLeft.auto)
          )
        },
        Rx {
          ChatView(focusState.copy(focusedId = chatFocus())).apply(
            height := "100%",
          )
        }
      )
    )
  }
}
