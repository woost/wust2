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

object LeftSidebar {
  val minWidthSidebar = 40

  def apply(state: GlobalState): VNode = {

    def authStatus(implicit ctx: Ctx.Owner) = SharedViewElements.authStatus(state).map(_(alignSelf.center, marginTop := "30px", marginBottom := "10px"))

    GenericSidebar.left(
      state.leftSidebarOpen,
      config = Ownable { implicit ctx => GenericSidebar.Config(
        mainModifier = VDomModifier(
          registerDragContainer(state, DragContainer.Sidebar),
          drag(target = DragItem.Sidebar),
        ),
        openModifier = VDomModifier(
          Rx{ VDomModifier.ifNot(state.topbarIsVisible())(Topbar(state).apply(Styles.flexStatic)) },
          channels(state),
          newProjectButton(state).apply(
            cls := "newChannelButton-large " + buttonStyles,
            onClick foreach { Analytics.sendEvent("sidebar_open", "newchannel") }
          ),
        ),
        overlayOpenModifier = VDomModifier(
          Rx {
            VDomModifier.ifTrue(state.screenSize() == ScreenSize.Small)(authStatus)
          },
          onClick(false) --> state.leftSidebarOpen
        ),
        expandedOpenModifier = VDomModifier(
          Rx {
            VDomModifier.ifNot(state.topbarIsVisible())(authStatus)
          },
        ),
        closedModifier = Some(VDomModifier(
          minWidth := s"${ minWidthSidebar }px", // this is needed when the hamburger is not rendered inside the sidebar
          Rx{ VDomModifier.ifNot(state.topbarIsVisible())(Topbar.hamburger(state)) },
          channelIcons(state, minWidthSidebar),
          newProjectButton(state, "+").apply(
            cls := "newChannelButton-small " + buttonStyles,
            UI.popup("right center") := "New Project",
            onClick foreach { Analytics.sendEvent("sidebar_closed", "newchannel") }
          )
        ))
      )}
    )
  }

  val buttonStyles = "tiny compact inverted grey"


  def expandToggleButton(state: GlobalState, node:Node, expanded: Boolean) = {
    div(
      padding := "3px",
      cursor.pointer,
      if(expanded)
        VDomModifier(
          Icons.collapse,
          onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(node.id, EdgeData.Expanded(false), state.user.now.id)) --> state.eventProcessor.changes
        )
      else
        VDomModifier(
          Icons.expand,
          onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(node.id, EdgeData.Expanded(true), state.user.now.id)) --> state.eventProcessor.changes
        )
    )
  }

  def channels(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    def channelLine(node: Node, pageParentId: Option[NodeId], pageStyle: PageStyle, expanded: Boolean, hasChildren: Boolean): VNode = {
      val selected = pageParentId contains node.id
      div(
        Styles.flex,
        expandToggleButton(state, node, expanded).apply(VDomModifier.ifNot(hasChildren)(visibility.hidden)),
        div(
          flexGrow := 1,
          cls := "channel-line",
          selected.ifTrueSeq(
            Seq(
              color := CommonStyles.sidebarBgColor,
              backgroundColor := pageStyle.sidebarBgHighlightColor
            )
          ),

          channelIcon(state, node, selected, 30),

          {
            val rendered = renderAsOneLineText(node)(cls := "channel-name")
            if (state.user.now.id == node.id) b(rendered) else rendered
          },

          onChannelClick(ChannelAction.Node(node.id))(state),
          onClick foreach { Analytics.sendEvent("sidebar_open", "clickchannel") },
          cls := "node",
          node match {
            case _:Node.Content => drag(DragItem.Channel(node.id))
            case _:Node.User => drag(target = DragItem.Channel(node.id))
          },
        )
      )
    }

    def channelList(graph: Graph, channels: Tree, pageParentId: Option[NodeId], pageStyle: PageStyle, depth: Int = 0): VNode = {
      val expanded:Boolean = graph.isExpanded(state.user.now.id, channels.node.id).getOrElse(true)
      div(
        channelLine(channels.node, pageParentId, pageStyle, expanded = expanded, hasChildren = channels.hasChildren),
        channels match {
          case Tree.Parent(_, children) if expanded => div(
            paddingLeft := "10px",
            fontSize := s"${ math.max(8, 14 - depth) }px",
            children.map { child => channelList(graph, child, pageParentId, pageStyle, depth = depth + 1) }(breakOut): Seq[VDomModifier]
          )
          case _             => VDomModifier.empty
        }
      )
    }

    val invites:Rx[Seq[Node]] = Rx {
      val graph = state.graph()
      val user = state.user()
      val userIdx = graph.idToIdxGet(user.id) // can fail when logging out
      userIdx match {
        case Some(userIdx) =>
          graph.inviteNodeIdx(userIdx).collect { case idx if !graph.pinnedNodeIdx.contains(userIdx)(idx) => graph.nodes(idx) } (breakOut)
        case None => Seq.empty[Node]
      }
    }

    div(
      cls := "channels",
      Rx {
        val channelForest = state.channelForest()
        val graph = state.graph()
        val page = state.page()
        val pageStyle = state.pageStyle()

        VDomModifier(
          channelForest.map { channelTree =>
            channelList(graph, channelTree, page.parentId, pageStyle)
          },
        )
      },
      Rx{
        val page = state.page()
        val pageStyle = state.pageStyle()
        val graph = state.graph()
        VDomModifier(
          invites().nonEmpty.ifTrue[VDomModifier](UI.horizontalDivider("invitations")(cls := "inverted")),
          invites().map(node => channelLine(node, page.parentId, pageStyle, expanded = graph.isExpanded(state.user.now.id, node.id).getOrElse(true), hasChildren = false).apply(
            div(
              cls := "ui icon buttons",
              height := "22px",
              marginRight := "4px",
              marginLeft := "auto",
              button(
                cls := "ui mini compact inverted green button",
                padding := "4px",
                freeSolid.faCheck,
                onClick.mapTo(GraphChanges(addEdges = Set(Edge.Pinned(node.id, state.user.now.id), Edge.Notify(node.id, state.user.now.id)), delEdges = Set(Edge.Invite(node.id, state.user.now.id)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              ),
              button(
                cls := "ui mini compact inverted button",
                padding := "4px",
                freeSolid.faTimes,
                onClick.mapTo(GraphChanges(delEdges = Set(Edge.Invite(node.id, state.user.now.id)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              )
            )
          ))
        )
      }
    )
  }

  def channelIcons(state: GlobalState, size: Int)(implicit ctx: Ctx.Owner): VNode = {
    val indentFactor = 3
    val focusBorderWidth = 2
    val defaultPadding = CommonStyles.channelIconDefaultPadding
    val maxVisualizedDepth = 2


    def channelIconList(graph: Graph, channels: Tree, pageParentId: Option[NodeId], depth: Int = 0): VDomModifier = {
      val expanded:Boolean = graph.isExpanded(state.user.now.id, channels.node.id).getOrElse(true)
      VDomModifier(
        channelIconLine(channels.node, pageParentId, depth, expanded = expanded, hasChildren = channels.hasChildren),
        channels match {
          case Tree.Parent(_, children) if expanded => 
            children.map { child => channelIconList(graph, child, pageParentId, depth = depth + 1) }(breakOut): Seq[VDomModifier]
          case _             => VDomModifier.empty
        }
      )
    }

    def channelIconLine(node: Node, pageParentId: Option[NodeId], depth: Int, expanded: Boolean, hasChildren: Boolean): VNode = {
      val isSelected = pageParentId.contains(node.id)
      channelIcon(state, node, isSelected, size)(ctx)(
        UI.popup("right center") := node.str,

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
    }

    div(
      cls := "channelIcons",
      Rx {
        val channelForest = state.channelForest()
        val graph = state.graph()
        val page = state.page()
        val pageStyle = state.pageStyle()

        VDomModifier(
          channelForest.map { channelTree =>
            channelIconList(graph, channelTree, page.parentId)
          },
        )
      },
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
  private def onChannelClick(action: ChannelAction)(state: GlobalState)(implicit ctx: Ctx.Owner) = VDomModifier(
    // action match {
    //   case ChannelAction.Node(id) => sidebarNodeFocusVisualizeMod(state.rightSidebarNode, id)
    //   case _                      => VDomModifier.empty
    // },
    onClick foreach { e =>
      val page = state.page.now
      action match {
        case ChannelAction.Node(id)   =>
         // if(e.shiftKey) {
         //   state.rightSidebarNode() = if (state.rightSidebarNode.now.contains(id)) None else Some(id)
         // } else {
           state.urlConfig.update(_.focus(Page(id)))
         // }
      }
    }
  )
}
