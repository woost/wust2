package wust.webApp.views

import fontAwesome._
import googleAnalytics.Analytics
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import semanticUi.{DimmerOptions, ModalOptions}
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import Rendered.renderNodeData
import wust.graph.Node.User
import wust.ids._
import wust.sdk.NodeColor.hue
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.webApp.Client
import wust.webApp.jsdom.{Navigator, Notifications, ShareData}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._

import scala.concurrent.Future
import scala.util.{Failure, Success}


object PageHeader {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._
    div(
      Rx {
        pageParentNodes().map { channel => channelRow(state, channel, state.user().channelNodeId) }
      }
    )
  }

  private def channelRow(state: GlobalState, channel: Node, channelNodeId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val channelTitle = editableNodeOnClick(state, channel, state.eventProcessor.changes, newTagParentIds = Set(channelNodeId))(ctx)(
      cls := "pageheader-channeltitle",
      onClick --> sideEffect { Analytics.sendEvent("pageheader", "editchanneltitle") }
    )

    div(
      padding := "5px",
      paddingRight := "20px",
      backgroundColor := BaseColors.pageBg.copy(h = hue(channel.id)).toHex,

      Styles.flex,
      alignItems.center,

      channelAvatar(channel, size = 30)(Styles.flexStatic, marginRight := "5px"),
      channelTitle(flexShrink := 1, paddingLeft := "5px", paddingRight := "5px", marginRight := "5px"),
      Rx {(state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](channelMembers(state, channel)(ctx)(Styles.flexStatic, marginRight := "10px"))},
      menu(state, channel)
    )
  }

  private def menu(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VDomModifier = {
    val buttonStyle = VDomModifier(Styles.flexStatic, marginLeft := "10px", fontSize := "20px", cursor.pointer)

    val isSpecialNode = Rx{ channel.id == state.user().id || channel.id == state.user().channelNodeId }
    val isBookmarked = Rx {
      state
        .graph()
        .children(state.user().channelNodeId)
        .contains(channel.id)
    }
    VDomModifier(
      Rx {(isSpecialNode() || isBookmarked()).ifFalse[VDomModifier](addToChannelsButton(state, channel)(ctx)(Styles.flexStatic, marginLeft := "10px"))},
      notifyControl(state, channel).apply(Styles.flexStatic, marginLeft := "auto", fontSize := "20px", cursor.pointer),
      addMember(state, channel).apply(buttonStyle),
      shareButton(channel).apply(buttonStyle),
      Rx {settingsMenu(state, channel, isBookmarked(), isSpecialNode()).apply(buttonStyle)},
    )
  }

  private def channelMembers(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexWrap.wrap,
      Rx {
        val graph = state.graph()
        val members = graph.members(channel.id)
        val authors = graph.authorsIn(channel.id)
        //TODO: possibility to show more
        //TODO: ensure that if I am member, my avatar is in the visible list
        val users = (members ++ authors).distinct.take(7)

        users.map(user => Avatar.user(user.id)(
          title := user.name,
          marginLeft := "2px",
          width := "22px",
          height := "22px",
          cls := "avatar",
          marginBottom := "2px",
        ))
      }
    )
  }

  private def shareButton(channel: Node)(implicit ctx: Ctx.Owner): VNode = {

    // Workaround: Autsch!
    val urlHolderId = "shareurlholder"
    val urlHolder = textArea(id := urlHolderId, height := "0px", width := "0px", opacity := 0, border := "0px", padding := "0px", fontSize := "0px", zIndex := 100, position.absolute)

    div(
      urlHolder,
      freeSolid.faShareAlt,
      onClick --> sideEffect {
        scribe.info(s"sharing post: $channel")

        val shareTitle = channel.data.str
        val shareUrl = dom.window.location.href
        val shareDesc = s"Share channel: $shareTitle, $shareUrl"

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
          Notifications.notify("Sharing link copied to clipboard", tag = Some("sharelink"), body = Some(shareDesc))
        }
      },
      onClick --> sideEffect { Analytics.sendEvent("pageheader", "share") }
    )
  }


  private def addMember(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = {
    val showDialog = Var(false)
    val activeDisplay = Rx { display := (if(showDialog()) "block" else "none") }
//    val addUserDialog = dialog(state, "Add Member", "Enter the username of a member you want to add", NodeColor.tagColor(channel.id).toHex)
//(onClick(GraphChanges.addNode(channel.copy(meta = channel.meta.copy(accessLevel = selection.access)))) --> state.eventProcessor.changes)
    //      addUserDialog(activeDisplay)(backgroundColor := NodeColor.tagColor(channel.id).toHex),


    val addMemberModal = PublishSubject[dom.html.Element]
    val addMember = PublishSubject[String]
    val removeMember = PublishSubject[Edge.Member]
    val userNameInputProcess = PublishSubject[String]

    addMember.foreach { name =>
      val graphUser = state.graph.now.userIdByName.get(name) match {
        case u @ Some(userId) => Future.successful(u)
        case _ => Client.api.getUserId(name)
      }

      graphUser.flatMap {
        case Some(u) => Client.api.addMember(node.id, u, AccessLevel.ReadWrite)
        case _       => Future.successful(false)
      }.onComplete {
        case Success(b) =>
          if(!b) {
            //TODO: display error in modal
            Notifications.notify("Add Member", tag = Some("addmember"), body = Some("Could not add member: Member does not exist"))
            scribe.error("Could not add member: Member does not exist")
          } else {
            Notifications.notify("Add Member", tag = Some("addmember"), body = Some("Successfully added member to the channel"))
            scribe.info("Added member to channel")
          }
        case Failure(ex) =>
          Notifications.notify("Add Member", tag = Some("addmember"), body = Some("Could not add member to channel"))
          scribe.error("Could not add member to channel", ex)
      }
    }

    removeMember.foreach { membership =>
      val change:GraphChanges = GraphChanges.from(delEdges = Set(membership))
      state.eventProcessor.changes.onNext(change)
    }

    div(
      cls := "item",
      freeSolid.faUserPlus,
      div(
        cls := "ui modal mini form",
        i(cls := "close icon"),
        div(
          cls := "header",
          backgroundColor := BaseColors.pageBg.copy(h = hue(node.id)).toHex,
          div(
            Styles.flex,
            alignItems.center,
            channelAvatar(node, size = 20)(marginRight := "5px"),
            renderNodeData(node.data)(fontFamily := "Roboto Slab", fontWeight.normal),
            paddingBottom := "5px",
          ),
          div(s"Manage Members"),
        ),
        div(
          cls := "content",
          backgroundColor := BaseColors.pageBgLight.copy(h = hue(node.id)).toHex,
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
              graph.membershipsByNodeId(node.id).map { membership =>
                val user = graph.nodesById(membership.userId).asInstanceOf[User]
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
                    fontSize := "15px",
                    user.name,
                    wordWrap := "break-word",
                    style("word-break") := "break-word",
                  ),
                  button(
                    cls := "ui tiny compact negative basic button",
                    marginLeft := "10px",
                    "Remove",
                    onClick(membership) --> removeMember
                  )
                )
              }
            }
          )
        ),
        onDomElementChange.asHtml --> sideEffect { elem =>
          import semanticUi.JQuery._
          $(elem).modal(new ModalOptions {
            //          blurring = true
            dimmerSettings = new DimmerOptions {
              opacity = "0.5"
            }
          }).modal("hide")
          addMemberModal.onNext(elem)
        },
      ),
      onClick.transform(_.withLatestFrom(addMemberModal)((_, o) => o)) --> sideEffect { elem =>
        import semanticUi.JQuery._
        $(elem).modal("toggle")
      },
    )
  }


  private def channelAvatar(node: Node, size: Int) = {
    Avatar(node)(
      width := s"${ size }px",
      height := s"${ size }px"
    )
  }

  private def notifyControl(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {

    def iconWithIndicator(icon: IconLookup, indicator: IconLookup, color: String): VNode = fontawesome.layered(
      fontawesome.icon(icon),
      fontawesome.icon(
        indicator,
        new Params {
          transform = new Transform {size = 13.0; x = 7.0; y = -7.0; }
          styles = scalajs.js.Dictionary[String]("color" -> color)
        }
      )
    )

    def decorateIcon(permissionState: PermissionState)(icon: IconLookup, action: VDomModifier, description: String): VDomModifier = {
      val default = "default".asInstanceOf[PermissionState]
      div(
        permissionState match {
          case PermissionState.granted => VDomModifier(
            (icon: VNode) (cls := "fa-fw"),
            title := description,
            action
          )
          case PermissionState.prompt | `default`  => VDomModifier(
            iconWithIndicator(icon, freeRegular.faQuestionCircle, "steelblue")(cls := "fa-fw"),
            title := "Notifications are currently disabled. Click to enable.",
            onClick --> sideEffect { Notifications.requestPermissionsAndSubscribe() }
          )
          case PermissionState.denied  => VDomModifier(
            iconWithIndicator(icon, freeRegular.faTimesCircle, "tomato")(cls := "fa-fw"),
            title := s"$description (Notifications are blocked by your browser. Please reconfigure your browser settings for this site.)",
            action
          )
        }
      )
    }

    div(
      Rx {
        val graph = state.graph()
        val user = state.user()
        val permissionState = state.permissionState()
        val hasNotifyEdge = graph.incomingEdges(user.id).exists(e => e.data == EdgeData.Notify && e.sourceId == channel.id)
        if(hasNotifyEdge) decorateIcon(permissionState)(
          freeRegular.faBell,
          action = onClick(GraphChanges.disconnect(Edge.Notify)(channel.id, user.id)) --> state.eventProcessor.changes,
          description = "You are watching this node and will be notified about changes. Click to stop watching."
        ) else decorateIcon(permissionState)(
          freeRegular.faBellSlash,
          action = onClick(GraphChanges.connect(Edge.Notify)(channel.id, user.id)) --> state.eventProcessor.changes,
          description = "You are not watching this node. Click to start watching."
        )
      }
    )
  }

  private def addToChannelsButton(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode =
    button(
      cls := "ui compact primary button",
      "Add to Channels",
      onClick(GraphChanges.connect(Edge.Parent)(channel.id, state.user.now.channelNodeId)) --> state.eventProcessor.changes,
      onClick --> sideEffect { Analytics.sendEvent("pageheader", "join") }
    )

  private def settingsMenu(state: GlobalState, channel: Node, bookmarked: Boolean, isOwnUser: Boolean)(implicit ctx: Ctx.Owner): VNode = {
    val permissionItem:Option[VNode] = channel match {
        case channel: Node.Content =>
          Some(div(
            cls := "item",
            i(cls := "dropdown icon"),
            span(cls := "text", "Permissions", cursor.default),
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
                  onClick --> sideEffect {
                    Analytics.sendEvent("pageheader", "changepermission", selection.access.str)
                  }
                )
              }
            )
          ))
        case _ => None
      }

    val leaveItem:Option[VNode] =
      (bookmarked && !isOwnUser).ifTrueOption(div(
        cls := "item",
        span(cls := "text", "Leave Channel", cursor.pointer),
        onClick(GraphChanges.disconnect(Edge.Parent)(channel.id, state.user.now.channelNodeId)) --> state.eventProcessor.changes
      ))

    val items:List[VNode] = List(permissionItem, leaveItem).flatten

    if(items.nonEmpty)
      div(
        // https://semantic-ui.com/modules/dropdown.html#pointing
        cls := "ui icon top left pointing dropdown",
        freeSolid.faCog,
        div(
          cls := "menu",
          div(cls := "header", "Settings", cursor.default),
          items
        ),
        // https://semantic-ui.com/modules/dropdown.html#/usage
        onInsert.asHtml --> sideEffect { elem =>
          import semanticUi.JQuery._
          $(elem).dropdown()
        },
        keyed(channel.id)
      )
    else
      div()
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
        val canAccess = graph
          .parents(nodeId)
          .exists(nid => graph.nodesById(nid).meta.accessLevel == NodeAccess.ReadWrite)
        console.log(graph.parents(nodeId).map(nid => graph.nodesById(nid)).mkString(", "))
        val inheritedLevel = if(canAccess) "Public" else "Private"
        s"Inherited ($inheritedLevel)"
      },
      value = "Inherited",
      description = "The permissions for this Node are inherited from its parents",
      icon = freeSolid.faArrowUp
    ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.ReadWrite),
        name = (_, _) => "Public",
        value = "Public",
        description = "Anyone can access this Node via the URL",
        icon = freeSolid.faUserPlus
      ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.Restricted),
        name = (_, _) => "Private",
        value = "Private",
        description = "Only you and explicit members can access this Node",
        icon = freeSolid.faLock
      ) ::
      Nil
}
