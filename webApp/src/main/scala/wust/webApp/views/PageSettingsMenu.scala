package wust.webApp.views

import wust.facades.googleanalytics.Analytics
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, Ownable, UI}
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.webApp._
import wust.webApp.jsdom.{Navigator, ShareData}
import wust.webApp.state._
import wust.webApp.views.Components._

import scala.collection.breakOut
import scala.util.{Failure, Success}


object PageSettingsMenu {

  def apply(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Icons.menu,
      cursor.pointer,
      onClick.stopPropagation.foreach { toggleSidebar(state, channelId) }
    )
  }

  def toggleSidebar(state: GlobalState, channelId: NodeId): Unit = {
    //TODO better way to check whether sidebar is currently active for toggling.
    if(dom.document.querySelectorAll(".pusher.dimmed").length > 0) state.uiSidebarClose.onNext(())
    else state.uiSidebarConfig.onNext(Ownable(implicit ctx => sidebarConfig(state, channelId)))
    ()
  }

  def nodeIsBookmarked(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner) = Rx {
    val g = state.graph()
    val channelIdx = g.idToIdxOrThrow(channelId)
    val userIdx = g.idToIdxOrThrow(state.userId())
    state.graph().pinnedNodeIdx(userIdx).contains(channelIdx)
  }

  def sidebarConfig(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner) = {
    def sidebarItems: List[VDomModifier] = {
      val isBookmarked = nodeIsBookmarked(state, channelId)

      val channelAsNode: Rx[Option[Node]] = Rx {
        state.graph().nodesById(channelId)
      }
      val channelAsContent: Rx[Option[Node.Content]] = channelAsNode.map(_.collect { case n: Node.Content => n })
      val channelIsContent: Rx[Boolean] = channelAsContent.map(_.isDefined)
      val canWrite: Rx[Boolean] = NodePermission.canWrite(state, channelId)

      val permissionItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map(Permission.permissionItem(state, _)))
      }
      val nodeRoleItem:VDomModifier = Rx {
        channelAsContent().collect {
          case channel if canWrite() => ConvertSelection.menuItem(state, channel)
        }
      }

      val leaveItem:VDomModifier = Rx {
        (channelIsContent()).ifTrue[VDomModifier](a(
          cls := "item",
          cursor.pointer,
          if (isBookmarked()) VDomModifier(
            Components.icon(Icons.signOut),
            span("Unpin from sidebar"),
            onClick.stopPropagation.mapTo(GraphChanges.disconnect(Edge.Pinned)(channelId, state.user.now.id)) --> state.eventProcessor.changes
          ) else VDomModifier(
            Components.icon(Icons.pin),
            span("Pin to sidebar"),
            onClick.stopPropagation.mapTo(GraphChanges(addEdges = Array(Edge.Pinned(channelId, state.user.now.id), Edge.Notify(channelId, state.user.now.id)), delEdges = Array(Edge.Invite(channelId, state.user.now.id)))) --> state.eventProcessor.changes
          )
        ))
      }

      val deleteItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
          a(
            cursor.pointer,
            cls := "item",
            Components.icon(Icons.delete),
            span("Archive at all places"),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(
                GraphChanges.delete(ChildId(channelId), state.graph.now.parents(channelId).map(ParentId(_))(breakOut))
                  .merge(GraphChanges.disconnect(Edge.Pinned)(channelId, state.user.now.id))
              )
              UI.toast(s"Archived '${ StringOps.trimToMaxLength(channel.str, 10) } at all places'", level = UI.ToastLevel.Success)
            }
          )
        })
      }

      val importItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
          Importing.settingsItem(state, channel.id)
        })
      }

      val addMemberItem: VDomModifier = Rx {
        channelAsContent() collect {
          case channel if canWrite() => manageMembers(state, channel)
        }
      }
      val shareItem = Rx {
        channelAsContent().map(shareButton(state, _))
      }
      val searchItem = Rx {
        channelAsNode().map(searchModalButton(state, _))
      }
      val notificationItem = Rx {
        channelAsContent().map(WoostNotification.generateNotificationItem(state, state.permissionState(), state.graph(), state.user().toNode, _))
      }

      List[VDomModifier](notificationItem, searchItem, addMemberItem, shareItem, importItem, permissionItem, nodeRoleItem, leaveItem, deleteItem)
    }

    GenericSidebar.SidebarConfig(
      sidebarItems,
      sidebarModifier = VDomModifier(
        borderWidth := "0px 0px 0px 5px",
        width := "165px",
        borderColor := BaseColors.pageBg.copy(h = NodeColor.hue(channelId)).toHex,
      )
    )
  }

  private def shareButton(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    import scala.concurrent.duration._

    val shareTitle = StringOps.trimToMaxLength(channel.data.str, 15)
    val shareUrl = dom.window.location.href
    val shareDesc = s"Share: $shareTitle"

    def assurePublic(): Unit = {
      // make channel public if it is not. we are sharing the link, so we want it to be public.
      channel match {
        case channel: Node.Content =>
          if (channel.meta.accessLevel != NodeAccess.ReadWrite) {
            val changes = GraphChanges.addNode(channel.copy(meta = channel.meta.copy(accessLevel = NodeAccess.ReadWrite)))
            state.eventProcessor.changes.onNext(changes)
            UI.toast(s"${StringOps.trimToMaxLength(channel.str, 10)} is now public")
          }
        case _ => ()
      }
    }

    a(
      cursor.pointer,
      cls := "item",
      Components.icon(Icons.share),
      dsl.span("Share Link"),
      onClick.transform(_.delayOnNext(200 millis)).foreach { // delay, otherwise the assurePublic changes update interferes with clipboard js
        assurePublic()
        Analytics.sendEvent("pageheader", "share")
      },

      if (Navigator.share.isDefined) VDomModifier(
        onClick.stopPropagation foreach {
          scribe.info(s"Sharing '$channel'")

          Navigator.share(new ShareData {
            title = shareTitle
            text = shareDesc
            url = shareUrl
          }).toFuture.onComplete {
            case Success(()) => ()
            case Failure(t)  => scribe.warn("Cannot share url via share-api", t)
          }
        },
      ) else VDomModifier(
        Elements.copiableToClipboard,
        dataAttr("clipboard-text") := shareUrl,
        onClick.stopPropagation foreach {
          scribe.info(s"Copying share-link for '$channel'")

          UI.toast(title = shareDesc, msg = "Link copied to clipboard")
        },
      ),
    )
  }

  private def searchModalButton(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = {
    SharedViewElements.searchButtonWithIcon(
      onClick.stopPropagation(Ownable(implicit ctx => SearchModal.config(state, node))) --> state.uiModalConfig
    )
  }

  private def manageMembers(state: GlobalState, node: Node.Content)(implicit ctx: Ctx.Owner): VNode = {
    a(
      cls := "item",
      cursor.pointer,
      Components.icon(Icons.users),
      span("Members"),

      onClick.stopPropagation(Ownable(implicit ctx => MembersModal.config(state, node))) --> state.uiModalConfig
    )
  }

  def addToChannelsButton(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    button(
      cls := "ui compact inverted button",
      if (BrowserDetect.isMobile) "Pin" else "Pin to sidebar",
      onClick.mapTo(GraphChanges(addEdges = Array(Edge.Pinned(channelId, state.user.now.id), Edge.Notify(channelId, state.user.now.id)), delEdges = Array(Edge.Invite(channelId, state.user.now.id)))) --> state.eventProcessor.changes,
      onClick foreach { Analytics.sendEvent("pageheader", "join") }
    )
  }

}
