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
import wust.graph.Node.User
import wust.graph._
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor.hue
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.{Navigator, ShareData}
import wust.webApp.outwatchHelpers._
import wust.webApp.search.Search
import wust.webApp.state._
import wust.webApp.views.Components.{renderNodeData, _}
import wust.webApp.views.UI.ModalConfig

import scala.collection.breakOut
import scala.scalajs.js
import scala.util.{Failure, Success}


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

  private def pageRow(state: GlobalState, pageNode: Node)(implicit ctx: Ctx.Owner): VNode = {
    val maxLength = if(BrowserDetect.isPhone) Some(30) else Some(250)
    val channelTitle = NodePermission.canWrite(state, pageNode.id).map { canWrite =>
      pageNode.role match {
        case NodeRole.Message | NodeRole.Task =>
          val node =
            if(!canWrite) nodeCard(pageNode)
            else {
              val editable = Var(false)
              nodeCardEditable(state, pageNode, editable).apply(onClick.stopPropagation(true) --> editable)
              // editableNodeOnClick(state, pageNode, state.eventProcessor.changes, maxLength)(ctx)(
              //   onClick foreach { Analytics.sendEvent("pageheader", "editchanneltitle") }
              // )
            }
          node(cls := "pageheader-channeltitle")

        case _ => // usually NodeRole.Project
          val node =
            if(!canWrite) renderNodeData(pageNode.data, maxLength)
            else {
              editableNodeOnClick(state, pageNode, maxLength)(ctx)(
                onClick.stopPropagation foreach { Analytics.sendEvent("pageheader", "editchanneltitle") }
              )
            }
          node(cls := "pageheader-channeltitle")

      }
    }


    val channelMembersList = Rx {
      val hasBigScreen = state.screenSize() != ScreenSize.Small
      hasBigScreen.ifTrue[VDomModifier](channelMembers(state, pageNode).apply(marginRight := "10px", lineHeight := "0")) // line-height:0 fixes vertical alignment
    }

    val permissionIndicator = Rx {
      val level = Permission.resolveInherited(state.graph(), pageNode.id)
      div(level.icon, UI.tooltip("bottom center") := level.description, zIndex := ZIndex.tooltip - 15)
    }

    div(
      paddingTop := "5px",
      paddingLeft := "5px",
      paddingRight := "10px",
      backgroundColor := BaseColors.pageBg.copy(h = hue(pageNode.id)).toHex,

      Styles.flex,
      alignItems.center,
      flexWrap.wrap,

      div(
        Styles.flex,
        alignItems.center,
        justifyContent.flexStart,
        flexGrow := 1,
        flexShrink := 2,
        padding := "0 5px",
        nodeAvatar(pageNode, size = 30)(marginRight := "5px", flexShrink := 0),
        channelTitle.map(_ (marginRight := "5px")),
        Components.automatedNodesOfNode(state, pageNode),
        channelMembersList,
        permissionIndicator,
        paddingBottom := "5px",
      ),
      menu(state, pageNode).apply(alignSelf.flexEnd, marginLeft.auto),
    )
  }

  private def menu(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    val isSpecialNode = Rx {
      //TODO we should use the permission system here and/or share code with the settings menu function
      channel.id == state.user().id
    }
    val isBookmarked = PageSettingsMenu.nodeIsBookmarked(state, channel.id)

    val buttonStyle = VDomModifier(Styles.flexStatic, margin := "5px", fontSize := "20px", cursor.pointer)

    div(
      Styles.flex,
      alignItems.center,
      minWidth.auto,
      Rx {
        val hideBookmarkButton = isSpecialNode() || isBookmarked()
        hideBookmarkButton.ifFalse[VDomModifier](PageSettingsMenu.addToChannelsButton(state, channel).apply(
          Styles.flexStatic,
          marginTop := "3px",
          marginBottom := "3px",
        ))
      },
      //      notifyControl(state, channel).apply(buttonStyle),
      state.isFilterActive.map(_.ifTrue[VDomModifier] {
        div(
          Elements.icon(Icons.filter),
          color := "green",
          onClick.stopPropagation(state.defaultTransformations) --> state.graphTransformations,
          cursor.pointer,
          UI.tooltip("bottom right") := "A filter is active. Click to reset to default.",
        )
      }),
      viewSwitcher(state).apply(Styles.flexStatic, alignSelf.flexEnd),
      Rx {
        PageSettingsMenu(state, channel.id).apply(buttonStyle, marginLeft := "15px")
      },
    )
  }

  def channelMembers(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexWrap.wrap,
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
          UI.tooltip("bottom center") := Components.displayUserName(user.data)
        )(
          drag(payload = DragItem.User(user.id)),
        ))(breakOut): js.Array[VNode]
      }
    )
  }

  private def viewSwitcher(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    case class TabInfo(targetView : View,
                       icon : IconDefinition,
                       wording : String,
                       numItems : Int)
    def isActiveTab(currentView: View, tabInfo : TabInfo) =
      currentView.viewKey == tabInfo.targetView.viewKey

    def getActivityStateCssClass(currentView: View, tabInfo : TabInfo) =
      cls := (if (isActiveTab(currentView, tabInfo)) "active"
              else "inactive")

    def getBackgroundColor(currentView: View, pageStyle: PageStyle, tabInfo : TabInfo) = Rx {
      val pageStyle = state.pageStyle()
      VDomModifier.ifTrue(isActiveTab(currentView, tabInfo))(
        backgroundColor := pageStyle.bgLightColor,
        borderBottomColor := pageStyle.bgLightColor)
    }

    def getTooltip(tabInfo : TabInfo) =
      UI.tooltip("bottom right") :=
        s"${tabInfo.targetView.toString}${(tabInfo.numItems > 0).ifTrue[String](
                                            s": ${tabInfo.numItems} ${tabInfo.wording}")}"

    def switchView(currentView : View, targetView : View) = {
      state.urlConfig.update(_.focus(targetView))
      Analytics.sendEvent("viewswitcher", "switch", currentView.viewKey)
    }

    /// @return a single iconized tab for switching to the respective view
    def singleTab(currentView: View, pageStyle: PageStyle, tabInfo: TabInfo) = {
      div(
        cls := "viewswitcher-item single",
        getActivityStateCssClass(currentView, tabInfo),
        getBackgroundColor(currentView, pageStyle, tabInfo),
        getTooltip(tabInfo),

        div(cls := "fa-fw", tabInfo.icon),
        VDomModifier.ifTrue(tabInfo.numItems > 0)(span(tabInfo.numItems, paddingLeft := "7px")),

        onClick.stopPropagation foreach switchView(currentView, tabInfo.targetView)
        ,
      )
    }

    /// @return like singleTab, but two iconized tabs grouped together visually to switch the current view
    def doubleTab(currentView: View, pageStyle: PageStyle, leftTabInfo : TabInfo, rightTabInfo : TabInfo) = {
      VDomModifier (
        div(
          cls := "viewswitcher-item double left",
          getActivityStateCssClass(currentView, leftTabInfo),
          getBackgroundColor(currentView, pageStyle, leftTabInfo),
          getTooltip(leftTabInfo),
          /// above tooltip sets zIndex to 1500. We need to increase it, since the right tab would otherwise hide
          /// this tabs shadow
          zIndex := ZIndex.tooltip + 10,
          onClick.stopPropagation foreach switchView(currentView, leftTabInfo.targetView),
          div(cls := "fa-fw", leftTabInfo.icon),
        ),
        div(
          cls := "viewswitcher-item double right",
          getActivityStateCssClass(currentView, rightTabInfo),
          getBackgroundColor(currentView, pageStyle, rightTabInfo),
          getTooltip(rightTabInfo),
          onClick.stopPropagation foreach switchView(currentView, rightTabInfo.targetView),
          div(cls := "fa-fw", rightTabInfo.icon),
          VDomModifier.ifTrue(leftTabInfo.numItems > 0)(span(leftTabInfo.numItems, paddingLeft := "7px")),
          ),
      ) }

    div(
      marginLeft := "5px",
      Styles.flex,
      justifyContent.center,
      alignItems.center,
      minWidth.auto,

      Rx {
        val currentView = state.view()
        val pageStyle = state.pageStyle()

        val (numMsg, numTasks, numFiles) = if(!BrowserDetect.isPhone) {
          state.page.now.parentId.fold((0, 0, 0)) { pid =>
            val graph = state.graph.now
            val nodeIdx = graph.idToIdx(pid)
            val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)
            val taskChildrenCount = graph.taskChildrenIdx.sliceLength(nodeIdx)
            val filesCount = graph.pageFiles(pid).length
            (messageChildrenCount, taskChildrenCount, filesCount)
          }
        } else (0, 0, 0)

        Seq(
          singleTab(currentView, pageStyle, TabInfo(View.Dashboard, Icons.dashboard, "dashboard", 0)),
          doubleTab(currentView, pageStyle,
                    TabInfo(View.Chat, Icons.chat, "messages", numMsg),
                    TabInfo(View.Thread, Icons.thread, "messages", numMsg)),
          doubleTab(currentView, pageStyle,
                    TabInfo(View.List, Icons.list, "tasks", numTasks),
                    TabInfo(View.Kanban, Icons.kanban, "tasks", numTasks)),
          singleTab(currentView, pageStyle, TabInfo(View.Files, Icons.files, "files", numFiles)),
        )
      }
    )

  }

}
