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
      contextParentIdAction = { nodeId =>
        chatFocus() = nodeId
      },
      onItemSingleClick = { focusPreference =>
        val nodeId = focusPreference.nodeId
        focusPreference.view match {
          // clicking on card and comment icon toggles in embedded chat view
          case None | Some(View.Chat) => chatFocus() = if (chatFocus.now == nodeId) originalFocusState.focusedId else nodeId
          // clicking on other icons behaves as usual
          case Some(view)             => originalFocusState.onItemSingleClick(focusPreference)
        }
      },
      itemIsFocused = nodeId => chatFocus.map(_ == nodeId)
    )

    div(
      cls := "listwithchat-view",
      Styles.flex,
      ScreenSize.dontShowOnSmallScreen(
        ListView(focusState, autoFocusInsert = false).apply(
          width := "300px",
          height := "100%",
        )
      ),
      div(
        flex := "3",
        Styles.flex,
        flexDirection.column,
        Rx{
          val chatFocusedId = chatFocus()
          val focusedTopLevel = chatFocusedId == originalFocusState.focusedId
          val graph = GlobalState.rawGraph()
          VDomModifier.ifNot(focusedTopLevel)(
            div(
              Styles.flexStatic,
              Styles.flex,
              alignItems.center,

              padding := "8px 10px",
              BreadCrumbs(
                graph,
                start = BreadCrumbs.EndPoint.Node(focusState.focusedId, inclusive = true),
                end = BreadCrumbs.EndPoint.Node(chatFocusedId),
                nodeId => chatFocus() = nodeId,
                hideIfSingle = false
              ).apply(paddingBottom := "3px"),
              div(MembersModal.settingsButton(chatFocusedId, tooltip = "Add members to this thread").apply(padding := "5px 0.5em", backgroundColor := "transparent", color := "black", cls := "hover-full-opacity"), Styles.flexStatic)
            )
          )
        },
        Rx {
          val chatFocusedId = chatFocus()
          ChatView(focusState.copy(focusedId = chatFocusedId)).apply(
            height := "100%",
          )
        }
      )
    )
  }
}
