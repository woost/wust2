package wust.webApp.views

import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.{Elements, ModalConfig}
import wust.webUtil.outwatchHelpers._
import wust.api.ApiEvent
import wust.graph.Node.User
import wust.graph._
import wust.ids._
import wust.webApp._
import wust.webApp.jsdom.FormValidator
import wust.webApp.state._
import wust.webApp.views.Components._

import scala.scalajs.js
import scala.util.{Failure, Success}

object MembersModal {
  def config(node: Node.Content)(implicit ctx: Ctx.Owner): ModalConfig = {

    val clear = Handler.unsafe[Unit].mapObservable(_ => "")
    val userNameInputProcess = PublishSubject[String]
    val statusMessageHandler = PublishSubject[Option[(String, String, VDomModifier)]]

    def addUserMember(userId: UserId): Unit = {
      val change: GraphChanges = GraphChanges(addEdges = Array(
        Edge.Invite(node.id, userId),
        Edge.Member(node.id, EdgeData.Member(AccessLevel.ReadWrite), userId)
      ))
      GlobalState.eventProcessor.changes.onNext(change)
      clear.onNext(())
    }
    def handleAddMember(email: String)(implicit ctx: Ctx.Owner): Unit = {
      val graphUser = Client.api.getUserByEMail(email)
      graphUser.onComplete {
        case Success(Some(u)) if GlobalState.graph.now.members(node.id).exists(_.id == u.id) => // user exists and is already member
          statusMessageHandler.onNext(None)
          clear.onNext(())
          ()
        case Success(Some(u)) => // user exists with this email
          addUserMember(u.id)
          //manually add this user into our local graph
          GlobalState.eventProcessor.localEvents.onNext(ApiEvent.NewGraphChanges.forPrivate(GlobalState.user.now.toNode, GraphChanges(addNodes = Array(u))))
        case Success(None) => // user does not exist with this email
          Client.auth.getUserDetail(GlobalState.user.now.id).onComplete {
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
        case Failure(ex) =>
          statusMessageHandler.onNext(Some(("negative", "Adding Member failed", "Unexpected error")))
          scribe.warn("Could not add member to channel because get userdetails failed", ex)
      }
    }

    def handleRemoveMember(membership: Edge.Member)(implicit ctx: Ctx.Owner): Unit = {
      if (membership.userId == GlobalState.user.now.id) {
        if (dom.window.confirm("Do you really want to remove yourself from this workspace?")) {
          GlobalState.urlConfig.update(_.focus(Page.empty))
          GlobalState.uiModalClose.onNext(())
        } else return
      }

      val change: GraphChanges = GraphChanges(delEdges = Array(membership))
      GlobalState.eventProcessor.changes.onNext(change)
    }

    def description(implicit ctx: Ctx.Owner) = {
      var element: dom.html.Form = null
      val showEmailInvite = Var(false)
      val inputSizeMods = VDomModifier(width := "250px", height := "30px")
      VDomModifier(
        form(
          onDomMount.asHtml.foreach { e => element = e.asInstanceOf[dom.html.Form] },

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
                    if (FormValidator.reportValidity(element)) {
                      handleAddMember(str)
                    }
                  },
                  onChange.value --> userNameInputProcess
                ),
                div(
                  cls := "ui primary button approve",
                  "Add",
                  onClick.stopPropagation(userNameInputProcess) foreach { str =>
                    if (FormValidator.reportValidity(element)) {
                      handleAddMember(str)
                    }
                  }
                ),
              ),
              a(href := "#", padding := "5px", onClick.stopPropagation.preventDefault(false) --> showEmailInvite, "Invite user by username")
            )
            case false => VDomModifier(
              searchInGraph(GlobalState.rawGraph, "Invite by username", filter = u => u.isInstanceOf[Node.User] && !GlobalState.graph.now.members(node.id).exists(_.id == u.id), inputModifiers = inputSizeMods).foreach { userId =>
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
            val graph = GlobalState.graph()
            graph.idToIdx(node.id).map { nodeIdx =>
              graph.membershipEdgeForNodeIdx(nodeIdx).map { membershipIdx =>
                val membership = graph.edges(membershipIdx).as[Edge.Member]
                val user = graph.nodesByIdOrThrow(membership.userId).as[User]
                Components.renderUser(user).apply(
                  marginTop := "10px",
                  button(
                    cls := "ui tiny compact negative basic button",
                    marginLeft := "10px",
                    "Remove",
                    onClick.stopPropagation(membership).foreach(handleRemoveMember(_))
                  )
                )
              }: VDomModifier
            }
          },
          if (true) VDomModifier.empty else List(div)
        ),
        div(
          textAlign.right,
          button(
            "Done",
            cls := "ui primary button",
            onClick.stopPropagation.foreach{ _ => GlobalState.uiModalClose.onNext(()) }
          )
        )
      )
    }

    ModalConfig(
      header = Modal.defaultHeader( node, "Members", Icons.users),
      description = description,
      modalModifier = VDomModifier(
        cls := "mini form",
      ),
      contentModifier = VDomModifier.empty
    )
  }
}
