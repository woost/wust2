package wust.webApp.views

import fontAwesome._
import wust.webApp.jsdom.{Navigator, ShareData}
import org.scalajs.dom
import outwatch.dom._
import outwatch.reactive.handler._
import outwatch.ext.monix._
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.parsers.UrlConfigWriter
import wust.webUtil.{ Ownable, UI, BrowserDetect}
import outwatch.dom.dsl._
import rx._
import wust.webUtil.{Elements, ModalConfig}
import wust.webUtil.outwatchHelpers._
import wust.api.ApiEvent
import wust.graph.Node.User
import wust.graph._
import wust.ids._
import wust.css.Styles
import wust.sdk.Colors
import wust.util.StringOps
import wust.webApp._
import wust.webApp.jsdom.FormValidator
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webUtil.Elements._

import scala.scalajs.js
import scala.util.{Failure, Success}

object MembersModal {

  case class NeedAction(action: () => Unit, reason: String, isPositive: Boolean = true)
  object NeedAction {
    def apply(changes: GraphChanges, reason: String): NeedAction = NeedAction(() => GlobalState.submitChanges(changes), reason)
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
      node().fold(UrlConfig.default)(node => UrlConfig.default.focus(Page(node.id)))
    }
    val targetUrl = Rx {
      s"${dom.window.location.origin}${UrlConfigWriter.toString(targetUrlConfig())}"
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
          reason = "Do you really want to remove yourself from this workspace? You might not be able to access it again.",
          isPositive = false
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
            reason = "Do you really want to change your own access to this workspace? You might not be able to change it again.",
            isPositive = false
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
          div(
            cls := "ui message yellow",
            marginBottom := "25px",
            padding := "5px",
            b(need.reason),
            div(
              padding := "5px",
              Styles.flex,
              button(cls := "ui mini compact button", cls := (if (need.isPositive) "positive" else "negative"), "Yes", onClickDefault foreach { need.action(); needAction() = None }, margin := "0 5px 0 auto"),
              button(cls := "ui mini compact button basic", "No", onClickDefault foreach { needAction() = None })
            )
          )
        )),

        div(
          marginBottom := "25px",
          Styles.flex,
          flexWrap.wrap,
          alignItems.center,

          label("Link to share:", Styles.flexStatic, margin := "0 15px 5px 0"),
          div(
            minWidth := "250px",
            marginRight := "50px", //WHY? do we overflow otherwise? What kind of sorcery does fomantic-ui do?
            flex := "1",
            cls := "ui action input",
            input(tpe := "text", readOnly := true, value <-- targetUrl),
            button(
              cls := "ui compact button",
              freeRegular.faCopy,
              Rx {
                node() map { node =>
                  shareModifiers(node, targetUrl()) foreach { need => needAction() = Some(need) }
                }
              }
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
                    ("Inherit", NodeAccess.Inherited) ::
                    ("Public", NodeAccess.Read) ::
                    Nil,
                    unselectableMapping = Map(NodeAccess.ReadWrite -> NodeAccess.Read)
                  ).foreach { nodeAccess =>
                    updateNodeAccess(nodeAccess)
                  }
                ),

                i(node.meta.accessLevel match {
                  case NodeAccess.Inherited =>
                    VDomModifier(Rx {
                      //TODO: share this code...
                      val level = GlobalState.rawGraph().accessLevelOfNode(nodeId).getOrElse(AccessLevel.Restricted) match {
                        case AccessLevel.Restricted => "Private"
                        case AccessLevel.ReadWrite | AccessLevel.Read => "Public"
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
                  val appendUserString = if (user.id == userId) " (yourself)" else ""

                  userLine(
                    Components.renderUser(user, appendName = i(appendUserString)),
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
                span("Anyone with Link", padding := "4px", borderRadius := "8px", border := "1px solid lightgray"),
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
            Styles.flex,
            alignItems.center,
            flexWrap.wrap,

            label("Invite another User:", Styles.flexStatic, margin := "0 15px 5px 0"),
            searchInGraph(
              GlobalState.rawGraph,
              "Add user or invite by Email",
              filter = u => u.isInstanceOf[Node.User] && !GlobalState.graph.now.members(nodeId).exists(_.id == u.id),
              showNotFound = false,
              elementModifier = VDomModifier(
                flex := "1",
                minWidth := "250px",
              ),
              inputModifiers = VDomModifier(
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
                  ("can read", AccessLevel.Read) ::
                  ("can write", AccessLevel.ReadWrite) ::
                  Nil
                ).apply(cls := "ui mini dropdown", minWidth := "75px", maxWidth := "75px"),

                button(
                  cls := "ui basic compact button",
                  freeSolid.faPlus,
                  onClick.stopPropagation foreach(addUserEmailFromInput())
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
            cls := "ui primary button",
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

  def settingsButton(nodeId: NodeId)(implicit ctx: Ctx.Owner) = {
    val permissionLevel = Rx {
      Permission.resolveInherited(GlobalState.rawGraph(), nodeId)
    }

    val openSharingModalOnClick = onClickDefault.use(Ownable(implicit ctx => MembersModal.config(nodeId))) --> GlobalState.uiModalConfig

    GlobalState.presentationMode.map {
      case PresentationMode.Full => button(
        marginLeft := "5px",
        openSharingModalOnClick,
        cls := "ui tiny compact primary button",

        openSharingModalOnClick,
        permissionLevel.map(Permission.permissionIndicator(_)),
      )
      case PresentationMode.ContentOnly => VDomModifier.empty
    }
  }

  private def shareModifiers(channel: Node, shareUrl: String)(implicit ctx: Ctx.Owner): EmitterBuilder[NeedAction, VDomModifier] = EmitterBuilder { needAction =>
    import scala.concurrent.duration._

    val shareTitle = StringOps.trimToMaxLength(channel.data.str, 15)
    val shareDesc = s"Share: $shareTitle"

    def assurePublic(): Unit = {
      // make channel public if it is not. we are sharing the link, so we want it to be public.
      channel match {
        case node: Node.Content if node.meta.accessLevel != NodeAccess.Read && node.meta.accessLevel != NodeAccess.ReadWrite =>
          needAction.onNext(NeedAction(
            GraphChanges.addNode(node.copy(meta = node.meta.copy(accessLevel = NodeAccess.Read))),
            reason = s"This ${node.role.toString} is currently not public and therefore will not be accessible for other people with Link. Do you want to make it public?"
          ))
        case _ => ()
      }
    }

    VDomModifier(
      onClick.transform(_.delay(200 millis)).foreach { // delay, otherwise the assurePublic changes update interferes with clipboard js
        assurePublic()
        FeatureState.use(Feature.ShareLink)
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
        Elements.copiableToClipboard(shareUrl),
        onClick.stopPropagation foreach {
          scribe.info(s"Copying share-link for '$channel'")

          UI.toast(title = shareDesc, msg = "Link copied to clipboard")
        },
      ),
    )
  }
}
