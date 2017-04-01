package wust.frontend.views

import org.scalajs.dom._
import concurrent.Future
import rx._
import scalatags.rx.all._
import scalatags.JsDom.all._
import boopickle.Default._
import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import wust.frontend.{ Client, GlobalState }
import wust.api.User
import wust.util.Pipe

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

  val registerButton =
    buttonClick("register", Client.auth.register(userField.value, passwordField.value) |> clearOnSuccess)
  def loginButton(currentUser: WriteVar[Option[User]]) =
    buttonClick("login", Client.auth.login(userField.value, passwordField.value) |> clearOnSuccess)
  def logoutButton(currentUser: WriteVar[Option[User]]) =
    buttonClick("logout", Client.auth.logout())

  val registerMask = div(userField, passwordField, registerButton)
  def userProfile(currentUser: WriteVar[Option[User]], user: User) =
    div(user.toString, logoutButton(currentUser))

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    state.currentUser.rx.map {
      case Some(user) => userProfile(state.currentUser, user)(if (user.isImplicit) registerMask else div()).render
      case None       => registerMask(loginButton(state.currentUser)).render
    }
  }
}
