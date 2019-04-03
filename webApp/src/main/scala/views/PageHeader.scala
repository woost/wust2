package wust.webApp.views

import clipboard.ClipboardJS
import fontAwesome._
import googleAnalytics.Analytics
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph.{Node, Edge, GraphChanges}
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor.hue
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.{DragItem, DragContainer}
import wust.webApp.jsdom.{Navigator, ShareData}
import wust.webApp.outwatchHelpers._
import wust.webApp.search.Search
import wust.webApp.state._
import wust.webApp.views.Components.{renderNodeData, _}

import scala.collection.breakOut
import scala.scalajs.js
import scala.util.{Failure, Success}
import pageheader.components.{TabContextParms, TabInfo, customTab, doubleTab, singleTab}


object PageHeader {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div.static(keyValue)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "pageheader",

        Rx {
          val graph = state.graph()
          val page = state.page()
          val pageNode = page.parentId.flatMap(graph.nodesByIdGet)
          pageNode.map { pageNode => pageRow(state, pageNode) }
        },
      )
    })
  }

  private def pageRow(state: GlobalState, pageNode: Node)(implicit ctx: Ctx.Owner): VDomModifier = {
    val maxLength = if(BrowserDetect.isPhone) Some(30) else Some(60)

    val channelTitle = Components.nodeCardAsOneLineText(pageNode).apply(
      cls := "pageheader-channeltitle",
      registerDragContainer(state, DragContainer.Chat),
      DragItem.fromNodeRole(pageNode.id, pageNode.role).map(drag(_)),
      Components.sidebarNodeFocusMod(state.rightSidebarNode, pageNode.id),
      Components.showHoveredNode(state, pageNode.id),
      Components.readObserver(state, pageNode.id)
    )

    val channelMembersList = Rx {
      val hasBigScreen = state.screenSize() != ScreenSize.Small
      hasBigScreen.ifTrue[VDomModifier](channelMembers(state, pageNode).apply(marginLeft := "5px", marginRight := "5px", lineHeight := "0")) // line-height:0 fixes vertical alignment, minimum fit one member
    }

    val permissionIndicator = Rx {
      val level = Permission.resolveInherited(state.graph(), pageNode.id)
      div(level.icon, Styles.flexStatic, UI.popup("bottom center") := level.description, marginRight := "5px")
    }

    div(
      paddingTop := "5px",
      paddingLeft := "5px",
      paddingRight := "10px",

      Styles.flex,
      alignItems.flexEnd,
      flexWrap := "wrap-reverse",

      ViewSwitcher(state, pageNode.id).apply(Styles.flexStatic, alignSelf.flexStart, marginRight := "5px"),
      div(
        Styles.flex,
        justifyContent.spaceBetween,
        flexGrow := 1,
        flexShrink := 2,
        div(
          Styles.flex,
          alignItems.center,
          pageNode.role match {
            case NodeRole.Project => 
              channelTitle.prepend(
                Styles.flex,
                nodeAvatar(pageNode, size = 25)(marginRight := "5px", alignSelf.center)
              )
            case _ =>
              channelTitle
          },
          flexShrink := 3,
        ),
        div(
          Styles.flex,
          alignItems.center,
          Components.automatedNodesOfNode(state, pageNode.id),
          channelMembersList,
          permissionIndicator,
          menuItems(state, pageNode)
        )
      ),
    )
  }

  private def menuItems(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VDomModifier = {
    val isSpecialNode = Rx {
      //TODO we should use the permission system here and/or share code with the settings menu function
      channel.id == state.user().id
    }
    val isBookmarked = PageSettingsMenu.nodeIsBookmarked(state, channel.id)

    val buttonStyle = VDomModifier(Styles.flexStatic, cursor.pointer)

    val pinButton = Rx {
      val hideBookmarkButton = isSpecialNode() || isBookmarked()
      hideBookmarkButton.ifFalse[VDomModifier](PageSettingsMenu.addToChannelsButton(state, channel).apply(
        cls := "mini",
        buttonStyle,
        marginRight := "5px"
      ))
    }

    VDomModifier(
      pinButton,
      PageSettingsMenu(state, channel.id).apply(buttonStyle, fontSize := "20px"),
    )
  }

  def channelMembers(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      cls := "tiny-scrollbar",
      overflowX.auto, // make scrollable for long member lists
      overflowY.hidden,
      registerDragContainer(state),
      Rx {
        val graph = state.graph()
        val nodeIdx = graph.idToIdx(channel.id)
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
