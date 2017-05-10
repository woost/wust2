package wust.frontend.views

import rx._
import org.scalajs.dom.window.location
import scalatags.JsDom.all._
import scalatags.rx.all._

import autowire._
import boopickle.Default._
import wust.graph.{User, Group}
import wust.frontend.{Client, GlobalState}
import wust.util.Pipe
import wust.util.tags._

import scala.concurrent.Future
import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object UserView {
  val inputText = input(`type` := "text")
  val inputPassword = input(`type` := "password")
  def buttonClick(name: String, handler: => Any) = button(name, onclick := handler _)

  val userField = inputText(placeholder := "user name").render
  val passwordField = inputPassword(placeholder := "password").render
  def clearOnSuccess(success: Future[Boolean]) = success.foreach(if (_) {
    userField.value = ""
    passwordField.value = ""
  })

  val registerButton = buttonClick(
    "register",
    Client.auth.register(userField.value, passwordField.value) |> clearOnSuccess
  )
  val loginButton = buttonClick(
    "login",
    Client.auth.login(userField.value, passwordField.value) |> clearOnSuccess
  )
  val logoutButton = buttonClick(
    "logout",
    Client.auth.logout()
  )

  //TODO: show existing in backend to revoke?
  private val createdGroupInvites = Var[Map[Group, String]](Map.empty)
  def groupInvite(group: Group)(implicit ctx: Ctx.Owner) =
    div(
      group.id.toString,
      Rx {
        val invites = createdGroupInvites()
        span(
          invites.get(group).map(token => aUrl(s"${location.host + location.pathname}#graph?invite=$token")).getOrElse(span()),
          buttonClick(
            "regenerate invite link",
            Client.api.createGroupInvite(group.id).call().foreach {
              case Some(token) => createdGroupInvites() = invites + (group -> token)
              case None =>
            }
          )
        ).render
      }
    )

  val registerMask = div(userField, passwordField, registerButton)
  // def userProfile(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
  //   span(state.currentUser.map(_.filterNot(_.isImplicit).map { user =>
  //     span(s"${user.name}").render
  //   }.getOrElse(span()))).render
  // }

  def groupProfile(groups: Seq[Group])(implicit ctx: Ctx.Owner) = div(groups.map(groupInvite): _*)

  def topBarUserStatus(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    (state.currentUser() match {
      case Some(user) if !user.isImplicit => span(user.name, logoutButton)
      case _ => registerMask(loginButton)
    }).render
  }

  // def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div {
  //   Rx {
  //     val userOpt = state.currentUser()
  //     val graph = state.rawGraph()
  //     userOpt match {
  //       case Some(user) =>
  //         userProfile(user)(if (user.isImplicit) registerMask else logoutButton)(groupProfile(graph.groups.toSeq)).render
  //       case None => registerMask(loginButton).render
  //     }
  //   }
  // }
}
