package wust.webApp.views

import cats.data.NonEmptyList
import fontAwesome._
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive.handler._
import rx._
import scala.scalajs.js
import wust.css.Styles
import wust.graph.Node.User
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.webApp._
import wust.webApp.jsdom.{ FormValidator, Navigator, ShareData }
import wust.webApp.parsers.UrlConfigWriter
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Elements, ModalConfig, Ownable, UI }

import scala.util.{ Failure, Success }
import wust.facades.segment.Segment

object MembersModal {

  case class NeedAction(action: () => Unit, title: VDomModifier, reason: VDomModifier, leftIsPositive: Boolean = NeedAction.defaultLeftIsPositive, actOnRight: Boolean = NeedAction.defaultActOnRight, leftText: String = NeedAction.defaultLeftText, rightText: String = NeedAction.defaultRightText)
  object NeedAction {
    def defaultLeftText = "Yes"
    def defaultRightText = "No"
    def defaultActOnRight = false
    def defaultLeftIsPositive = true

    def apply(changes: GraphChanges, title: VDomModifier, reason: VDomModifier, actOnRight: Boolean, leftText: String, rightText: String): NeedAction = NeedAction(() => GlobalState.submitChanges(changes), title = title, reason = reason, actOnRight = actOnRight, leftText = leftText, rightText = rightText)
    def apply(changes: GraphChanges, title: VDomModifier, reason: VDomModifier): NeedAction = apply(changes, title, reason, actOnRight = defaultActOnRight, leftText = defaultLeftText, rightText = defaultRightText)
  }

  def config(nodeId: NodeId)(implicit ctx: Ctx.Owner): ModalConfig = {

    val node = GlobalState.rawGraph.map(_.nodesById(nodeId).collect { case n: Node.Content => n })

    val clear = Handler.unsafe[Unit]
    val userNameInputProcess = Handler.unsafe[String]
    val statusMessageHandler = Handler.unsafe[Option[(String, String, VDomModifier)]]

    val needAction: Var[Option[NeedAction]] = Var(None)

    val nodeAccess: Rx[Option[NodeAccess]] = Rx {
      node().map(_.meta.accessLevel)
    }
    val targetUrlConfig = Rx {
      node().fold(UrlConfig.default)(node => UrlConfig.default.copy(mode = GlobalState.urlConfig().mode).focus(Page(node.id)))
    }
    def urlConfigToUrl(urlConfig: UrlConfig) = s"${dom.window.location.origin}${UrlConfigWriter.toString(urlConfig)}"
    val targetUrl = Rx {
      urlConfigToUrl(targetUrlConfig())
    }
    val targetUrlWithPresentation = Rx {
      @inline def default = targetUrlConfig.now
      val config = node.now.fold(default)(_.views.fold(default) {
        case Nil          => default
        case _ :: Nil     => default
        case head :: tail => default.focus(View.Tiled(ViewOperator.Row, NonEmptyList(head, tail)))
      })
      urlConfigToUrl(config.copy(mode = PresentationMode.ContentOnly))
    }

    def addUserMember(userId: UserId, accesslevel: AccessLevel): Unit = node.now.foreach { node =>
      val change: GraphChanges = GraphChanges(addEdges = Array(
        Edge.Invite(node.id, userId),
        Edge.Member(node.id, EdgeData.Member(accesslevel), userId)
      ))
      GlobalState.submitChanges(change)
      clear.onNext(())
    }

    def handleAddMember(email: String, accesslevel: AccessLevel)(implicit ctx: Ctx.Owner): Unit = node.now.foreach { node =>
      val graphUser = Client.api.getUserByEMail(email)
      graphUser.onComplete {
        case Success(Some(u)) if GlobalState.graph.now.members(node.id).exists(_.id == u.id) => // user exists and is already member
          statusMessageHandler.onNext(None)
          clear.onNext(())
          ()
        case Success(Some(u)) => // user exists with this email
          addUserMember(u.id, accesslevel: AccessLevel)
        case Success(None) => // user does not exist with this email
          Client.auth.getUserDetail(GlobalState.user.now.id).onComplete {
            case Success(Some(userDetail)) if userDetail.verified =>
              Client.auth.invitePerMail(address = email, node.id, accesslevel).onComplete {
                case Success(()) =>
                  statusMessageHandler.onNext(Some(("positive", "New member was invited", s"Invitation mail has been sent to '$email'.")))
                  Segment.trackInviteSent()
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
        case Failure(ex) =>
          statusMessageHandler.onNext(Some(("negative", "Adding Member failed", "Unexpected error")))
          scribe.warn("Could not add member to channel because get userdetails failed", ex)
      }
    }

    def handleRemoveMember(membership: Edge.Member)(implicit ctx: Ctx.Owner): Unit = {
      def action(): Unit = {
        val change: GraphChanges = GraphChanges(delEdges = Array(membership))
        GlobalState.submitChanges(change)
      }

      if (membership.userId == GlobalState.user.now.id) {
        needAction() = Some(NeedAction(
          { () =>
            GlobalState.urlConfig.update(_.focus(Page.empty))
            GlobalState.uiModalClose.onNext(())
            action()
          },
          title = div(Styles.flex, alignItems.center, "Remove yourself from ", node.map(_.map(n => nodeCardAsOneLineText(n, projectWithIcon = true).apply(margin := "0 0.5em"))), "?"),
          reason = VDomModifier("Are you sure you want to leave the workspace? You may not be able to access it afterwards."),
          leftIsPositive = false
        ))
      } else {
        action()
      }
    }

    val description = {
      var formElement: dom.html.Form = null
      var inputElement: dom.html.Input = null

      val addUserAccessLevel: Var[Option[AccessLevel]] = Var(Some(AccessLevel.ReadWrite))

      val nodeAccessIsPrivate = nodeAccess.map(_ == Some(NodeAccess.Level(AccessLevel.Restricted)))
      val nodeAccessIsInherited = nodeAccess.map(n => n == Some(NodeAccess.Inherited) || n == Some(NodeAccess.Level(AccessLevel.Read)) || n == Some(NodeAccess.Level(AccessLevel.ReadWrite)))
      val nodeAccessIsPublic = nodeAccess.map(n => n == Some(NodeAccess.Level(AccessLevel.Read)) || n == Some(NodeAccess.Level(AccessLevel.ReadWrite)))

      def updateNodeAccess(nodeAccess: NodeAccess): Unit = node.now.foreach { node =>
        val newNode = node.copy(meta = node.meta.copy(accessLevel = nodeAccess))
        GlobalState.submitChanges(GraphChanges.addNode(newNode))
      }
      def updateMembership(membership: Edge.Member, level: AccessLevel): Unit = node.now.foreach { node =>
        def action(): Unit = {
          val newEdge = membership.copy(data = membership.data.copy(level = level))
          GlobalState.submitChanges(GraphChanges(addEdges = Array(newEdge)))
        }

        if (membership.userId == GlobalState.user.now.id) {
          needAction() = Some(NeedAction(
            action,
            title = div(Styles.flex, alignItems.center, "Change your own permissions for ", nodeCardAsOneLineText(node, projectWithIcon = true).apply(margin := "0 0.5em"), "?"),
            reason = "Do you really want to change your own access rights to this workspace? You may not be able to change that afterwards.",
            leftIsPositive = false
          ))
        } else {
          action()
        }
      }

      def addUserEmailFromInput(): Unit = {
        val str = inputElement.value
        if (str.nonEmpty && FormValidator.reportValidity(formElement)) {
          addUserAccessLevel.now.foreach(handleAddMember(str, _))
        }
      }

      def userLine(userName: VDomModifier, accesslevel: Option[AccessLevel], removeMember: () => Unit) = EmitterBuilder[AccessLevel, VNode] { sink =>
        div(
          marginBottom := "5px",
          Styles.flex,
          justifyContent.spaceBetween,
          alignItems.center,

          userName,

          div(
            Styles.flex,

            (EditableContent.selectEmitter[AccessLevel](
              Some("Access Level"),
              accesslevel,
              ("can read", AccessLevel.Read) ::
                ("can write", AccessLevel.ReadWrite) ::
                Nil
            ) --> sink).apply(minWidth := "80px", maxWidth := "80px"),

            button(
              marginLeft := "10px",
              cls := "ui tiny compact negative basic button",
              freeSolid.faTimes,
              onClickDefault.foreach(removeMember())
            )
          )
        )
      }

      VDomModifier(
        Styles.flex,
        flexDirection.column,

        needAction.map(_.map(need =>
          UI.message(
            header = Some(need.title),
            content = Some(VDomModifier(
              div(need.reason),
              div(
                padding := "5px",
                Styles.flex,
                button(cls := "ui mini compact button", cls := (if (need.leftIsPositive) "positive" else "negative"), need.leftText, onClickDefault foreach { if (!need.actOnRight) need.action(); needAction() = None }, margin := "0 5px 0 auto"),
                button(cls := "ui mini compact button basic", need.rightText, onClickDefault foreach { if (need.actOnRight) need.action(); needAction() = None })
              )
            ))
          ).apply(fontSize := "14px"))),

        div(
          marginBottom := "25px",
          Styles.flex,
          flexWrap.wrap,
          alignItems.center,

          div("Link to share:", Styles.flexStatic, margin := "0 15px 5px 0"),
          div(
            minWidth := "250px",
            width := "100%",
            flex := "1",
            cls := "ui action input",
            input(tpe := "text", readOnly := true, value <-- targetUrl, flex := "1"),
            button(
              cls := "ui compact button",
              freeRegular.faCopy,
              Rx {
                node() map { node =>
                  shareModifiers(node, targetUrl()) foreach { need => needAction() = Some(need) }
                }
              }
            ),
            button(
              cls := "ui compact button",
              padding := "2px",
              borderLeft := "1px solid lightgray",
              UI.dropdownMenu(VDomModifier(
                padding := "5px",
                div(
                  cls := "item",
                  b("Copy Link"),
                  Rx {
                    node() map { node =>
                      shareModifiers(node, targetUrl()) foreach { need => needAction() = Some(need) },
                    }
                  }
                ),
                div(
                  cls := "item",
                  "Copy Presentation Link",
                  Rx {
                    node() map { node =>
                      shareModifiers(node, targetUrlWithPresentation()) foreach { need => needAction() = Some(need) },
                    }
                  }
                )
              ), dropdownModifier = cls := "right top"),
              i(cls := "dropdown icon"),
            )
          )
        ),

        b("Who has access?"),

        div(
          margin := "0px 0 25px 10px",

          div(
            Styles.flex,
            flexDirection.column,
            marginBottom := "15px",

            node.map(_.map { node =>
              VDomModifier(
                span(
                  marginBottom := "5px",
                  alignSelf.flexEnd,
                  Styles.flex,
                  alignItems.center,

                  label(
                    Styles.flexStatic,
                    marginRight := "10px",
                    "Permission Setting:"
                  ),

                  EditableContent.selectEmitter[NodeAccess](
                    None,
                    Some(node.meta.accessLevel),
                    ("Private", NodeAccess.Restricted) ::
                      ("Inherit Permissions", NodeAccess.Inherited) ::
                      ("Anyone with Link", NodeAccess.ReadWrite) ::
                      Nil,
                    unselectableMapping = Map(NodeAccess.Read -> NodeAccess.ReadWrite)
                  ).foreach { nodeAccess =>
                      updateNodeAccess(nodeAccess)
                    }
                ),

                i(node.meta.accessLevel match {
                  case NodeAccess.Inherited =>
                    VDomModifier(Rx {
                      //TODO: share this code...
                      val level = GlobalState.rawGraph().accessLevelOfNode(nodeId).getOrElse(AccessLevel.Restricted) match {
                        case AccessLevel.Restricted                   => "Private"
                        case AccessLevel.ReadWrite | AccessLevel.Read => "Anyone with Link"
                      }
                      s"This ${node.role.toString} is accessible for members of parent folders (${level}) and the members listed below."
                    })
                  case NodeAccess.Level(AccessLevel.Restricted) =>
                    s"This ${node.role.toString} is only accessible for the members listed below."
                  case NodeAccess.Level(AccessLevel.Read | AccessLevel.ReadWrite) =>
                    s"This ${node.role.toString} is accessible for members of parent folders and anyone with link."
                })
              )
            })
          ),

          Rx {
            node().map { node =>
              val graph = GlobalState.graph()
              val userId = GlobalState.userId()
              graph.idToIdx(node.id).map { nodeIdx =>
                graph.membershipEdgeForNodeIdx(nodeIdx).map { membershipIdx =>
                  val membership = graph.edges(membershipIdx).as[Edge.Member]
                  val user = graph.nodes(graph.edgesIdx.b(membershipIdx)).as[User]
                  val appendUserString = if (user.id == userId) " (You)" else ""

                  userLine(
                    Components.renderUser(user, appendName = span(appendUserString, opacity := 0.5)),
                    Some(membership.data.level),
                    () => handleRemoveMember(membership)
                  ).foreach { newLevel =>
                      updateMembership(membership, newLevel)
                    }
                }
              }
            }
          },

          nodeAccess.map(_.map {
            case NodeAccess.Inherited | NodeAccess.Level(AccessLevel.Restricted) => VDomModifier.empty

            case NodeAccess.Level(level) =>
              userLine(
                span("Anyone with Link", padding := "4px 8px", borderRadius := "4px", border := "1px solid lightgray"),
                Some(level),
                () => updateNodeAccess(NodeAccess.Inherited)
              ).foreach { newLevel =>
                  updateNodeAccess(NodeAccess.Level(newLevel))
                }
          }),
        ),

        form(
          onSubmit.preventDefault.discard,
          onDomMount.asHtml.foreach { e => formElement = e.asInstanceOf[dom.html.Form] },

          width := "100%",
          marginBottom := "25px",

          input(tpe := "text", position.fixed, left := "-10000000px", disabled := true), // prevent autofocus of input elements. it might not be pretty, but it works.

          div(
            b("Invite more users:", Styles.flexStatic),
            searchInGraph(
              GlobalState.rawGraph,
              "username or email",
              filter = u => u.isInstanceOf[Node.User] && !GlobalState.graph.now.members(nodeId).exists(_.id == u.id),
              showNotFound = false,
              elementModifier = VDomModifier(
                marginTop := "5px",
                flex := "1",
                minWidth := "250px",
              ),
              inputModifiers = VDomModifier(
                value <-- clear.map(_ => ""),
                onDomMount.asHtml.foreach { e => inputElement = e.asInstanceOf[dom.html.Input] },
                tpe := "email",
                flex := "1",
              ),
              inputDecoration = Some(VDomModifier(
                cls := "ui action input",
                width := "100%",

                EditableContent.select[AccessLevel](
                  None,
                  addUserAccessLevel,
                  ("can write", AccessLevel.ReadWrite) ::
                    ("can read", AccessLevel.Read) ::
                    Nil
                ).apply(cls := "ui mini dropdown", minWidth := "75px", maxWidth := "75px"),

                button(
                  cls := "ui primary button",
                  "Invite",
                  onClick.stopPropagation foreach (addUserEmailFromInput())
                ),
              ))
            ).foreach { userId => addUserAccessLevel.now.foreach(addUserMember(UserId(userId), _)) },
          ),

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
          textAlign.right,
          button(
            "Done",
            cls := "ui button",
            onClickDefault.use(()) --> GlobalState.uiModalClose
          )
        )
      )
    }

    ModalConfig(
      header = node.map(_.map(Modal.defaultHeader(_, "Sharing Settings", Icons.users))),
      description = description,
      modalModifier = VDomModifier(
        cls := "mini form",
        width := "600px",
      ),
      contentModifier = VDomModifier.empty
    )
  }

  def openSharingModalOnClick(nodeId: NodeId, analyticsVia:String) = onClickDefault.foreach{
    GlobalState.uiModalConfig.onNext(Ownable(implicit ctx => MembersModal.config(nodeId)))
    Segment.trackEvent("Open Members Modal", js.Dynamic.literal(via = analyticsVia))
  }

  def settingsButton(nodeId: NodeId, analyticsVia:String, tooltip:String = "Add members", tooltipPosition:String = "bottom center")(implicit ctx: Ctx.Owner):VNode = {
    val permissionLevel = Rx {
      Permission.resolveInherited(GlobalState.rawGraph(), nodeId)
    }


    div(
      openSharingModalOnClick(nodeId, analyticsVia),

      display.flex,
      alignItems.flexStart,
      padding := "0.5em",

      // permissionLevel.map(Permission.permissionIndicator(_)),

      div(
        cls := "fa-fw",
        Icons.membersModal
      ),
      UI.tooltip(tooltipPosition) := tooltip
    )
  }

  private def shareModifiers(channel: Node, shareUrl: String)(implicit ctx: Ctx.Owner): EmitterBuilder[NeedAction, VDomModifier] = EmitterBuilder { needAction =>

    def assurePublic(): Unit = {
      // make channel public if it is not. we are sharing the link, so we want it to be public.
      channel match {
        case node: Node.Content if node.meta.accessLevel != NodeAccess.Read && node.meta.accessLevel != NodeAccess.ReadWrite =>
          GlobalState.submitChanges(GraphChanges.addNode(node.copy(meta = node.meta.copy(accessLevel = NodeAccess.ReadWrite))))

          needAction.onNext(NeedAction(
            GraphChanges.addNode(node),
            title = div(Styles.flex, alignItems.center, nodeCardAsOneLineText(node, projectWithIcon = true).apply(margin := "0 0.5em 0 0"), " is now accessible via a link."),
            reason = s"This ${node.role.toString} is now accessible to everyone with the link below. If this was not your intention, you can undo this change.",
            actOnRight = true,
            leftText = "Ok",
            rightText = "Undo"
          ))
        case _ => ()
      }
    }

    VDomModifier(
      if (Navigator.share.isDefined) VDomModifier(
        onClick foreach {
          scribe.info(s"Sharing '$channel'")

          Navigator.share(new ShareData {
            url = shareUrl
          }).toFuture.onComplete {
            case Success(()) => ()
            case Failure(t)  => scribe.warn("Cannot share url via share-api", t)
          }
        },
      )
      else VDomModifier(
        Elements.copiableToClipboard(shareUrl),
        onClick foreach {
          scribe.info(s"Copying share-link for '$channel'")

          UI.toast("Link copied to clipboard")
        },
      ),

      onClick.foreach {
        assurePublic()
        FeatureState.use(Feature.ShareLink)
      },
    )
  }
}
