package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.ids._
import wust.sdk.Colors
import wust.webApp.dragdrop.DragItem
import wust.webApp.state._
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._
import GlobalState.showOnlyInFullMode

object PageHeader {

  def apply(viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VNode = {
    div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "pageheader",

        GlobalState.page.map(_.parentId.map{ parentId =>
          pageRow(parentId, viewRender)
        }),
      )
    })
  }

  private def pageRow(pageNodeId: NodeId, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VDomModifier = {

    val pageStyle = PageStyle.ofNode(pageNodeId)
    val pageNodeOpt = Rx {
      GlobalState.graph().nodesById(pageNodeId)
    }

    val focusState: Rx[Option[FocusState]] = Rx {
      val viewConfig = GlobalState.viewConfig()
      GlobalState.mainFocusState(viewConfig)
    }

    def channelTitle(focusState: FocusState)(implicit ctx: Ctx.Owner) = div(
      backgroundColor := pageStyle.pageBgColor,
      cls := "pageheader-channeltitle",

      Components.sidebarNodeFocusMod(pageNodeId, focusState),
      Components.showHoveredNode(pageNodeId),
      registerDragContainer,
      Rx {
        pageNodeOpt().map { node =>
          VDomModifier(
            Components.renderNodeCardMod(node, Components.renderAsOneLineText(_), projectWithIcon = false),
            DragItem.fromNodeRole(node.id, node.role).map(DragComponents.drag(_)),
          )
        }
      },

      showOnlyInFullMode(div(
        UnreadComponents.readObserver(
          pageNodeId,
          labelModifier = border := s"1px solid ${Colors.unreadBorder}" // light border has better contrast on colored pageheader background
        ),
        onClick.stopPropagation.use(View.Notifications).foreach(view => GlobalState.urlConfig.update(_.focus(view))),
        float.right,
        alignSelf.center,
      ))
    )

    val channelNotification = UnreadComponents
      .activityButtons(pageNodeId, modifiers = VDomModifier(marginLeft := "5px"))
      .foreach(view => GlobalState.urlConfig.update(_.focus(view)))

    val channelMembersList = Rx {
      VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
        // line-height:0 fixes vertical alignment, minimum fit one member
        SharedViewElements.channelMembers(pageNodeId, enableClickFilter = true).apply(marginLeft := "5px", lineHeight := "0", maxWidth := "200px")
      )
    }

    VDomModifier(
      backgroundColor := pageStyle.pageBgColor,

      showOnlyInFullMode(
        div(
          Styles.flexStatic,

          Styles.flex,
          alignItems.center,

          Rx {
            VDomModifier(
              VDomModifier.ifTrue(GlobalState.screenSize() != ScreenSize.Small)(
                breadCrumbs,
                bookmarkButton(pageNodeId),

                PaymentView.focusButton(color.white),

                AuthControls.authStatusOnColoredBackground(showLogin = false).map(_(Styles.flexStatic, marginLeft.auto, marginTop := "3px"))
              )
            )
          },
        )
      ),

      div(
        paddingTop := "5px",

        Styles.flex,
        alignItems.center,
        flexWrap := "wrap-reverse",

        ViewSwitcher(pageNodeId)
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
          },

        div(
          Styles.flex,
          alignItems.center,
          marginLeft.auto,

          Rx {
            focusState().map(focusState => channelTitle(focusState))
          },

          channelMembersList,
          showOnlyInFullMode(div(MembersModal.settingsButton(pageNodeId).apply(
            color := "white",
          ), id := "tutorial-pageheader-sharing"), additionalModes = List(PresentationMode.ThreadTracker)),
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
          showOnlyInFullMode(div(channelNotification, marginRight := "8px")),
          showOnlyInFullMode(PageSettingsMenu(pageNodeId).apply(fontSize := "20px"))
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
