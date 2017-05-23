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
import wust.util.EventTracker.sendEvent

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
    Client.auth.register(userField.value, passwordField.value).call() ||> clearOnSuccess |> ((success: Future[Boolean]) => success.foreach(if (_)
      sendEvent("registration", "successful", "auth")
    else
      sendEvent("registration", "failed", "auth")))
  )
  val loginButton = buttonClick(
    "login",
    Client.auth.login(userField.value, passwordField.value).call() ||> clearOnSuccess |> ((success: Future[Boolean]) => success.foreach(if (_)
      sendEvent("login", "successful", "auth")
    else
      sendEvent("login", "failed", "auth")))
  )
  val logoutButton = buttonClick(
    "logout",
    {
      Client.auth.logout().call()
      sendEvent("logout", "logout", "auth")
    }
  )

  //TODO: show existing in backend to revoke?
  //TODO: instead of this local var, get all tokens from backend
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
            Client.api.recreateGroupInviteToken(group.id).call().foreach {
              case Some(token) => createdGroupInvites() = invites + (group -> token)
              case None        =>
            }
          )
        ).render
      }
    )

  val registerMask = div(display.flex, div(userField, br(), passwordField), registerButton)

  def groupProfile(groups: Seq[Group])(implicit ctx: Ctx.Owner) = div(groups.map(groupInvite): _*)

  def topBarUserStatus(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    (state.currentUser() match {
      case Some(user) if !user.isImplicit => span(user.name, logoutButton)
      case Some(user) if user.isImplicit  => span(registerMask(loginButton))
      case _                              => registerMask(loginButton)
    }).render
  }
}
