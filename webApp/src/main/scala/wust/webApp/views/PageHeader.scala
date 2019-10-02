package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph.Node
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Ownable, UI }
import wust.webUtil.Elements._
import wust.css.Styles
import wust.ids._
import wust.sdk.Colors
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{ drag, registerDragContainer }

import scala.collection.breakOut
import scala.scalajs.js

object PageHeader {

  def apply(viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "pageheader",

        GlobalState.page.map(_.parentId.map(pageRow(_, viewRender))),
      )
    })
  }

  private def pageRow(pageNodeId: NodeId, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VDomModifier = {

    val pageStyle = PageStyle.ofNode(pageNodeId)
    val pageNode = Rx {
      GlobalState.graph().nodesByIdOrThrow(pageNodeId)
    }

    val channelTitle = div(
      backgroundColor := pageStyle.pageBgColor,
      cls := "pageheader-channeltitle",

      Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, pageNodeId),
      Components.showHoveredNode(pageNodeId),
      registerDragContainer,
      Rx {
        val node = pageNode()

        VDomModifier(
          Components.renderNodeCardMod(node, Components.renderAsOneLineText(_), projectWithIcon = false),
          DragItem.fromNodeRole(node.id, node.role).map(DragComponents.drag(_)),
        )
      },

      div(
        UnreadComponents.readObserver(
          pageNodeId,
          labelModifier = border := s"1px solid ${Colors.unreadBorder}" // light border has better contrast on colored pageheader background
        ),
        // onClick.stopPropagation.use(View.Notifications).foreach(view => GlobalState.urlConfig.update(_.focus(view))),
        float.right,
        alignSelf.center,
      )
    )

    val channelNotification = UnreadComponents
      .activityButtons(pageNodeId, modifiers = VDomModifier(marginLeft := "5px"))
      .foreach(view => GlobalState.urlConfig.update(_.focus(view)))

    val channelMembersList = Rx {
      VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
        // line-height:0 fixes vertical alignment, minimum fit one member
        SharedViewElements.channelMembers(pageNodeId).apply(marginLeft := "5px", lineHeight := "0", maxWidth := "200px")
      )
    }


    VDomModifier(
      backgroundColor := pageStyle.pageBgColor,

      GlobalState.presentationMode.map {
        case PresentationMode.Full => div(
          Styles.flexStatic,

          Styles.flex,
          alignItems.center,

          Rx {
            VDomModifier(
              VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
                breadCrumbs,
                bookmarkButton(pageNodeId),
                AuthControls.authStatusOnColoredBackground.map(_(Styles.flexStatic, marginLeft.auto, marginTop := "3px"))
              )
            )
          },
        )
        case PresentationMode.ContentOnly => VDomModifier.empty
      },

      div(
        paddingTop := "5px",

        Styles.flex,
        alignItems.center,
        flexWrap := "wrap-reverse",

        GlobalState.presentationMode.map {
          case PresentationMode.Full => ViewSwitcher(pageNodeId)
            .mapResult(_.apply(
              Styles.flexStatic,
              alignSelf.flexStart,
              marginRight := "5px",
              id := "tutorial-pageheader-viewswitcher",
              MainTutorial.onDomMountContinue,
            ))
            .foreach{ view =>
              view match {
                case View.Kanban => FeatureState.use(Feature.SwitchToKanbanInPageHeader)
                case View.List   => FeatureState.use(Feature.SwitchToChecklistInPageHeader)
                case View.Chat   => FeatureState.use(Feature.SwitchToChatInPageHeader)
                case _           =>
              }
            }
          case PresentationMode.ContentOnly => VDomModifier.empty
        },

        div(
          Styles.flex,
          alignItems.center,
          marginLeft.auto,

          channelTitle,
          channelNotification,
          channelMembersList,
          GlobalState.presentationMode.map {
            case PresentationMode.Full => MembersModal.settingsButton(pageNodeId)
            case PresentationMode.ContentOnly => VDomModifier.empty
          },
          id := "tutorial-pageheader-title",
          marginBottom := "2px", // else nodecards in title overlap

          marginRight.auto,
        ),

        div(
          Styles.flex,
          alignItems.center,
          justifyContent.flexEnd,

          Rx{
            VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
              ViewFilter.filterBySearchInputWithIcon.apply(marginRight := "5px")
            )
          },
          GlobalState.presentationMode.map {
            case PresentationMode.Full => PageSettingsMenu(pageNodeId).apply(fontSize := "20px")
            case PresentationMode.ContentOnly => VDomModifier.empty
          }
        )
      )
    )
  }

  def breadCrumbs(implicit ctx: Ctx.Owner): Rx[VDomModifier] = Rx{
    VDomModifier.ifTrue(GlobalState.pageHasNotDeletedParents()) {
      val page = GlobalState.page()
      val graph = GlobalState.rawGraph()

      div(
        page.parentId.map { parentId =>
          BreadCrumbs.modifier(
            graph,
            start = BreadCrumbs.EndPoint.None,
            end = BreadCrumbs.EndPoint.Node(parentId),
            clickAction = nid => GlobalState.focus(nid)
          )
        },
        flexShrink := 1,
        marginRight := "10px"
      )
    }
  }

  private def bookmarkButton(pageNodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val isSpecialNode = Rx {
      //TODO we should use the permission system here and/or share code with the settings menu function
      pageNodeId == GlobalState.userId()
    }
    val isBookmarked = PageSettingsMenu.nodeIsBookmarked(pageNodeId)
    val showBookmarkButton = Rx{ !isSpecialNode() && !isBookmarked() }

    val buttonStyle = VDomModifier(Styles.flexStatic, cursor.pointer)

    val button = Rx {
      VDomModifier.ifTrue(showBookmarkButton())(
        PageSettingsMenu.addToChannelsButton(pageNodeId).apply(
          cls := "mini",
          buttonStyle,
          marginRight := "5px"
        )
      )
    }

    button
  }

}
