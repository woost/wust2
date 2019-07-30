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

  def apply(viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "pageheader",

        GlobalState.page.map(_.parentId.map(pageRow( _, viewRender))),
      )
    })
  }

  private def pageRow(pageNodeId: NodeId, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VDomModifier = {

    val pageStyle = PageStyle.ofNode(pageNodeId)
    val pageNode = Rx {
      GlobalState.graph().nodesByIdOrThrow(pageNodeId)
    }

    val channelTitle = Rx {
      val node = pageNode()
      div(
        Components.renderNodeCardMod(node, Components.renderAsOneLineText( _), projectWithIcon = false),
        backgroundColor := pageStyle.pageBgColor,

        cls := "pageheader-channeltitle",
        Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, node.id),
        Components.showHoveredNode( node.id),
        registerDragContainer,
        DragItem.fromNodeRole(node.id, node.role).map(DragComponents.drag(_)),

        div(
          UnreadComponents.readObserver(
            node.id,
            labelModifier = border := s"1px solid ${Colors.unreadBorder}" // light border has better contrast on colored pageheader background
          ), 
          onClick.stopPropagation(View.Notifications).foreach(view => GlobalState.urlConfig.update(_.focus(view))),
          float.right,
          alignSelf.center,
        )
      )
    }

    val channelNotification = UnreadComponents.notificationsButton( pageNodeId, modifiers = VDomModifier(
      marginLeft := "5px",
    )).foreach(view => GlobalState.urlConfig.update(_.focus(view)))

    val hasBigScreen = Rx {
      GlobalState.screenSize() != ScreenSize.Small
    }

    val channelMembersList = Rx {
      VDomModifier.ifTrue(hasBigScreen())(SharedViewElements.channelMembers( pageNodeId).apply(marginLeft := "5px", marginRight := "5px", lineHeight := "0", maxWidth := "200px")) // line-height:0 fixes vertical alignment, minimum fit one member
    }

    val permissionLevel = Rx {
      Permission.resolveInherited(GlobalState.rawGraph(), pageNodeId)
    }


    val filterControls = VDomModifier(
      ViewFilter.filterBySearchInputWithIcon.apply(marginLeft.auto),
      MovableElement.withToggleSwitch(
        Seq(
          FilterWindow.movableWindow( MovableElement.RightPosition(100, 200)),
          TagList.movableWindow( viewRender, MovableElement.RightPosition(100, 400)),
        ),
        enabled = GlobalState.urlConfig.map(c => c.pageChange.page.parentId.isDefined && c.view.forall(_.isContent)),
        resizeEvent = GlobalState.rightSidebarNode.toTailObservable.map(_ => ()),
      )
    )

    val breadCrumbs = Rx{ 
      VDomModifier.ifTrue(GlobalState.pageHasNotDeletedParents())(
        BreadCrumbs(flexShrink := 1, marginRight := "10px")
      ) 
    }

    VDomModifier(
      backgroundColor := pageStyle.pageBgColor,
      div(
        Styles.flexStatic,

        Styles.flex,
        alignItems.center,

        breadCrumbs,
        Rx {
          VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
            AnnouncekitWidget.widget.apply(marginLeft.auto, Styles.flexStatic),
            FeedbackForm(ctx)(Styles.flexStatic),
            AuthControls.authStatus( buttonStyleLoggedOut = "inverted", buttonStyleLoggedIn = "inverted").map(_(Styles.flexStatic))
          )
        },
      ),
      div(
        paddingTop := "5px",

        Styles.flex,
        alignItems.center,
        flexWrap := "wrap-reverse",

        ViewSwitcher( pageNodeId).apply(Styles.flexStatic, alignSelf.flexStart, marginRight := "5px"),
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
          Rx{ VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
            filterControls
          )},
          div(
            Styles.flex,
            alignItems.center,
            channelMembersList,

            menuItems( pageNodeId)
          )
        ),
      )
    )
  }

  private def menuItems(channelId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val isSpecialNode = Rx {
      //TODO we should use the permission system here and/or share code with the settings menu function
      channelId == GlobalState.userId()
    }
    val isBookmarked = PageSettingsMenu.nodeIsBookmarked( channelId)

    val buttonStyle = VDomModifier(Styles.flexStatic, cursor.pointer)

    val pinButton = Rx {
      val hideBookmarkButton = isSpecialNode() || isBookmarked()
      hideBookmarkButton.ifFalse[VDomModifier](PageSettingsMenu.addToChannelsButton( channelId).apply(
        cls := "mini",
        buttonStyle,
        marginRight := "5px"
      ))
    }

    VDomModifier(
      pinButton,
      PageSettingsMenu( channelId).apply(buttonStyle, fontSize := "20px"),
    )
  }

}
