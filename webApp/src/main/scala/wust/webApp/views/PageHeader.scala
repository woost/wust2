package wust.webApp.views

import wust.webUtil.BrowserDetect
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Ownable, UI}
import wust.css.Styles
import wust.ids._
import wust.sdk.Colors
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}

import scala.collection.breakOut
import scala.scalajs.js
import monix.reactive.Observer

object PageHeader {

  def apply(state: GlobalState, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "pageheader",
        backgroundColor <-- state.pageStyle.map(_.pageBgColor),

        state.page.map(_.parentId.map(pageRow(state, _, viewRender))),
      )
    })
  }

  private def pageRow(state: GlobalState, pageNodeId: NodeId, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VDomModifier = {

    val pageNode = Rx {
      state.graph().nodesByIdOrThrow(pageNodeId)
    }

    val channelTitle = Rx {
      val node = pageNode()
      div(
        Components.renderNodeCardMod(node, Components.renderAsOneLineText, projectWithIcon = false),
        cls := "pageheader-channeltitle",
        DragItem.fromNodeRole(node.id, node.role).map(DragComponents.drag(_)),
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
      VDomModifier.ifTrue(hasBigScreen())(SharedViewElements.channelMembers(state, pageNodeId).apply(marginLeft := "5px", marginRight := "5px", lineHeight := "0")) // line-height:0 fixes vertical alignment, minimum fit one member
    }

    val permissionLevel = Rx {
      Permission.resolveInherited(state.rawGraph(), pageNodeId)
    }


    val filterControls = VDomModifier(
      ViewFilter.filterBySearchInputWithIcon(state).apply(marginLeft.auto),
      MovableElement.withToggleSwitch(
        Seq(
          FilterWindow.movableWindow(state, MovableElement.RightPosition(100, 200)),
          TagList.movableWindow(state, viewRender, MovableElement.RightPosition(100, 400)),
        ),
        enabled = state.urlConfig.map(c => c.pageChange.page.parentId.isDefined && c.view.forall(_.isContent)),
        resizeEvent = state.rightSidebarNode.toTailObservable.map(_ => ()),
      )
    )

    val breadCrumbs = Rx{ 
      VDomModifier.ifTrue(state.pageHasNotDeletedParents())(
        BreadCrumbs(state)(flexShrink := 1, marginRight := "10px")
      ) 
    }

    VDomModifier(
      div(
        Styles.flexStatic,

        Styles.flex,
        alignItems.center,

        breadCrumbs,
        Rx {
          VDomModifier.ifTrue(state.screenSize() != ScreenSize.Small)(
            AnnouncekitWidget.widget(state).apply(marginLeft.auto, Styles.flexStatic),
            FeedbackForm(state)(ctx)(Styles.flexStatic),
            AuthControls.authStatus(state, buttonStyleLoggedOut = "inverted", buttonStyleLoggedIn = "inverted").map(_(Styles.flexStatic))
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

            permissionLevel.map(Permission.permissionIndicator(_, marginRight := "5px")),
            channelTitle,
            channelNotification,
            marginBottom := "2px", // else nodecards in title overlap
          ),
          Rx{ VDomModifier.ifTrue(state.screenSize() != ScreenSize.Small)(
            filterControls
          )},
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

}
