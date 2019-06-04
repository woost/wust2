package wust.webApp.views

import wust.sdk.Colors
import clipboard.ClipboardJS
import fontAwesome._
import googleAnalytics.Analytics
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{ CommonStyles, Styles, ZIndex }
import wust.graph.{ Node, Edge, GraphChanges }
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor.hue
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.{ DragItem, DragContainer }
import wust.webApp.jsdom.{ Navigator, ShareData }
import wust.webApp.outwatchHelpers._
import wust.webApp.search.Search
import wust.webApp.state._
import wust.webApp.views.Components.{ renderNodeData, _ }

import scala.collection.breakOut
import scala.scalajs.js
import scala.util.{ Failure, Success }
import PageHeaderParts.{ TabContextParms, TabInfo, customTab, singleTab }

object PageHeader {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "pageheader",
        backgroundColor <-- state.pageStyle.map(_.pageBgColor),

        state.page.map(_.parentId.map(pageRow(state, _))),
      )
    })
  }

  private def pageRow(state: GlobalState, pageNodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {

    val pageNode = Rx {
      state.graph().nodesByIdOrThrow(pageNodeId)
    }

    val channelTitle = Rx {
      val node = pageNode()
      div(
        Components.renderNodeCardMod(node, Components.renderAsOneLineText, projectWithIcon = false),
        cls := "pageheader-channeltitle",
        DragItem.fromNodeRole(node.id, node.role).map(drag(_)),
        Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id),
        Components.showHoveredNode(state, node.id),
        div(
          Components.readObserver(
            state,
            node.id,
            labelModifier = border := s"1px solid ${Colors.unreadBorder}" // light border has better contrast on colored pageheader background
          ), 
          onClick.stopPropagation(View.Notifications).foreach(view => state.urlConfig.update(_.focus(view))),
          float.right,
          alignSelf.center,
        )
      )
    }

    val channelNotification = NotificationView.notificationsButton(state, pageNodeId, modifiers = VDomModifier(
      marginLeft := "5px",
    )).foreach(view => state.urlConfig.update(_.focus(view)))

    val hasBigScreen = Rx {
      state.screenSize() != ScreenSize.Small
    }

    val channelMembersList = Rx {
      VDomModifier.ifTrue(hasBigScreen())(channelMembers(state, pageNodeId).apply(marginLeft := "5px", marginRight := "5px", lineHeight := "0")) // line-height:0 fixes vertical alignment, minimum fit one member
    }

    val permissionLevel = Rx {
      Permission.resolveInherited(state.graph(), pageNodeId)
    }

    val permissionIndicator = Rx {
      val level = permissionLevel()
      div(level.icon, Styles.flexStatic, UI.popup("bottom center") := level.description, marginRight := "5px")
    }

    VDomModifier(
      div(
        Styles.flexStatic,
        marginLeft.auto,
        Styles.flex,
        alignItems.center,

        Rx{ VDomModifier.ifTrue(state.pageHasNotDeletedParents())(BreadCrumbs(state)(flexShrink := 1, marginRight := "10px")) },
        Rx {
          VDomModifier.ifTrue(state.screenSize() != ScreenSize.Small)(
            ViewFilter.filterBySearchInputWithIcon(state).apply(marginLeft.auto),
            FeedbackForm(state)(ctx)(marginLeft.auto, Styles.flexStatic),
            AuthControls.authStatus(state).map(_(Styles.flexStatic))
          )
        },
      ),
      div(
        paddingTop := "5px",

        Styles.flex,
        alignItems.center,
        flexWrap := "wrap-reverse",

        ViewSwitcher(state, pageNodeId).apply(Styles.flexStatic, alignSelf.flexStart, marginRight := "5px"),
        div(
          Styles.flex,
          justifyContent.spaceBetween,
          flexGrow := 1,
          flexShrink := 2,
          div(
            Styles.flex,
            alignItems.center,
            flexShrink := 3,

            permissionIndicator,
            channelTitle,

            channelNotification,
            marginBottom := "2px", // else nodecards in title overlap
          ),
          div(
            Styles.flex,
            alignItems.center,
            Components.automatedNodesOfNode(state, pageNodeId),
            channelMembersList,

            menuItems(state, pageNodeId)
          )
        ),
      )
    )
  }

  private def menuItems(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val isSpecialNode = Rx {
      //TODO we should use the permission system here and/or share code with the settings menu function
      channelId == state.userId()
    }
    val isBookmarked = PageSettingsMenu.nodeIsBookmarked(state, channelId)

    val buttonStyle = VDomModifier(Styles.flexStatic, cursor.pointer)

    val pinButton = Rx {
      val hideBookmarkButton = isSpecialNode() || isBookmarked()
      hideBookmarkButton.ifFalse[VDomModifier](PageSettingsMenu.addToChannelsButton(state, channelId).apply(
        cls := "mini",
        buttonStyle,
        marginRight := "5px"
      ))
    }

    VDomModifier(
      pinButton,
      PageSettingsMenu(state, channelId).apply(buttonStyle, fontSize := "20px"),
    )
  }

  def channelMembers(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      cls := "tiny-scrollbar",
      overflowX.auto, // make scrollable for long member lists
      overflowY.hidden,
      registerDragContainer(state),
      Rx {
        val graph = state.graph()
        val nodeIdx = graph.idToIdxOrThrow(channelId)
        val members = graph.membersByIndex(nodeIdx)

        members.map(user => div(
          Avatar.user(user.id)(
            marginLeft := "2px",
            width := "22px",
            height := "22px",
            cls := "avatar",
            marginBottom := "2px",
          ),
          Styles.flexStatic,
          cursor.grab,
          UI.popup("bottom center") := Components.displayUserName(user.data)
        )(
            drag(payload = DragItem.User(user.id)),
          ))(breakOut): js.Array[VNode]
      }
    )
  }

}
