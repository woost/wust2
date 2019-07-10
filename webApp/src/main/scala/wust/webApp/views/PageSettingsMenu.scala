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

  def apply(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Icons.menu,
      cursor.pointer,
      onClick.stopPropagation.foreach { toggleSidebar( channelId) }
    )
  }

  def toggleSidebar(channelId: NodeId): Unit = {
    //TODO better way to check whether sidebar is currently active for toggling.
    if(dom.document.querySelectorAll(".pusher.dimmed").length > 0) GlobalState.uiSidebarClose.onNext(())
    else GlobalState.uiSidebarConfig.onNext(Ownable(implicit ctx => sidebarConfig( channelId)))
    ()
  }

  def nodeIsBookmarked(channelId: NodeId)(implicit ctx: Ctx.Owner) = Rx {
    val g = GlobalState.graph()
    val channelIdx = g.idToIdxOrThrow(channelId)
    val userIdx = g.idToIdxOrThrow(GlobalState.userId())
    GlobalState.graph().pinnedNodeIdx(userIdx).contains(channelIdx)
  }

  def sidebarConfig(channelId: NodeId)(implicit ctx: Ctx.Owner) = {
    def sidebarItems: List[VDomModifier] = {
      val isBookmarked = nodeIsBookmarked( channelId)

      val channelAsNode: Rx[Option[Node]] = Rx {
        GlobalState.graph().nodesById(channelId)
      }
      val channelAsContent: Rx[Option[Node.Content]] = channelAsNode.map(_.collect { case n: Node.Content => n })
      val channelIsContent: Rx[Boolean] = channelAsContent.map(_.isDefined)
      val canWrite: Rx[Boolean] = NodePermission.canWrite( channelId)

      val permissionItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map(Permission.permissionItem( _)))
      }
      val nodeRoleItem:VDomModifier = Rx {
        channelAsContent().collect {
          case channel if canWrite() => ConvertSelection.menuItem( channel)
        }
      }

      val leaveItem:VDomModifier = Rx {
        (channelIsContent()).ifTrue[VDomModifier](a(
          cls := "item",
          cursor.pointer,
          if (isBookmarked()) VDomModifier(
            Elements.icon(Icons.signOut),
            span("Unpin from sidebar"),
            onClick.stopPropagation.mapTo(GraphChanges.disconnect(Edge.Pinned)(channelId, GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes
          ) else VDomModifier(
            Elements.icon(Icons.pin),
            span("Pin to sidebar"),
            onClick.stopPropagation.mapTo(GraphChanges(addEdges = Array(Edge.Pinned(channelId, GlobalState.user.now.id), Edge.Notify(channelId, GlobalState.user.now.id)), delEdges = Array(Edge.Invite(channelId, GlobalState.user.now.id)))) --> GlobalState.eventProcessor.changes
          )
        ))
      }

      val deleteItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
          a(
            cursor.pointer,
            cls := "item",
            Elements.icon(Icons.delete),
            span("Archive at all places"),
            onClick.stopPropagation foreach {
              GlobalState.submitChanges(
                GraphChanges.delete(ChildId(channelId), GlobalState.graph.now.parents(channelId).map(ParentId(_))(breakOut))
                  .merge(GraphChanges.disconnect(Edge.Pinned)(channelId, GlobalState.user.now.id))
              )
              UI.toast(s"Archived '${ StringOps.trimToMaxLength(channel.str, 10) }' at all places", level = UI.ToastLevel.Success)
            }
          )
        })
      }

      val copyItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
          GraphChangesAutomationUI.copyNodeItem( channel.id).foreach({ case (node, changes) =>
            GlobalState.submitChanges(changes)
            UI.toast("Successfully copied node, click to focus", StringOps.trimToMaxLength(channel.str, 50), level = UI.ToastLevel.Success, click = () => GlobalState.urlConfig.update(_.focus(Page(node.id), needsGet = false)))
          }: ((Node.Content, GraphChanges)) => Unit)
        })
      }

      val resyncWithTemplatesItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
          val hasTemplates = GlobalState.rawGraph().idToIdxFold(channel.id)(false)(channelIdx => GlobalState.rawGraph().derivedFromTemplateEdgeIdx.sliceNonEmpty(channelIdx))
          VDomModifier.ifTrue(hasTemplates)(
            GraphChangesAutomationUI.resyncWithTemplatesItem( channel.id).foreach { changes =>
              UI.toast("Successfully synced with templates", StringOps.trimToMaxLength(channel.str, 50), level = UI.ToastLevel.Success)
              GlobalState.submitChanges(changes)
            }
          )
        })
      }

      val importItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
          Importing.settingsItem( channel.id)
        })
      }

      val addMemberItem: VDomModifier = Rx {
        channelAsContent() collect {
          case channel if canWrite() => manageMembers( channel)
        }
      }
      val shareItem = Rx {
        channelAsContent().map(shareButton( _))
      }
      val searchItem = Rx {
        channelAsNode().map(searchModalButton( _))
      }
      val notificationItem = Rx {
        channelAsContent().map(WoostNotification.generateNotificationItem( GlobalState.permissionState(), GlobalState.graph(), GlobalState.user().toNode, _))
      }

      List[VDomModifier](notificationItem, searchItem, addMemberItem, shareItem, importItem, permissionItem, nodeRoleItem, copyItem, resyncWithTemplatesItem, leaveItem, deleteItem)
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

  private def shareButton(channel: Node)(implicit ctx: Ctx.Owner): VNode = {
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
            GlobalState.submitChanges(changes)
            UI.toast(s"${StringOps.trimToMaxLength(channel.str, 10)} is now public")
          }
        case _ => ()
      }
    }

    a(
      cursor.pointer,
      cls := "item",
      Elements.icon(Icons.share),
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

  private def searchModalButton(node: Node)(implicit ctx: Ctx.Owner): VNode = {
    SharedViewElements.searchButtonWithIcon(
      onClick.stopPropagation(Ownable(implicit ctx => SearchModal.config( node))) --> GlobalState.uiModalConfig
    )
  }

  private def manageMembers(node: Node.Content)(implicit ctx: Ctx.Owner): VNode = {
    a(
      cls := "item",
      cursor.pointer,
      Elements.icon(Icons.users),
      span("Members"),

      onClick.stopPropagation(Ownable(implicit ctx => MembersModal.config( node))) --> GlobalState.uiModalConfig
    )
  }

  def addToChannelsButton(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    button(
      cls := "ui compact inverted button",
      if (BrowserDetect.isMobile) "Pin" else "Pin to sidebar",
      onClick.mapTo(GraphChanges(addEdges = Array(Edge.Pinned(channelId, GlobalState.user.now.id), Edge.Notify(channelId, GlobalState.user.now.id)), delEdges = Array(Edge.Invite(channelId, GlobalState.user.now.id)))) --> GlobalState.eventProcessor.changes,
      onClick foreach { Analytics.sendEvent("pageheader", "join") }
    )
  }

}
