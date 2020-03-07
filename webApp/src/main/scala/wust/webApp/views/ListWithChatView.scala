package wust.webApp.views

import wust.sdk.Colors
import flatland._
import fontAwesome.freeSolid
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import colibri._
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
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, Ownable }

import scala.collection.{ breakOut, mutable }
import scala.scalajs.js

object ListWithChatView {

  def apply(originalFocusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val topLevelId: NodeId = originalFocusState.focusedId
    def focusOrToplevel(nodeIdOpt: Option[NodeId]): NodeId = nodeIdOpt.getOrElse(topLevelId)
    val chatFocus: Var[NodeId] = Var(focusOrToplevel(GlobalState.subPage.now.parentId))

    // sync with urlConfig.subPage
    chatFocus.triggerLater{ nodeId =>
      if (nodeId == topLevelId)
        GlobalState.focusSubPage(None)
      else
        GlobalState.focusSubPage(Some(nodeId))
    }
    GlobalState.subPage.triggerLater{ page => chatFocus() = focusOrToplevel(page.parentId) }

    val focusState = originalFocusState.copy(
      contextParentIdAction = { nodeId => chatFocus() = nodeId },
      onItemSingleClick = { focusPreference =>
        val nodeId = focusPreference.nodeId
        focusPreference.view match {
          // clicking on card and comment icon toggles in embedded chat view
          case None | Some(View.Chat) => chatFocus() = if (chatFocus.now == nodeId) topLevelId else nodeId
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
        div(
          flexShrink := 0,
          Styles.flex,
          flexDirection.column,
          backgroundColor := Colors.contentBgShade,
          h2(
            "Threads", fontSize := "18px", padding := "10px", marginBottom := "0px", paddingBottom := "0px"
          ),
          ListView(focusState, autoFocusInsert = false, showNestedInputFields = true).apply(
            width := "300px",
            minWidth := "300px",
            height := "100%",
          )
        )
      ),
      div(
        flex := "3",
        minWidth := "300px",
        flexShrink := 0,
        Styles.flex,
        flexDirection.column,
        chatHeader(originalFocusState, focusState, chatFocus, topLevelId),
        Rx {
          val chatFocusedId = chatFocus()
          ChatView(focusState.copy(focusedId = chatFocusedId)).apply(
            height := "100%",
          )
        }
      )
    )
  }

  private def chatHeader(originalFocusState: FocusState, focusState: FocusState, chatFocus: Var[NodeId], topLevelId: NodeId)(implicit ctx: Ctx.Owner) = {
    val buttonMods = VDomModifier(
      Styles.flexStatic,
      padding := "5px 0.5em",
      cls := "hover-full-opacity",
    )

    Rx {
      val chatFocusedId = chatFocus()
      val graph = GlobalState.rawGraph()
      VDomModifier.ifNot(chatFocusedId == topLevelId)(
        div(
          // backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(chatFocusedId)).toHex,
          Styles.flexStatic,
          Styles.flex,
          alignItems.center,

          padding := "8px 10px",
          backButton(chatFocus, topLevelId),
          BreadCrumbs(
            graph,
            start = BreadCrumbs.EndPoint.Node(focusState.focusedId, inclusive = true),
            end = BreadCrumbs.EndPoint.Node(chatFocusedId),
            nodeId => chatFocus() = nodeId,
            hideIfSingle = false
          ).apply(paddingBottom := "3px"),

          div(
            div(cls := "fa-fw", Icons.edit),
            UI.tooltip := "Edit Thread",
            buttonMods,
            marginLeft := "10px",
            onClickDefault.foreach { originalFocusState.onItemSingleClick(FocusPreference(chatFocusedId)) },
          ),
          MembersModal.settingsButton(chatFocusedId, analyticsVia = "ListWithChatView.chatheader", tooltip = "Add members to this thread").apply(buttonMods),
        )
      )
    }
  }

  def backButton(chatFocus: Var[NodeId], topLevelId: NodeId)(implicit ctx: Ctx.Owner) = Rx{
    VDomModifier.ifTrue(chatFocus() != topLevelId)(
      span(
        freeSolid.faArrowLeft,
        cls := "fa-fw",
        marginRight := "0.5em",
        cls := "hover-full-opacity",
        onClickDefault.foreach {
          chatFocus() = topLevelId
        }
      )
    )
  }

}
