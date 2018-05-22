package wust.webApp.views

import org.scalajs.dom.raw.Element
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.sdk.PostColor._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import cats.effect.IO

trait AuthView extends View {
  //TODO no boolean but adt
  def isInitiallySignup: Boolean

  //TODO: we need the last view to return to it on success?
  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = for {
    isSignup <- Handler.create[Boolean](isInitiallySignup)
    userName <- Handler.create[String]
    password <- Handler.create[String]
    nameAndPassword <- IO(userName.combineLatest(password))
    elem <- div (
      maxWidth := "400px", width := "400px",
      maxHeight := "400px", height := "400px",
      margin := "auto",
      isSignup.map {
        case true =>
          val actionSink: Sink[(String,String)] = sideEffect[(String, String)] {
            case (userName, password) =>  Client.auth.register(userName, password).foreach {
              case true => state.viewConfig() = state.viewConfig.now.noOverlayView
              case false => ()
            }
          }
          div(
            h2("Create an account"),
            div(cls := "ui fluid input", input(tpe := "text", placeholder := "Username", value := "", display.block, margin := "auto", onInput.value --> userName)),
            div(cls := "ui fluid input", input(tpe := "password", placeholder := "Password", value := "", display.block, margin := "auto", onInput.value --> password, onEnter(nameAndPassword) --> actionSink)),
            button(cls := "ui fluid primary button", "Signup", display.block, margin := "auto", onClick(nameAndPassword) --> actionSink),
            div(cls := "ui divider"),
            h3("Already have an account?", textAlign := "center"),
            button(cls := "ui fluid button", "Login with existing account", display.block, margin := "auto", onClick(false) --> isSignup)
          )
        case false =>
          val actionSink: Sink[(String,String)] = sideEffect[(String, String)] {
            case (userName, password) =>  Client.auth.login(userName, password).foreach {
              case true => state.viewConfig() = state.viewConfig.now.noOverlayView
              case false => ()
            }
          }
          div(
            h2("Login with existing account"),
            div(cls := "ui fluid input", input(tpe := "text", placeholder := "Username", value := "", display.block, margin := "auto", onInput.value --> userName)),
            div(cls := "ui fluid input", input(tpe := "password", placeholder := "Password", value := "", display.block, margin := "auto", onInput.value --> password, onEnter(nameAndPassword) --> actionSink)),
            button(cls := "ui fluid primary button", "Login", display.block, margin := "auto", onClick(nameAndPassword) --> actionSink),
            div(cls := "ui divider"),
            h3("New to Woost?", textAlign := "center"),
            button(cls := "ui fluid button", "Create an account", display.block, margin := "auto", onClick(true) --> isSignup)
          )
      }
    )
  } yield elem
}

object LoginView extends AuthView {
  val isInitiallySignup: Boolean = false
  val key = "login"
  val displayName = "Login"
}
object SignupView extends AuthView {
  val isInitiallySignup: Boolean = true
  val key = "signup"
  val displayName = "Signup"
}
