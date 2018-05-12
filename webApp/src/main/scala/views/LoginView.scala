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

object LoginView extends View {
  override val key = "login"
  override val displayName = "Login"

  //TODO: we need the last view to return to it on success?
  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = for {
    isSignup <- Handler.create[Boolean](true)
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
              case true => state.view() = View.default
              case false => ()
            }
          }
          div(
            h2("Create an account"),
            input(tpe := "text", placeholder := "Username", value := "", display.block, margin := "auto", onInput.value --> userName),
            input(tpe := "password", placeholder := "Password", value := "", display.block, margin := "auto", onInput.value --> password, onEnter(nameAndPassword) --> actionSink),
            button("Signup", display.block, margin := "auto", onClick(nameAndPassword) --> actionSink),
            hr(),
            div("Already have an account?", textAlign := "center", width := "100%"),
            button("Login with existing account", display.block, margin := "auto", onClick(false) --> isSignup)
          )
        case false =>
          val actionSink: Sink[(String,String)] = sideEffect[(String, String)] {
            case (userName, password) =>  Client.auth.login(userName, password).foreach {
              case true => state.view() = View.default
              case false => ()
            }
          }
          div(
            h2("Login with existing account"),
            input(tpe := "text", placeholder := "Username", value := "", display.block, margin := "auto", onInput.value --> userName),
            input(tpe := "password", placeholder := "Password", value := "", display.block, margin := "auto", onInput.value --> password, onEnter(nameAndPassword) --> actionSink),
            button("Login", display.block, margin := "auto", onClick(nameAndPassword) --> actionSink),
            hr(),
            div("New to Woost?", textAlign := "center", width := "100%"),
            button("Create an account", display.block, margin := "auto", onClick(true) --> isSignup)
          )
      }
    )
  } yield elem
}
