package wust.webApp.views

import org.scalajs.dom.raw.Element
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.sdk.NodeColor._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import cats.effect.IO

import scala.concurrent.Future

object AuthView {
  def apply(state: GlobalState)(
    header: String,
    submitText: String,
    submitAction: (String, String) => Future[Boolean], //TODO Future[View] or something to show inner error?
    alternativeHeader: String,
    alternativeView: View,
    alternativeText: String
  )(implicit ctx: Ctx.Owner): VNode = {
    val actionSink: Sink[(String,String)] = sideEffect[(String, String)] {
      case (userName, password) =>  submitAction(userName, password).foreach {
        case true => state.viewConfig() = state.viewConfig.now.noOverlayView
        case false => () //TODO inform user!
      }
    }

    for {
        userName <- Handler.create[String]
        password <- Handler.create[String]
        viewConfig = state.viewConfig.toObservable
        nameAndPassword <- IO(userName.combineLatest(password))
        elem <- div (
          padding := "10px",
          maxWidth := "400px", width := "400px",
          maxHeight := "400px", height := "400px",
          margin := "auto",
          form(
            h2(header),
            div(cls := "ui fluid input", input(tpe := "text", placeholder := "Username", value := "", display.block, margin := "auto", onInput.value --> userName)),
            div(cls := "ui fluid input", input(tpe := "password", placeholder := "Password", value := "", display.block, margin := "auto", onInput.value --> password, onEnter(nameAndPassword) --> actionSink)),
            button(cls := "ui fluid primary button", submitText, display.block, margin := "auto", marginTop := "5px", onClick(nameAndPassword) --> actionSink),
            div(cls := "ui divider"),
            h3(alternativeHeader, textAlign := "center"),
            state.viewConfig.map { cfg =>
              viewConfigLink(cfg.copy(view = alternativeView))(cls := "ui fluid button", alternativeText, display.block, margin := "auto")
            }
          )
        )} yield elem
  }
}

object LoginView extends View {
  val key = "login"
  val displayName = "Login"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) =
    AuthView(state)(
      header = "Login with existing account",
      submitText = "Login",
      submitAction = Client.auth.login,
      alternativeHeader = "New to Woost?",
      alternativeView = SignupView,
      alternativeText = "Create an account")
}
object SignupView extends View {
  val key = "signup"
  val displayName = "Signup"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) =
    AuthView(state)(
      header = "Create an account",
      submitText = "Signup",
      submitAction = Client.auth.register,
      alternativeHeader = "Already have an account?",
      alternativeView = LoginView,
      alternativeText = "Login with existing account")
}
