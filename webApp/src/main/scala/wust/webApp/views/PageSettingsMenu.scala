package wust.webApp.views

import wust.facades.googleanalytics.Analytics
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, Ownable, UI}
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.webApp._
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

  private def sidebarMenuItems(channelId: NodeId)(implicit ctx: Ctx.Owner) = {
    val isBookmarked = nodeIsBookmarked( channelId)

    val channelAsNode: Rx[Option[Node]] = Rx {
      GlobalState.graph().nodesById(channelId)
    }
    val channelAsContent: Rx[Option[Node.Content]] = channelAsNode.map(_.collect { case n: Node.Content => n })
    val channelIsContent: Rx[Boolean] = channelAsContent.map(_.isDefined)
    val canWrite: Rx[Boolean] = NodePermission.canWrite( channelId)

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
          Elements.icon(Icons.unbookmark),
          span("Remove Bookmark"),
          onClick.stopPropagation.useLazy(GraphChanges.disconnect(Edge.Pinned)(channelId, GlobalState.userId.now)) --> GlobalState.eventProcessor.changes
        ) else VDomModifier(
          Elements.icon(Icons.bookmark),
          span("Bookmark"),
          onClick.stopPropagation.useLazy(GraphChanges(addEdges = Array(Edge.Pinned(channelId, GlobalState.userId.now), Edge.Notify(channelId, GlobalState.userId.now)), delEdges = Array(Edge.Invite(channelId, GlobalState.userId.now)))) --> GlobalState.eventProcessor.changes
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
                .merge(GraphChanges.disconnect(Edge.Pinned)(channelId, GlobalState.userId.now))
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
          UI.toast("Successfully duplicated node, click here to focus", StringOps.trimToMaxLength(channel.str, 50), level = UI.ToastLevel.Success, click = () => GlobalState.focus(node.id, needsGet = false))
        }: ((Node.Content, GraphChanges)) => Unit)
      })
    }

    //TODO: Not safe...
    // val resyncWithTemplatesItem = Rx {
    //   VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
    //     val hasTemplates = GlobalState.rawGraph().idToIdxFold(channel.id)(false)(channelIdx => GlobalState.rawGraph().derivedFromTemplateEdgeIdx.sliceNonEmpty(channelIdx))
    //     VDomModifier.ifTrue(hasTemplates)(
    //       GraphChangesAutomationUI.resyncWithTemplatesItem( channel.id).foreach { changes =>
    //         UI.toast("Successfully synced with templates", StringOps.trimToMaxLength(channel.str, 50), level = UI.ToastLevel.Success)
    //         GlobalState.submitChanges(changes)
    //       }
    //     )
    //   })
    // }

    val importItem = Rx {
      VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
        Importing.settingsItem( channel.id)
      })
    }

    val searchItem = Rx {
      channelAsNode().map(searchModalButton( _))
    }
    val notificationItem = Rx {
      channelAsContent().map(WoostNotification.generateNotificationItem( GlobalState.permissionState(), GlobalState.graph(), GlobalState.user().toNode, _))
    }

    List[VDomModifier](notificationItem, searchItem, importItem, nodeRoleItem, copyItem, /*resyncWithTemplatesItem, */ leaveItem, deleteItem)

  }

  def sidebarConfig(channelId: NodeId)(implicit ctx: Ctx.Owner) = {
    def sidebarItems: List[VDomModifier] = sidebarMenuItems(channelId)

    GenericSidebar.SidebarConfig(
      sidebarItems,
      sidebarModifier = VDomModifier(
        borderWidth := "0px 0px 0px 5px",
        width := "165px",
        borderColor := BaseColors.pageBg.copy(h = NodeColor.hue(channelId)).toHex,
      )
    )
  }

  private def searchModalButton(node: Node)(implicit ctx: Ctx.Owner): VNode = {
    SharedViewElements.searchButtonWithIcon(
      onClick.stopPropagation.use(Ownable(implicit ctx => SearchModal.config(node))) --> GlobalState.uiModalConfig
    )
  }

  def addToChannelsButton(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    button(
      cls := "ui compact inverted button",
      UI.tooltip("left center") := "Bookmark in left Sidebar",
      Icons.bookmark,
      onClick.useLazy(GraphChanges(addEdges = Array(Edge.Pinned(channelId, GlobalState.userId.now), Edge.Notify(channelId, GlobalState.userId.now)), delEdges = Array(Edge.Invite(channelId, GlobalState.userId.now)))) --> GlobalState.eventProcessor.changes,
      onClick foreach {
        GlobalState.graph.now.nodesById(channelId).foreach { node =>
          node.role match {
            case NodeRole.Project => FeatureState.use(Feature.BookmarkProject)
            case NodeRole.Task => FeatureState.use(Feature.BookmarkTask)
            case NodeRole.Message => FeatureState.use(Feature.BookmarkMessage)
            case NodeRole.Note => FeatureState.use(Feature.BookmarkNote)
            case _ =>
          }
        }
      }
    )
  }
}
