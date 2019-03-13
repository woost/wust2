package wust.webApp.views

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

import scala.collection.breakOut
import scala.scalajs.js
import scala.util.{Failure, Success}


object PageSettingsMenu {

  def nodeIsBookmarked(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner) = Rx {
    val g = state.graph()
    val channelIdx = g.idToIdx(channelId)
    val userIdx = g.idToIdx(state.user().id)
    state.graph().pinnedNodeIdx(userIdx).contains(channelIdx)
  }

  def sidebarConfig(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner) = {
    def sidebarItems: List[VDomModifier] = {
      val isBookmarked = nodeIsBookmarked(state, channelId)

      val channelAsNode: Rx[Option[Node]] = Rx {
        state.graph().nodesByIdGet(channelId)
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
        (channelIsContent()).ifTrue[VDomModifier](div(
          cls := "item",
          cursor.pointer,
          if (isBookmarked()) VDomModifier(
            Elements.icon(Icons.signOut),
            span("Unpin from sidebar"),
            onClick.stopPropagation.mapTo(GraphChanges.disconnect(Edge.Pinned)(channelId, state.user.now.id)) --> state.eventProcessor.changes
          ) else VDomModifier(
            Elements.icon(Icons.pin),
            span("Pin to sidebar"),
            onClick.stopPropagation.mapTo(GraphChanges(addEdges = Set(Edge.Pinned(channelId, state.user.now.id), Edge.Notify(channelId, state.user.now.id)), delEdges = Set(Edge.Invite(channelId, state.user.now.id)))) --> state.eventProcessor.changes
          )
        ))
      }

      val deleteItem = Rx {
        VDomModifier.ifTrue(canWrite())(channelAsContent().map { channel =>
          div(
            cursor.pointer,
            cls := "item",
            Elements.icon(Icons.delete),
            span("Archive at all places"),
            onClick.stopPropagation foreach {
              state.eventProcessor.changes.onNext(
                GraphChanges.delete(ChildId(channelId), state.graph.now.parents(channelId).map(ParentId(_)).toSet)
                  .merge(GraphChanges.disconnect(Edge.Pinned)(channelId, state.user.now.id))
              )
              UI.toast(s"Archived '${ StringOps.trimToMaxLength(channel.str, 10) } at all places'", level = UI.ToastLevel.Success)
            }
          )
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
        channelAsNode().map(searchButton(state, _))
      }
      val notificationItem = Rx {
        channelAsContent().map(WoostNotification.generateNotificationItem(state, state.permissionState(), state.graph(), state.user().toNode, _))
      }

      List[VDomModifier](notificationItem, searchItem, addMemberItem, shareItem, permissionItem, nodeRoleItem, leaveItem, deleteItem)
    }

    def header: VDomModifier = div(
      padding := "5px 5px 5px 15px",
      color.gray,
      Styles.flex,
      justifyContent.spaceBetween,
      b(cursor.default, "Settings"),
      i(cursor.pointer, cls := "close icon", onClick(()) --> state.uiSidebarClose)
    )

    UI.SidebarConfig(header :: sidebarItems)
  }

  def toggleSidebar(state: GlobalState, channelId: NodeId): Unit = {
    //TODO better way to check whether sidebar is currently active for toggling.
    if(dom.document.querySelectorAll(".pusher.dimmed").length > 0) state.uiSidebarClose.onNext(())
    else state.uiSidebarConfig.onNext(Ownable(implicit ctx => sidebarConfig(state, channelId)))
    ()
  }

  def apply(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Icons.menu,
      cursor.pointer,
      onClick.foreach { toggleSidebar(state, channelId) }
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

    div(
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

  private def searchButton(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = {
    sealed trait SearchInput
    object SearchInput {
      case class Global(query: String) extends SearchInput
      case class Local(query: String) extends SearchInput

    }

    val searchLocal = PublishSubject[String]
    val searchGlobal = PublishSubject[String]
    val searchInputProcess = PublishSubject[String]

    def renderSearchResult(needle: String, haystack: List[Node], globalSearchScope: Boolean) = {
      val searchRes = Search.byString(needle, haystack, Some(100), 0.75).map( nodeRes =>
        div(
          cls := "ui approve item",
          fontWeight.normal,
          cursor.pointer,
          padding := "3px",
          Components.nodeCard(nodeRes._1),
          onClick.stopPropagation.mapTo(state.urlConfig.now.focus(Page(nodeRes._1.id))) --> state.urlConfig,
          onClick.stopPropagation(()) --> state.uiModalClose
        ),
      )

      div(
        s"Found ${searchRes.length} result(s) in ${if(globalSearchScope) "all channels" else "the current workspace"} ",
        padding := "5px 0",
        fontWeight.bold,
        height := s"${dom.window.innerHeight/2}px",
        div(
          height := "100%",
          overflow.auto,
          searchRes,
        ),
        //TODO: Implement backend search
        //        button(
        //          cls := "ui button",
        //          marginTop := "10px",
        //          display := (if(globalSearchScope) "none" else "block"),
        //          "Search in all channels",
        //          onClick(needle) --> searchGlobal
        //        )
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
        nodeAvatar(node, size = 20),
        renderNodeData(node.data)(fontWeight.normal),
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
            cursor.pointer,
            cls := "ui primary icon button approve",
            Elements.icon(Icons.search),
            span(cls := "text", "Search", marginLeft := "5px"),
            onClick.stopPropagation(searchInputProcess) --> searchLocal
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
      cursor.pointer,
      Elements.icon(Icons.search),
      span("Search"),

      onClick.stopPropagation(Ownable(implicit ctx => UI.ModalConfig(header = header, description = description,
        modalModifier = cls := "form",
        contentModifier = backgroundColor := BaseColors.pageBgLight.copy(h = hue(node.id)).toHex,
      ))) --> state.uiModalConfig
    )
  }

  private def manageMembers(state: GlobalState, node: Node.Content)(implicit ctx: Ctx.Owner): VNode = {

    val clear = Handler.unsafe[Unit].mapObservable(_ => "")
    val userNameInputProcess = PublishSubject[String]
    val statusMessageHandler = PublishSubject[Option[(String, String, VDomModifier)]]

    def addUserMember(userId: UserId): Unit = {
      val change:GraphChanges = GraphChanges.from(addEdges = Set(
        Edge.Invite(node.id, userId),
        Edge.Member(node.id, EdgeData.Member(AccessLevel.ReadWrite), userId)
      ))
      state.eventProcessor.changes.onNext(change)
      clear.onNext(())
    }
    def handleAddMember(email: String)(implicit ctx: Ctx.Owner): Unit = {
      val graphUser = Client.api.getUserByEMail(email)
      graphUser.onComplete {
        case Success(Some(u)) if state.graph.now.members(node.id).exists(_.id == u.id) => // user exists and is already member
          statusMessageHandler.onNext(None)
          clear.onNext(())
          ()
        case Success(Some(u)) => // user exists with this email
          addUserMember(u.id)
        case Success(None)       => // user does not exist with this email
          Client.auth.getUserDetail(state.user.now.id).onComplete {
            case Success(Some(userDetail)) if userDetail.verified =>
              Client.auth.invitePerMail(address = email, node.id).onComplete {
                case Success(()) =>
                  statusMessageHandler.onNext(Some(("positive", "New member was invited", s"Invitation mail has been sent to '$email'.")))
                  clear.onNext(())
                case Failure(ex) =>
                  statusMessageHandler.onNext(Some(("negative", "Adding Member failed", "Unexpected error")))
                  scribe.warn("Could not add member to channel because invite failed", ex)
              }
            case Success(_) =>
              statusMessageHandler.onNext(Some(("negative", "Adding Member failed", "Please verify your own email address to send out invitation emails.")))
              scribe.warn("Could not add member to channel because user email is not verified")
            case Failure(ex) =>
              statusMessageHandler.onNext(Some(("negative", "Adding Member failed", "Unexpected error")))
              scribe.warn("Could not add member to channel", ex)
          }
        case Failure(ex)       =>
          statusMessageHandler.onNext(Some(("negative", "Adding Member failed", "Unexpected error")))
          scribe.warn("Could not add member to channel because get userdetails failed", ex)
      }
    }

    def handleRemoveMember(membership: Edge.Member)(implicit ctx: Ctx.Owner): Unit = {
      if(membership.userId == state.user.now.id) {
        if(dom.window.confirm("Do you really want to remove yourself from this workspace?")) {
          state.urlConfig.update(_.focus(Page.empty))
          state.uiModalClose.onNext(())
        } else return
      }

      val change:GraphChanges = GraphChanges.from(delEdges = Set(membership))
      state.eventProcessor.changes.onNext(change)
    }

    def userLine(user:Node.User)(implicit ctx: Ctx.Owner):VNode = {
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
          Components.displayUserName(user.data),
          fontSize := "15px",
          Styles.wordWrap,
        ),
      )
    }

    def description(implicit ctx: Ctx.Owner) = {
      var element: dom.html.Element = null
      val showEmailInvite = Var(false)
      val inputSizeMods = VDomModifier(width := "250px", height := "30px")
      VDomModifier(
        form(
          onDomMount.asHtml.foreach { element = _ },

          input(tpe := "text", position.fixed, left := "-10000000px", disabled := true), // prevent autofocus of input elements. it might not be pretty, but it works.

          showEmailInvite.map {
            case true => VDomModifier(
              div(
                cls := "ui fluid action input",
                inputSizeMods,
                input(
                  tpe := "email",
                  placeholder := "Invite by email address",
                  value <-- clear,
                  Elements.valueWithEnter(clearValue = false) foreach { str =>
                    if(element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
                      handleAddMember(str)
                    }
                  },
                  onChange.value --> userNameInputProcess
                ),
                div(
                  cls := "ui primary button approve",
                  "Add",
                  onClick.stopPropagation(userNameInputProcess) foreach { str =>
                    if(element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
                      handleAddMember(str)
                    }
                  }
                ),
              ),
              a(href := "#", padding := "5px", onClick.stopPropagation.preventDefault(false) --> showEmailInvite, "Invite user by username")
            )
            case false => VDomModifier(
              searchInGraph(state.rawGraph, "Invite by username", filter = u => u.isInstanceOf[Node.User] && !state.graph.now.members(node.id).exists(_.id == u.id), inputModifiers = inputSizeMods).foreach { userId =>
                addUserMember(UserId(userId))
              },
              a(href := "#", padding := "5px", onClick.stopPropagation.preventDefault(true) --> showEmailInvite, "Invite user by email address")
            )
          },
          statusMessageHandler.map {
            case Some((statusCls, title, errorMessage)) => div(
              cls := s"ui $statusCls message",
              div(cls := "header", title),
              p(errorMessage)
            )
            case None => VDomModifier.empty
          },
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
                    onClick.stopPropagation(membership).foreach(handleRemoveMember(_))
                  )
                )
              }:VDomModifier
            } else VDomModifier.empty
          },
          if(true) VDomModifier.empty else List(div)
        )
      )
    }

    div(
      cls := "item",
      cursor.pointer,
      Elements.icon(Icons.users),
      span("Members"),

      onClick.stopPropagation(Ownable(implicit ctx => UI.ModalConfig(
        header = UI.ModalConfig.defaultHeader(state, node, "Members", Icons.users),
        description = description,
        modalModifier = VDomModifier(
          cls := "mini form",
        ),
        contentModifier = VDomModifier(
          backgroundColor := BaseColors.pageBgLight.copy(h = hue(node.id)).toHex
        )
      ))) --> state.uiModalConfig
    )
  }

  def addToChannelsButton(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    button(
      cls := "ui compact primary button",
      if (BrowserDetect.isMobile) "Pin" else "Pin to sidebar",
      onClick.mapTo(GraphChanges(addEdges = Set(Edge.Pinned(channel.id, state.user.now.id), Edge.Notify(channel.id, state.user.now.id)), delEdges = Set(Edge.Invite(channel.id, state.user.now.id)))) --> state.eventProcessor.changes,
      onClick foreach { Analytics.sendEvent("pageheader", "join") }
    )
  }

  case class ConvertSelection(
    role: NodeRole,
    icon: IconLookup,
    description: String,
  )
  object ConvertSelection {

    def menuItem(state: GlobalState, node: Node.Content): VNode = {

      div(
        cls := "item",
//        Elements.icon(Icons.convertItem),
        span("Convert"),
        Components.horizontalMenu(
          ConvertSelection.all.map { convert =>
            Components.MenuItem(
              title = Elements.icon(convert.icon),
              description = convert.role.toString,
              active = node.role == convert.role,
              clickAction = { () =>
                state.eventProcessor.changes.onNext(GraphChanges.addNode(node.copy(role = convert.role)))
                Analytics.sendEvent("pageheader", "changerole", convert.role.toString)
              }
            )
          }
        )
      )
    }

    val all =
      ConvertSelection(
        role = NodeRole.Message,
        icon = Icons.conversation,
        description = "Message of a workspace or chat.",
      ) ::
        ConvertSelection(
          role = NodeRole.Task,
          icon = Icons.task,
          description = "Task item of a list or kanban.",
        ) ::
        ConvertSelection(
          role = NodeRole.Project,
          icon = Icons.project,
          description = "A Project. It can contain conversations and tasks.",
        ) ::
        // ConvertSelection(
        //   role = NodeRole.Stage,
        //   icon = Icons.stage,
        //   description = "Stage, a period in a structure / progress, e.g. column in a kanban",
        // ) ::
        Nil
  }
}
