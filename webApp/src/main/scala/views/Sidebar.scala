package wust.webApp.views

import colorado._
import wust.webApp.dragdrop._
import fontAwesome.freeSolid
import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.util.RichBoolean
import wust.webApp.{BrowserDetect, Icons, Ownable}
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.SharedViewElements._

import scala.collection.breakOut

object Sidebar {

  def apply(state: GlobalState): VNode = {
    val smallIconSize = 40

    def authStatus(implicit ctx: Ctx.Owner) = SharedViewElements.authStatus(state).map(_(alignSelf.center, marginTop := "30px", marginBottom := "10px"))

    def closedSidebar(implicit ctx: Ctx.Owner) = VDomModifier(
      cls := "sidebar",
      minWidth := s"${ smallIconSize }px",
      Rx{ VDomModifier.ifNot(state.topbarIsVisible())(Topbar.hamburger(state)) },
      channelIcons(state, smallIconSize),
      newProjectButton(state, "+").apply(
        cls := "newChannelButton-small " + buttonStyles,
        UI.popup("right center") := "New Project",
        onClick foreach { Analytics.sendEvent("sidebar_closed", "newchannel") }
      ),
      onSwipeRight(true) --> state.sidebarOpen,
    )

    def openSidebar(implicit ctx: Ctx.Owner) = VDomModifier(
      cls := "sidebar",
      Rx{ VDomModifier.ifNot(state.topbarIsVisible())(Topbar(state).apply(Styles.flexStatic)) },
      channels(state),
      newProjectButton(state).apply(
        cls := "newChannelButton-large " + buttonStyles,
        onClick foreach { Analytics.sendEvent("sidebar_open", "newchannel") }
      ),
    )

    def overlayOpenSidebar(implicit ctx: Ctx.Owner) = VDomModifier(
      cls := "overlay-sidebar",
      onClick(false) --> state.sidebarOpen,
      onSwipeLeft(false) --> state.sidebarOpen,
      div(
        openSidebar,
        authStatus
      )
    )

    def sidebarWithOverlay(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
      closedSidebar,
      Rx {
        state.sidebarOpen() match {
          case true  => div(overlayOpenSidebar)
          case false => VDomModifier.empty
        }
      }
    )

    def sidebarWithExpand(implicit ctx: Ctx.Owner): VDomModifier = Rx {
      state.sidebarOpen() match {
        case true  => VDomModifier(openSidebar, (state.screenSize() == ScreenSize.Small).ifTrue[VDomModifier](authStatus), maxWidth := "202px") // maxWith = 200px sidebar + 2px border
        case false => closedSidebar
      }
    }

    div.static(keyValue)(Ownable { implicit ctx =>
      VDomModifier(
        if (BrowserDetect.isMobile) sidebarWithOverlay
        else sidebarWithExpand,
        registerDragContainer(state, DragContainer.Sidebar),
        drag(target = DragItem.Sidebar),
      )
    })
  }

  val buttonStyles = "tiny compact inverted grey"

  def channels(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    def channelLine(node: Node, pageParentId: Option[NodeId], pageStyle: PageStyle): VNode = {
      val selected = pageParentId contains node.id
      div(
        cls := "channel-line",
        selected.ifTrueSeq(
          Seq(
            color := CommonStyles.sidebarBgColor,
            backgroundColor := pageStyle.sidebarBgHighlightColor
          )
        ),

        channelIcon(state, node, selected, 30),

        {
          val rendered = renderNodeData(node.data, maxLength = Some(100))(cls := "channel-name")
          if (state.user.now.id == node.id) b(rendered) else rendered
        },

        onChannelClick(ChannelAction.Node(node.id))(state),
        onClick foreach { Analytics.sendEvent("sidebar_open", "clickchannel") },
        cls := "node",
        node match {
          case _:Node.Content => drag(DragItem.Channel(node.id))
          case _:Node.User => drag(target = DragItem.Channel(node.id))
        },

        channelFocusButton(state, node.id)(cls := "channel-line-hover-show", marginLeft := "auto")
      )
    }

    def channelList(channels: Tree, pageParentId: Option[NodeId], pageStyle: PageStyle, depth: Int = 0): VNode = {
      div(
        channelLine(channels.node, pageParentId, pageStyle),
        channels match {
          case Tree.Parent(_, children) => div(
            paddingLeft := "10px",
            fontSize := s"${ math.max(8, 14 - depth) }px",
            children.map { child => channelList(child, pageParentId, pageStyle, depth = depth + 1) }(breakOut): Seq[VDomModifier]
          )
          case Tree.Leaf(_)             => VDomModifier.empty
        }
      )
    }

    val invites:Rx[Array[Node]] = Rx {
      val graph = state.graph()
      val user = state.user()
      val userIdx = graph.idToIdxGet(user.id) // can fail when logging out
      userIdx match {
        case Some(userIdx) =>
          graph.inviteNodeIdx(userIdx).collect { case idx if !graph.pinnedNodeIdx.contains(userIdx)(idx) => graph.nodes(idx) } (breakOut)
        case None => Array.empty[Node]
      }
    }

    div(
      cls := "channels",
      Rx {
        val channelForest = state.channelForest()
        val page = state.page()
        val pageStyle = state.pageStyle()
        val user = state.user()

        VDomModifier(
          // channelLine(user.toNode, page.parentId, pageStyle),
          // channelForest.nonEmpty.ifTrue[VDomModifier](UI.horizontalDivider("workspaces")(cls := "inverted")),
          channelForest.map { channelTree =>
            channelList(channelTree, page.parentId, pageStyle)
          },
        )
      },
      Rx{
        val page = state.page()
        val pageStyle = state.pageStyle()
        VDomModifier(
          invites().nonEmpty.ifTrue[VDomModifier](UI.horizontalDivider("invitations")(cls := "inverted")),
          invites().map(node => channelLine(node, page.parentId, pageStyle).apply(
            div(
              cls := "ui icon buttons",
              height := "22px",
              marginRight := "4px",
              marginLeft := "auto",
              button(
                cls := "ui mini compact inverted green button",
                padding := "4px",
                freeSolid.faCheck,
                onClick.mapTo(GraphChanges(addEdges = Set(Edge.Pinned(state.user.now.id, node.id), Edge.Notify(node.id, state.user.now.id)), delEdges = Set(Edge.Invite(state.user.now.id, node.id)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              ),
              button(
                cls := "ui mini compact inverted button",
                padding := "4px",
                freeSolid.faTimes,
                onClick.mapTo(GraphChanges(delEdges = Set(Edge.Invite(state.user.now.id, node.id)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              )
            )
          ))
        )
      }
    )
  }

  def channelFocusButton(state: GlobalState, nodeId: NodeId): VNode = {
    div(
      cls := "ui icon buttons channel-focus-buttons",
      height := "22px",
      button(
        cls := "ui mini compact inverted button",
        padding := "2px",
        renderFontAwesomeIcon(Icons.conversation)(color.gray),
        onClick.stopPropagation.foreach(state.viewConfig.update(_.focusView(Page(nodeId), View.Chat))),
      ),
      button(
        cls := "ui mini compact inverted button",
        padding := "2px",
        renderFontAwesomeIcon(Icons.tasks)(color.gray),
        onClick.stopPropagation.foreach(state.viewConfig.update(_.focusView(Page(nodeId), View.Kanban))),
      )
    )
  }

  def channelIcons(state: GlobalState, size: Int)(implicit ctx: Ctx.Owner): VNode = {
    val indentFactor = 3
    val focusBorderWidth = 2
    val defaultPadding = CommonStyles.channelIconDefaultPadding
    val maxVisualizedDepth = 2
    div(
      cls := "channelIcons",
      Rx {
        val allChannels = state.channels()
        val user = state.user()
        val page = state.page()
        VDomModifier(
          (allChannels).map { case (node,rawDepth) =>
            val depth = rawDepth min maxVisualizedDepth
            val isSelected = page.parentId.contains(node.id)
            channelIcon(state, node, isSelected, size)(ctx)(
              UI.popupHtml("right center") := div(
                channelFocusButton(state, node.id)(marginRight := "4px"),
                node.str
              ),

              onChannelClick(ChannelAction.Node(node.id))(state),
              onClick foreach { Analytics.sendEvent("sidebar_closed", "clickchannel") },
              drag(target = DragItem.Channel(node.id)),
              cls := "node",
              // for each indent, steal padding on left and right
              // and reduce the width, so that the icon keeps its size
              width := s"${ size-(depth*indentFactor) }px",
              marginLeft := s"${depth*indentFactor}px",
              if(isSelected) VDomModifier(
                height := s"${ size-(2*focusBorderWidth) }px",
                marginTop := "2px",
                marginBottom := "2px",
                padding := s"${defaultPadding - focusBorderWidth}px ${defaultPadding - (depth*indentFactor/2.0)}px",
              ) else VDomModifier(
                padding := s"${defaultPadding}px ${defaultPadding - (depth*indentFactor/2.0)}px",
              ),
            )
          },
        )
      }
    )
  }

  def channelIcon(state: GlobalState, node: Node, isSelected: Boolean, size: Int)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    div(
      cls := "channelicon",
      keyed(node.id),
      width := s"${ size }px",
      height := s"${ size }px",
      backgroundColor := (node match {
        case node: Node.Content => ((if(isSelected) BaseColors.pageBgLight else BaseColors.pageBg).copy(h = NodeColor.hue(node.id)).toHex)
        case _: Node.User       => if(isSelected) "rgb(255, 255, 255)" else "rgba(255, 255, 255, 0.9)"
      }),
      Avatar(node),
    )
  }

  sealed trait ChannelAction extends Any
  object ChannelAction {
    case class Node(id: NodeId) extends AnyVal with ChannelAction
  }
  private def onChannelClick(action: ChannelAction)(state: GlobalState)(implicit ctx: Ctx.Owner) =
    onClick foreach { e =>
      val page = state.page.now
      //TODO if (e.shiftKey) {
      val newPage = action match {
        case ChannelAction.Node(id)   =>
//          if(e.ctrlKey) {
//            val filtered = page.parentIds.filterNot(_ == id)
//            val parentIds =
//              if(filtered.size == page.parentIds.size) page.parentIds :+ id
//              else if(filtered.nonEmpty) filtered
//              else Seq(id)
//            page.copy(parentIds = parentIds)
//          } else
                        Page(id)
      }
      state.viewConfig() = state.viewConfig.now.focus(newPage)
    }
}
