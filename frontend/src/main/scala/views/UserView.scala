package wust.frontend.views

import rx._
import wust.api.User
import wust.frontend.{Client, GlobalState}
import wust.util.Pipe

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom.all._
import scalatags.rx.all._

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

  val registerButton = buttonClick("register",
    Client.auth.register(userField.value, passwordField.value) |> clearOnSuccess)
  val loginButton = buttonClick("login",
    Client.auth.login(userField.value, passwordField.value) |> clearOnSuccess)
  val logoutButton = buttonClick("logout",
    Client.auth.logout())

  val registerMask = div(userField, passwordField, registerButton)
  def userProfile(user: User) = div(user.toString, logoutButton)

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div {
    state.currentUser.map {
      case Some(user) => userProfile(user)(if (user.isImplicit) registerMask else div()).render
      case None => registerMask(loginButton).render
    }
  }
}
