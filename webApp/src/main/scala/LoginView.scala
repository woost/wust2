package wust.webApp.views

import org.scalajs.dom.raw.Element
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.webApp.GlobalState
import wust.sdk.PostColor._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._

object LoginView extends View {
  override val key = "login"
  override val displayName = "Login"

  //TODO: we need the last view to return to it on success?
  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = for {
    isSignup <- Handler.create[Boolean](true)
    userName <- Handler.create[String]
    password <- Handler.create[String]
    elem <- div (
      isSignup.map {
        case true => div(
          input(tpe := "text", onChange.value --> userName),
          input(tpe := "password", onChange.value --> password),
          button("Signup",
            onClick(userName.combineLatest(password)) --> sideEffect(_ match { // TODO: should work with case?
              case (userName, password) =>  Client.auth.register(userName, password).foreach { success =>
                if (success) state.view() = View.default
              }
            })
          ),
          button("Already a user?", onClick(false) --> isSignup)
        )
        case false => div(
          input(tpe := "text", onChange.value --> userName),
          input(tpe := "password", onChange.value --> password),
          button("Login",
            onClick(userName.combineLatest(password)) --> sideEffect(_ match { // TODO: should work with case?
              case (userName, password) =>  Client.auth.login(userName, password).foreach { success =>
                if (success) state.view() = View.default
              }
            })
          ),
          button("Want to join?", onClick(true) --> isSignup)
        )
      }
    )
  } yield elem
}
