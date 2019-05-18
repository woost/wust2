package wust.webApp.views

import colorado._
import wust.webApp.dragdrop._
import fontAwesome.freeSolid
import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import views.ChannelTreeData
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
            UI.tooltip("right center") := "New Project",
            onClick foreach { Analytics.sendEvent("sidebar_closed", "newchannel") }
          )
        ))
      )}
    )
  }

  val buttonStyles = "tiny compact inverted grey"


  private def expandToggleButton(state: GlobalState, nodeId :NodeId, userId: UserId, expanded: Rx[Boolean])(implicit ctx: Ctx.Owner) = {

    div(
      padding := "3px",
      cursor.pointer,
      Rx {
        (if(expanded()) Icons.collapse else Icons.expand) : VDomModifier
      },
      onClick.stopPropagation.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(!expanded.now), userId)) --> state.eventProcessor.changes
    )
  }

  private def channels(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    def channelLine(traverseState: TraverseState, userId: UserId, expanded: Rx[Boolean], hasChildren: Rx[Boolean])(implicit ctx: Ctx.Owner): VNode = {
      val nodeId = traverseState.parentId
      val selected = state.page.map(_.parentId contains nodeId)
      val node = Rx {
        state.rawGraph().nodesByIdOrThrow(nodeId)
      }

      div(
        Styles.flex,
        expandToggleButton(state, nodeId, userId, expanded).apply(
          Rx {
            VDomModifier.ifNot(hasChildren())(visibility.hidden)
          }
        ),
        div(
          flexGrow := 1,
          cls := "channel-line",
          Rx {
            VDomModifier.ifTrue(selected())(
              color := CommonStyles.sidebarBgColor,
              backgroundColor <-- state.pageStyle.map(_.sidebarBgHighlightColor)
            )
          },

          channelIcon(state, nodeId, selected, 30),

          Rx {
            renderAsOneLineText(node())(cls := "channel-name")
          },

          onChannelClick(state, nodeId),
          onClick foreach { Analytics.sendEvent("sidebar_open", "clickchannel") },
          cls := "node",
          drag(DragItem.Channel(nodeId))
        )
      )
    }

    def channelList(traverseState: TraverseState, userId: UserId, depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div.thunkStatic(keyValue(traverseState.parentId.hashCode))(Ownable { implicit ctx =>
        val children = Rx {
          val graph = state.rawGraph()
          ChannelTreeData.children(graph, traverseState, userId)
        }
        val hasChildren = children.map(_.nonEmpty)
        val expanded = Rx {
          state.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        VDomModifier(
          channelLine(traverseState, userId, expanded = expanded, hasChildren = hasChildren),
          Rx {
            VDomModifier.ifTrue(hasChildren() && expanded())(div(
              paddingLeft := "10px",
              fontSize := s"${ math.max(8, 14 - depth) }px",
              children().map { child => channelList(traverseState.step(child), userId, depth = depth + 1) }
            ))
          }
        )
      })
    }

    val toplevelChannels = Rx {
      val graph = state.rawGraph()
      val user = state.user()
      ChannelTreeData.toplevel(graph, user.id)
    }

    val invites:Rx[Seq[NodeId]] = Rx {
      val graph = state.rawGraph()
      val user = state.user()
      val userIdx = graph.idToIdx(user.id) // can fail when logging out
      userIdx match {
        case Some(userIdx) => graph.inviteNodeIdx(userIdx).collect { case idx if !graph.pinnedNodeIdx.contains(userIdx)(idx) => graph.nodeIds(idx) } (breakOut)
        case None => Seq.empty[NodeId]
      }
    }

    div(
      cls := "channels",
      Rx {
        val user = state.user()

        VDomModifier(
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), user.id))
        )
      },

      Rx {
        val user = state.user()

        VDomModifier.ifTrue(invites().nonEmpty)(
          UI.horizontalDivider("invitations")(cls := "inverted"),
          invites().map(nodeId => channelLine(TraverseState(nodeId), user.id, expanded = Var(false), hasChildren = Var(false)).apply(
            div(
              cls := "ui icon buttons",
              height := "22px",
              marginRight := "4px",
              marginLeft := "auto",
              button(
                cls := "ui mini compact inverted green button",
                padding := "4px",
                freeSolid.faCheck,
                onClick.mapTo(GraphChanges(addEdges = Array(Edge.Pinned(nodeId, user.id), Edge.Notify(nodeId, state.user.now.id)), delEdges = Array(Edge.Invite(nodeId, state.user.now.id)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              ),
              button(
                cls := "ui mini compact inverted button",
                padding := "4px",
                freeSolid.faTimes,
                onClick.mapTo(GraphChanges(delEdges = Array(Edge.Invite(nodeId, user.id)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              )
            )
          ))
        )
      },
    )
  }

  private def channelIcons(state: GlobalState, size: Int)(implicit ctx: Ctx.Owner): VNode = {
    val indentFactor = 3
    val focusBorderWidth = 2
    val defaultPadding = CommonStyles.channelIconDefaultPadding

    def channelLine(traverseState: TraverseState, userId: UserId, depth: Int, expanded: Rx[Boolean], hasChildren: Rx[Boolean])(implicit ctx: Ctx.Owner): VNode = {
      val nodeId = traverseState.parentId
      val selected = state.page.map(_.parentId contains nodeId)
      val nodeStr = Rx {
        state.rawGraph().nodesByIdOrThrow(nodeId).str
      }

      channelIcon(state, nodeId, selected, size)(ctx)(
        UI.popup("right center") <-- nodeStr,
        onChannelClick(state, nodeId),
        onClick foreach { Analytics.sendEvent("sidebar_closed", "clickchannel") },
        drag(target = DragItem.Channel(nodeId)),
        cls := "node",

        // for each indent, steal padding on left and right
        // and reduce the width, so that the icon keeps its size
        width := s"${ size-(depth*indentFactor) }px",

        Rx {
          if(selected()) VDomModifier(
            height := s"${ size-(2*focusBorderWidth) }px",
            marginTop := "2px",
            marginBottom := "2px",
            padding := s"${defaultPadding - focusBorderWidth}px ${defaultPadding - (depth*indentFactor/2.0)}px",
          ) else VDomModifier(
            padding := s"${defaultPadding}px ${defaultPadding - (depth*indentFactor/2.0)}px",
          )
        }
      )
    }

    def channelList(traverseState: TraverseState, userId: UserId, depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div.thunkStatic(keyValue(traverseState.parentId.hashCode))(Ownable { implicit ctx =>
        val children = Rx {
          val graph = state.rawGraph()
          ChannelTreeData.children(graph, traverseState, userId)
        }
        val hasChildren = children.map(_.nonEmpty)
        val expanded = Rx {
          state.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        VDomModifier(

          channelLine(traverseState, userId, depth, expanded = expanded, hasChildren = hasChildren),
          Rx {
            VDomModifier.ifTrue(hasChildren() && expanded())(div(
              paddingLeft := s"${indentFactor}px",
              fontSize := s"${ math.max(8, 14 - depth) }px",
              children().map { child => channelList(traverseState.step(child), userId, depth = depth + 1) }
            ))
          }
        )
      })
    }

    val toplevelChannels = Rx {
      val graph = state.rawGraph()
      val user = state.user()
      ChannelTreeData.toplevel(graph, user.id)
    }

    div(
      cls := "channelIcons",
      Rx {
        val user = state.user()

        VDomModifier(
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), user.id))
        )
      },
    )
  }

  private def channelIcon(state: GlobalState, nodeId: NodeId, isSelected: Rx[Boolean], size: Int)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "channelicon",
      width := s"${ size }px",
      height := s"${ size }px",
      backgroundColor <-- Rx {
        (if(isSelected()) BaseColors.pageBgLight else BaseColors.pageBg.copy(h = NodeColor.hue(nodeId))).toHex
      },
      Avatar.node(nodeId),
    )
  }

  private def onChannelClick(state: GlobalState, nodeId: NodeId) = VDomModifier(
    onClick foreach {
      state.urlConfig.update(_.focus(Page(nodeId)))
      ()
    }
  )
}
