package wust.webApp.views

import fomanticui.{DimmerOptions, ModalOptions}
import fontAwesome._
import googleAnalytics.Analytics
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.html
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.ApiEvent.NewGraphChanges
import wust.css.{Styles, ZIndex}
import wust.graph.Node.User
import wust.graph._
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor.hue
import wust.util._
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.{Navigator, Notifications, ShareData}
import wust.webApp.outwatchHelpers._
import wust.webApp.search.Search
import wust.webApp.state._
import wust.webApp.views.Components.{renderNodeData, _}
import wust.webApp.{BrowserDetect, Client, Icons, Ownable}

import scala.collection.breakOut
import scala.concurrent.Future
import scala.scalajs.js
import scala.util.{Failure, Success}


object PageHeader {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div.staticRx(keyValue)(implicit ctx =>
      VDomModifier(
        cls := "pageheader",
        Rx {
          val graph = state.graph()
          val page = state.page()
          page.parentId.flatMap(graph.nodesByIdGet).map { parentNode => channelRow(state, parentNode) }
        },
      )
    )
  }

  private def channelRow(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    val channelTitle = NodePermission.canWrite(state, channel.id).map { canWrite =>
      val node =
        if(!canWrite) renderNodeData(channel.data)
        else {
          editableNodeOnClick(state, channel, state.eventProcessor.changes)(ctx)(
            onClick foreach { Analytics.sendEvent("pageheader", "editchanneltitle") }
          )
        }
      node(cls := "pageheader-channeltitle")
    }

    div(
      padding := "5px",
      paddingRight := "20px",
      backgroundColor := BaseColors.pageBg.copy(h = hue(channel.id)).toHex,

      Styles.flex,
      alignItems.center,

      channelAvatar(channel, size = 30)(Styles.flexStatic, marginRight := "5px"),
      channelTitle.map(_(flexShrink := 1, paddingLeft := "5px", paddingRight := "5px", marginRight := "5px")),
      Rx {
        val hasBigScreen = state.screenSize() != ScreenSize.Small
        hasBigScreen.ifTrue[VDomModifier](channelMembers(state, channel).apply(Styles.flexStatic, marginRight := "10px", lineHeight := "0")) // line-height:0 fixes vertical alignment
      },
      Rx {
        val level = state.graph().accessLevelOfNode(channel.id)
        val isPublic = level.fold(false)(_ == AccessLevel.ReadWrite)
        isPublic.ifTrue[VDomModifier](
          div(freeSolid.faGlobeAmericas, UI.tooltip("bottom center") := "Anyone can join via URL")
        )
      },
      menu(state, channel).apply(marginLeft.auto),
    )
  }

  private def menu(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    val isSpecialNode = Rx{
      //TODO we should use the permission system here
      channel.id == state.user().id
    }
    val isBookmarked = Rx {
      val g = state.graph()
      val channelIdx = g.idToIdx(channel.id)
      val userIdx = g.idToIdx(state.user().id)
      state.graph().pinnedNodeIdx(userIdx).contains(channelIdx)
    }

    val buttonStyle = VDomModifier(Styles.flexStatic, margin := "5px", fontSize := "20px", cursor.pointer)

    div(
      Styles.flex,
      alignItems.center,
      flexWrap.wrap,
      minWidth.auto, // when wrapping, prevents container to get smaller than the smallest element
      justifyContent.flexEnd, // horizontal centering when wrapped
      Rx {
        val hideBookmarkButton = isSpecialNode() || isBookmarked()
        hideBookmarkButton.ifFalse[VDomModifier](addToChannelsButton(state, channel).apply(
          Styles.flexStatic,
          marginTop := "3px",
          marginBottom := "3px",
        ))
      },
//      notifyControl(state, channel).apply(buttonStyle),
      viewSwitcher(state),
      Rx {
        settingsMenu(state, channel, isBookmarked(), isSpecialNode()).apply(buttonStyle)
      },
    )
  }

  private def channelMembers(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexWrap.wrap,
      registerDraggableContainer(state),
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
          cursor.grab,
          UI.tooltip("bottom center") := Components.displayUserName(user.data)
        )(
          draggableAs(DragItem.AvatarNode(user.id)),
          cls := "draghandle",
        ))(breakOut) : js.Array[VNode]
      }
    )
  }

  private def shareButton(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {

    // Workaround: Autsch!
    val urlHolderId = "shareurlholder"
    val urlHolder = textArea(id := urlHolderId, height := "0px", width := "0px", opacity := 0, border := "0px", padding := "0px", fontSize := "0px", zIndex := 100, position.absolute)

    div(
      cls := "item",
      i(
        cls := "icon fa-fw",
        freeSolid.faShareAlt,
        marginRight := "5px",
      ),
      span(cls := "text", "Share Link", cursor.pointer),
      urlHolder,
      onClick foreach {
        scribe.info(s"sharing post: $channel")

        // make channel public if it is not. we are sharing the link, so we want it to be public.
        channel match {
          case channel: Node.Content =>
            if (channel.meta.accessLevel != NodeAccess.ReadWrite) {
              val changes = GraphChanges.addNode(channel.copy(meta = channel.meta.copy(accessLevel = NodeAccess.ReadWrite)))
              state.eventProcessor.changes.onNext(changes)
              UI.toast(s"Node '${StringOps.trimToMaxLength(channel.str, 10)}' is now public")
            }
          case _ => ()
        }

        val shareTitle = channel.data.str
        val shareUrl = dom.window.location.href
        val shareDesc = s"Share channel: $shareTitle"

        if(Navigator.share.isDefined) {
          Navigator.share(new ShareData {
            title = shareTitle
            text = shareDesc
            url = shareUrl
          }).toFuture.onComplete {
            case Success(()) => ()
            case Failure(t)  => scribe.warn("Cannot share url via share-api", t)
          }
        } else {
          //TODO
          val elem = dom.document.querySelector(s"#$urlHolderId").asInstanceOf[dom.html.TextArea]
          elem.textContent = shareUrl
          elem.select()
          dom.document.execCommand("copy")
          UI.toast(title = shareDesc, msg = "Link copied to clipboard")
        }
      },
      onClick foreach { Analytics.sendEvent("pageheader", "share") }
    )
  }

  private def searchButton(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = {
    sealed trait SearchInput
    object SearchInput {
      case class Global(query: String) extends SearchInput
      case class Local(query: String) extends SearchInput

    }

    val searchLocal = PublishSubject[String]
    val searchGlobal = PublishSubject[String]
    val searchInputProcess = PublishSubject[String]
    val closeModal = PublishSubject[Unit]

    def renderSearchResult(needle: String, haystack: List[Node], globalSearchScope: Boolean) = {
      val searchRes = Search.byString(needle, haystack, Some(100), 0.2).map( nodeRes =>
        div(
          cls := "ui approve item",
          fontWeight.normal,
          cursor.pointer,
          paddingTop := "3px",
          Components.nodeCard(nodeRes._1, maxLength = Some(60)),
          onClick(Page(nodeRes._1.id)) --> state.page,
          onClick(()) --> closeModal
        ),
      )

      div(
        s"Found ${searchRes.length} result(s) in ${if(globalSearchScope) "all channels" else "the current channel"} ",
        padding := "5px 0",
        fontWeight.bold,
        div(
          searchRes,
        ),
        button(
          cls := "ui button",
          marginTop := "10px",
          display := (if(globalSearchScope) "none" else "block"),
          "Search in all channels",
          onClick(needle) --> searchGlobal
        )
      )
    }

    val searches = Observable(searchLocal.map(SearchInput.Local), searchGlobal.map(SearchInput.Global))
      .merge
      .distinctUntilChanged(cats.Eq.fromUniversalEquals)

    val searchResult: Observable[VDomModifier] = searches.map {
      case SearchInput.Local(query) if query.nonEmpty =>
        val graph = state.graph.now
        val nodes = graph.nodes.toList
        val descendants = graph.descendants(node.id)

        val channelDescendants = nodes.filter(n => descendants.toSeq.contains(n.id))
        renderSearchResult(query, channelDescendants, false)
      case SearchInput.Global(query) if query.nonEmpty =>
        Observable.fromFuture(Client.api.getGraph(Page.empty)).map { graph => //TODO? get whole graph? does that make sense?
          renderSearchResult(query, graph.nodes.toList, true)
        }
      case _ => VDomModifier.empty
    }

    def header(implicit ctx: Ctx.Owner) = VDomModifier(
      backgroundColor := BaseColors.pageBg.copy(h = hue(node.id)).toHex,
      div(
        Styles.flex,
        alignItems.center,
        channelAvatar(node, size = 20)(marginRight := "5px"),
        renderNodeData(node.data)(fontWeight.normal),
        paddingBottom := "5px",
      ),
      div(
        cls := "ui search",
        div(
          cls := "ui input action",
          input(
            cls := "prompt",
            placeholder := "Enter search text",
            Elements.valueWithEnter --> searchLocal,
            onChange.value --> searchInputProcess
          ),
          div(
            cls := "ui primary icon button approve",
            i(
              cls := "icon",
              freeSolid.faSearch,
            ),
            span(cls := "text", "Search", marginLeft := "5px", cursor.pointer),
            onClick(searchInputProcess) --> searchLocal
          ),
        ),
      )
    )

    def description(implicit ctx: Ctx.Owner) = VDomModifier(
      cls := "scrolling",
      backgroundColor := BaseColors.pageBgLight.copy(h = hue(node.id)).toHex,
      div(
        cls := "ui fluid search-result",
        searchResult,
      )
    )

    div(
      cls := "item",
      i(
        cls := "icon fa-fw",
        freeSolid.faSearch,
        marginRight := "5px",
        ),
      span(cls := "text", "Search", cursor.pointer),

      onClick(Ownable(implicit ctx => UI.ModalConfig(header = header, description = description, close = closeModal,
        modalModifier = cls := "form",
        contentModifier = backgroundColor := BaseColors.pageBgLight.copy(h = hue(node.id)).toHex,
      ))) --> state.modalConfig
    )
  }

  private def manageMembers(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = {

    val addMember = PublishSubject[String]
    val removeMember = PublishSubject[Edge.Member]
    val userNameInputProcess = PublishSubject[String]
    val modalCloseTrigger = PublishSubject[Unit]

    def handleAddMember(name: String)(implicit ctx: Ctx.Owner): Unit = {
      val graphUser = Client.api.getUserId(name)
      graphUser.flatMap {
        case Some(u) =>
          val change:GraphChanges = GraphChanges.from(addEdges = Set(Edge.Member(u, EdgeData.Member(AccessLevel.ReadWrite), node.id)))
          state.eventProcessor.localEvents.onNext(NewGraphChanges(state.user.now.toNode, change))
          Client.api.addMember(node.id, u, AccessLevel.ReadWrite)
        case _       => Future.successful(false)
      }.onComplete {
        case Success(b) =>
          if(!b) {
            //TODO: display error in modal
            UI.toast(title = "Add Member", msg = "Could not add member: Member does not exist", level = UI.ToastLevel.Error)
            scribe.error("Could not add member: Member does not exist")
          } else {
            UI.toast(title = "Add Member", msg = "Successfully added member to the channel", level = UI.ToastLevel.Success)
            scribe.info("Added member to channel")
          }
        case Failure(ex) =>
          UI.toast(title = "Add Member", msg = "Could not add member to channel", level = UI.ToastLevel.Error)
          scribe.error("Could not add member to channel", ex)
      }
    }

    def handleRemoveMember(membership: Edge.Member)(implicit ctx: Ctx.Owner): Unit = {
      if(membership.userId == state.user.now.id) {
        if(dom.window.confirm("Do you really want to remove yourself from this workspace?")) {
          state.viewConfig() = state.viewConfig.now.copy(pageChange = PageChange(Page.empty))
          modalCloseTrigger.onNext(())
        } else return
      }
      // optimistic UI, since removeMember does not send an event itself:
      val change:GraphChanges = GraphChanges.from(delEdges = Set(membership)) merge GraphChanges.disconnect(Edge.Pinned)(membership.userId, membership.nodeId)
      state.eventProcessor.localEvents.onNext(NewGraphChanges(state.user.now.toNode, change))
      
      Client.api.removeMember(membership.nodeId,membership.userId,membership.level)
    }

    def header(implicit ctx: Ctx.Owner) = VDomModifier(
      backgroundColor := BaseColors.pageBg.copy(h = hue(node.id)).toHex,
      div(
        Styles.flex,
        alignItems.center,
        channelAvatar(node, size = 20)(marginRight := "5px", Styles.flexStatic),
        renderNodeData(node.data)(cls := "channel-name", fontWeight.normal, marginRight := "15px"),
        paddingBottom := "5px",
      ),
      div(s"Manage Members"),
    )

    def userLine(user:Node.User)(implicit ctx: Ctx.Owner):VNode = {
      import concurrent.duration._
      div(
        marginTop := "10px",
        Styles.flex,
        alignItems.center,
        Avatar.user(user.id)(
          cls := "avatar",
          width := "22px",
          height := "22px",
          Styles.flexStatic,
          marginRight := "5px",
        ),
        div(
          user.name,
          fontSize := "15px",
          Styles.wordWrap,
        ),
      )
    }

    def description(implicit ctx: Ctx.Owner) = VDomModifier(
      div(
        div(
          cls := "ui fluid action input",
          input(
            placeholder := "Enter username",
            Elements.valueWithEnter --> addMember,
            onChange.value --> userNameInputProcess
          ),
          div(
            cls := "ui primary button approve",
            "Add",
            onClick(userNameInputProcess) --> addMember
          ),
        ),
      ),
      div(
        marginLeft := "10px",
        Rx {
          val graph = state.graph()
          val nodeIdx = graph.idToIdx(node.id)
          if(nodeIdx != -1) {
            graph.membershipEdgeForNodeIdx(nodeIdx).map { membershipIdx =>
              val membership = graph.edges(membershipIdx).asInstanceOf[Edge.Member]
              val user = graph.nodesById(membership.userId).asInstanceOf[User]
              userLine(user).apply(
                button(
                  cls := "ui tiny compact negative basic button",
                  marginLeft := "10px",
                  "Remove",
                  onClick(membership) --> removeMember
                )
              )
            }:VDomModifier
          } else VDomModifier.empty
        },
        if(true) VDomModifier.empty else List(div)
      )
    )

    div(
      emitter(addMember).foreach(handleAddMember(_)),
      emitter(removeMember).foreach(handleRemoveMember(_)),

      cls := "item",
      i(
        freeSolid.faUsers,
        cls := "icon fa-fw",
        marginRight := "5px",
        ),
      span(cls := "text", "Manage Members", cursor.pointer),

      onClick(Ownable(implicit ctx => UI.ModalConfig(header = header, description = description, close = modalCloseTrigger, modalModifier =
        cls := "mini form",
        contentModifier = backgroundColor := BaseColors.pageBgLight.copy(h = hue(node.id)).toHex,
      ))) --> state.modalConfig
    )
  }


  private def channelAvatar(node: Node, size: Int) = {
    Avatar(node)(
      width := s"${ size }px",
      height := s"${ size }px"
    )
  }

  private def iconWithIndicator(icon: IconLookup, indicator: IconLookup, color: String): VNode = fontawesome.layered(
    fontawesome.icon(icon),
    fontawesome.icon(
      indicator,
      new Params {
        transform = new Transform {size = 13.0; x = 7.0; y = -7.0; }
        styles = scalajs.js.Dictionary[String]("color" -> color)
      }
    )
  )

  private def decorateNotificationIcon(state: GlobalState, permissionState: PermissionState)(icon: IconLookup, description: String, changes: GraphChanges, changesOnSuccessPrompt: Boolean)(implicit ctx: Ctx.Owner): VDomModifier = {
    val default = "default".asInstanceOf[PermissionState]
    permissionState match {
      case PermissionState.granted => VDomModifier(
        (icon: VNode) (cls := "fa-fw"),
        title := description,
        onClick(changes) --> state.eventProcessor.changes
      )
      case PermissionState.prompt | `default`  => VDomModifier(
        iconWithIndicator(icon, freeRegular.faQuestionCircle, "black")(cls := "fa-fw"),
        title := "Notifications are currently disabled. Click to enable.",
        onClick foreach {
          Notifications.requestPermissionsAndSubscribe {
            if (changesOnSuccessPrompt) state.eventProcessor.changes.onNext(changes)
          }
        },
      )
      case PermissionState.denied  => VDomModifier(
        iconWithIndicator(icon, freeRegular.faTimesCircle, "tomato")(cls := "fa-fw"),
        title := s"$description (Notifications are blocked by your browser. Please reconfigure your browser settings for this site.)",
        onClick(changes) --> state.eventProcessor.changes
      )
    }
  }

  private def notifyControl(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VDomModifier = {
    Rx {
      val graph = state.graph()
      val user = state.user()
      val channelIdx = graph.idToIdx(channel.id)
      val userIdx = graph.idToIdx(user.id)
      val permissionState = state.permissionState()
      val hasNotifyEdge = graph.notifyByUserIdx(userIdx).contains(channelIdx)

      if(hasNotifyEdge) decorateNotificationIcon(state, permissionState)(
        freeSolid.faBell,
        description = "You are watching this node and will be notified about changes. Click to stop watching.",
        changes = GraphChanges.disconnect(Edge.Notify)(channel.id, user.id),
        changesOnSuccessPrompt = false
      ) else {
        val canNotifyParents = graph
          .ancestorsIdx(channelIdx)
          .exists(idx => graph.notifyByUserIdx(userIdx).contains(idx))

        if (canNotifyParents) decorateNotificationIcon(state, permissionState)(
          freeRegular.faBell,
          description = "You are not watching this node explicitly, but you watch a parent and will be notified about changes. Click to start watching this node explicitly.",
          changes = GraphChanges.connect(Edge.Notify)(channel.id, user.id),
          changesOnSuccessPrompt = true
        ) else decorateNotificationIcon(state, permissionState)(
          freeRegular.faBellSlash,
          description = "You are not watching this node and will not be notified. Click to start watching.",
          changes = GraphChanges.connect(Edge.Notify)(channel.id, user.id),
          changesOnSuccessPrompt = true
        )
      }
    }
  }

  private def addToChannelsButton(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode =
    button(
      cls := "ui compact primary button",
      if (BrowserDetect.isMobile) "Pin" else "Pin to sidebar",
      onClick(GraphChanges.connect(Edge.Pinned)(state.user.now.id, channel.id)) --> state.eventProcessor.changes,
      onClick foreach { Analytics.sendEvent("pageheader", "join") }
    )

  //TODO make this reactive by itself and never rerender, because the modal stuff is quite expensive.
  //TODO move menu to own file, makes up for a lot of code in this file
  //TODO: also we maybe can prevent rerendering menu buttons and modals while the menu is closed and do this lazily?
  private def settingsMenu(state: GlobalState, channel: Node, bookmarked: Boolean, isOwnUser: Boolean)(implicit ctx: Ctx.Owner): VNode = {
    val canWrite = NodePermission.canWrite(state, channel.id).now //TODO reactive? but settingsmenu is anyhow rerendered
    val permissionItem:VDomModifier = channel match {
        case channel: Node.Content if canWrite =>
          div(
            cls := "item",
            i(
              cls := "icon fa-fw",
              freeSolid.faUserLock,
              marginRight := "5px",
            ),
            span(cls := "text", "Set Permissions", cursor.pointer),
            div(
              cls := "menu",
              PermissionSelection.all.map { selection =>
                div(
                  cls := "item",
                  (channel.meta.accessLevel == selection.access).ifTrueOption(i(cls := "check icon")),
                  // value := selection.value,
                  Rx {
                    selection.name(channel.id, state.graph()) //TODO: report Scala.Rx bug, where two reactive variables in one function call give a compile error: selection.name(state.user().id, node.id, state.graph())
                  },
                  onClick(GraphChanges.addNode(channel.copy(meta = channel.meta.copy(accessLevel = selection.access)))) --> state.eventProcessor.changes,
                  onClick foreach {
                    Analytics.sendEvent("pageheader", "changepermission", selection.access.str)
                  }
                )
              }
            )
          )
        case _ => VDomModifier.empty
      }

    val notificationItem:VDomModifier = {
      (!isOwnUser).ifTrue[VDomModifier](div(
        cls := "item",
        notifyControl(state,channel),

        Rx {
          val graph = state.graph()
          val user = state.user()
          val channelIdx = graph.idToIdx(channel.id)
          val userIdx = graph.idToIdx(user.id)
          @inline def permissionGranted = state.permissionState() == PermissionState.granted
          @inline def hasNotifyEdge = graph.notifyByUserIdx(userIdx).contains(channelIdx)
          val text = if(permissionGranted && hasNotifyEdge) "Mute" else "Unmute"
          span(marginLeft := "7px", cls := "text", text, cursor.pointer)
        }
      ))
    }


    val nodeRoleItem:VDomModifier = channel match {
      case channel: Node.Content if canWrite =>
        def nodeRoleSubItem(nodeRole: NodeRole) = div(
          cls := "item",
          (channel.role == nodeRole).ifTrueOption(i(cls := "check icon")),
          nodeRole.toString,
          onClick(GraphChanges.addNode(channel.copy(role = nodeRole))) --> state.eventProcessor.changes,
          onClick foreach {
            Analytics.sendEvent("pageheader", "changerole", nodeRole.toString)
          }
        )

        div(
          cls := "item",
          i(
            cls := "icon fa-fw",
            freeSolid.faExchangeAlt,
            marginRight := "5px",
          ),
          span(cls := "text", "Convert", cursor.pointer),
          div(
            cls := "menu",
            nodeRoleSubItem(NodeRole.Message),
            nodeRoleSubItem(NodeRole.Task),
            nodeRoleSubItem(NodeRole.Stage)
          )
        )
      case _ => VDomModifier.empty
    }

    val mentionInItem:VDomModifier = {
      div(
        cls := "item",
        i(
          cls := "icon fa-fw",
          freeSolid.faCopy,
          marginRight := "5px",
        ),
        span(cls := "text", "Mention in", cursor.pointer),
        div(
          cls := "menu",
          padding := "10px",
          searchInGraph(state.graph, placeholder = "Enter Tag", filter = {
            case node: Node.Content => true
            case node: Node.User => node.id == state.user.now.id
          }).foreach { nodeId =>
            state.eventProcessor.changes.onNext(
              GraphChanges.addToParent(channel.id, nodeId)
            )
          }
        )
      )
    }

    val leaveItem:VDomModifier =
      (bookmarked && !isOwnUser).ifTrue[VDomModifier](div(
        cls := "item",
        i(
          cls := "icon fa-fw",
          freeSolid.faSignOutAlt,
          marginRight := "5px",
        ),
        span(cls := "text", "Unpin from sidebar", cursor.pointer),
        onClick(GraphChanges.disconnect(Edge.Pinned)(state.user.now.id, channel.id)) --> state.eventProcessor.changes
      ))

    val deleteItem =
      (canWrite).ifTrue[VDomModifier](div(
        cls := "item",
        i(
          cls := "icon fa-fw",
          Icons.delete,
          marginRight := "5px",
        ),
        span(cls := "text", "Delete", cursor.pointer),
        onClick foreach {
          state.eventProcessor.changes.onNext(
            GraphChanges.delete(channel.id, state.graph.now.parents(channel.id).toSet)
              .merge(GraphChanges.disconnect(Edge.Pinned)(state.user.now.id, channel.id))
          )
          state.viewConfig() = ViewConfig.default
          UI.toast(s"Deleted node '${StringOps.trimToMaxLength(channel.str, 10)}'", click = () => state.viewConfig() = state.viewConfig.now.focusNode(channel.id), level = UI.ToastLevel.Success)
        }
      ))


    val addMemberItem = canWrite.ifTrue[VDomModifier](manageMembers(state, channel))
    val shareItem = isOwnUser.ifFalse[VDomModifier](shareButton(state, channel))
    val searchItem = searchButton(state, channel)

    val items:List[VDomModifier] = List(notificationItem, searchItem, addMemberItem, mentionInItem, shareItem, permissionItem, nodeRoleItem, leaveItem, deleteItem)

    div(
      // https://semantic-ui.com/modules/dropdown.html#pointing
      cls := "ui icon top left labeled pointing dropdown",
      zIndex := ZIndex.overlay,
      freeSolid.faCog,
      div(
        cls := "menu",
        div(cls := "header", "Settings", cursor.default),
        items
      ),
        // revert default passive events, else dropdown is not working
      Elements.withoutDefaultPassiveEvents,
      // https://semantic-ui.com/modules/dropdown.html#/usage
      onDomMount.asJquery.foreach(_.dropdown("hide")),
    )
  }

  private def viewSwitcher(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    def viewId(view:View) = s"viewswitcher_${view.viewKey}"
    def MkLabel(currentView: View, pageStyle: PageStyle, targetView: View, icon: IconDefinition) = {
      label(`for` := viewId(targetView), icon, onClick(targetView) --> state.view, cursor.pointer,
        (currentView.viewKey == targetView.viewKey).ifTrue[VDomModifier](Seq(
          backgroundColor := pageStyle.sidebarBgColor,
          color := "white",
        )),
        UI.tooltip("bottom right") := targetView.toString
      )
    }

    def MkInput(currentView: View, pageStyle: PageStyle, targetView: View) = {
      input(display.none, id := viewId(targetView), `type` := "radio", name := "viewswitcher",
        (currentView.viewKey == targetView.viewKey).ifTrue[VDomModifier](Seq(checked := true, cls := "checked")),
        onInput foreach {
          Analytics.sendEvent("viewswitcher", "switch", currentView.viewKey)
        }
      )
    }

    div(
      cls := "viewbar",
      Styles.flex,
      flexDirection.row,
      justifyContent.flexEnd,
      alignItems.center,

      Rx {
        val currentView = state.view()
        val pageStyle = state.pageStyle()
        Seq(
          // MkInput(currentView, pageStyle, View.Magic),
          // MkLabel(currentView, pageStyle, View.Magic, freeSolid.faMagic),
          MkInput(currentView, pageStyle, View.Conversation),
          MkLabel(currentView, pageStyle, View.Conversation, Icons.conversation),
//          MkInput(currentView, pageStyle, View.Chat),
//          MkLabel(currentView, pageStyle, View.Chat, freeRegular.faComments),
//          MkInput(currentView, pageStyle, View.Thread),
//          MkLabel(currentView, pageStyle, View.Thread, freeSolid.faStream),
          MkInput(currentView, pageStyle, View.Tasks),
          MkLabel(currentView, pageStyle, View.Tasks, Icons.tasks),
//          MkInput(currentView, pageStyle, View.Kanban),
//          MkLabel(currentView, pageStyle, View.Kanban, freeSolid.faColumns),
//          MkInput(currentView, pageStyle, View.ListV),
//          MkLabel(currentView, pageStyle, View.ListV, freeSolid.faList),
//          MkInput(currentView, pageStyle, View.Graph),
//          MkLabel(currentView, pageStyle, View.Graph, freeBrands.faCloudsmith)
        )
      }
    )

  }

}

case class PermissionSelection(
  access: NodeAccess,
  value: String,
  name: (NodeId, Graph) => String,
  description: String,
  icon: IconLookup
)
object PermissionSelection {
  val all =
    PermissionSelection(
      access = NodeAccess.Inherited,
      name = { (nodeId, graph) =>
        val level = graph.accessLevelOfNode(nodeId)
        val isPublic = level.fold(false)(_ == AccessLevel.ReadWrite)
        val inheritedLevel = if(isPublic) "Public" else "Private"
        s"Inherited ($inheritedLevel)"
      },
      value = "Inherited",
      description = "The permissions for this page are the same as for its parents", // TODO: write name of parent page. Notion did permission UI very well.
      icon = freeSolid.faArrowUp
    ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.ReadWrite),
        name = (_, _) => "Public",
        value = "Public",
        description = "Anyone can access this page via the URL",
        icon = freeSolid.faUserPlus
      ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.Restricted),
        name = (_, _) => "Private",
        value = "Private",
        description = "Only you and explicit members can access this page",
        icon = freeSolid.faLock
      ) ::
      Nil
}
