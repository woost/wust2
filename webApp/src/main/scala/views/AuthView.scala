package wust.webApp.views

import monix.reactive.Observer
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.AuthResult
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, View}
import wust.webApp.views.Elements._

import scala.concurrent.Future
import scala.util.{Failure, Success}

// an html view for the authentication. That is login and signup.
object AuthView {
  def apply(state: GlobalState)(
      header: String,
      submitText: String,
      submitAction: (String, String) => Future[Option[String]],
      alternativeHeader: String,
      alternativeView: String => View,
      alternativeText: String,
      defaultUsername: String = ""
  )(implicit ctx: Ctx.Owner): VNode =
    for {
      errorMessageHandler <- Handler.create[String]
      actionSink = sideEffect[(String, String)] {
        case (userName, password) =>
          submitAction(userName, password).onComplete {
            case Success(None)        => state.viewConfig() = state.viewConfig.now.redirect
            case Success(Some(vnode)) => errorMessageHandler.onNext(vnode)
            case Failure(t)           => errorMessageHandler.onNext(s"Unexpected error: $t")
          }
      }
      userName <- Handler.create[String](defaultUsername)
      password <- Handler.create[String]
      nameAndPassword = userName.combineLatest(password)
      elem <- div(
        padding := "10px",
        maxWidth := "400px",
        maxHeight := "400px",
        margin := "auto",
        form(
          h2(header),
          div(
            cls := "ui fluid input",
            input(
              placeholder := "Username",
              value := defaultUsername,
              tpe := "text",
              attr("autocomplete") := "username",
              display.block,
              margin := "auto",
              onInput.value --> userName,
              onInsert.asHtml --> sideEffect { e => if(defaultUsername.isEmpty) e.focus() }
            )
          ),
          div(
            cls := "ui fluid input",
            input(
              placeholder := "Password",
              tpe := "password",
              attr("autocomplete") := "current-password",
              display.block,
              margin := "auto",
              onInput.value --> password,
              onEnter(nameAndPassword) --> actionSink,
              onInsert.asHtml --> sideEffect { e => if(defaultUsername.nonEmpty) e.focus() }
            )
          ),
          button(
            cls := "ui fluid primary button",
            submitText,
            display.block,
            margin := "auto",
            marginTop := "5px",
            onClick(nameAndPassword) --> actionSink
          ),
          errorMessageHandler.map { errorMessage =>
            div(
              cls := "ui negative message",
              div(cls := "header", s"$submitText failed"),
              p(errorMessage)
            )
          },
          div(cls := "ui divider"),
          h3(alternativeHeader, textAlign := "center"),
          state.viewConfig.map { cfg =>
            div(
              onClick(userName).map(u => cfg.copy(view = alternativeView(u))) --> state.viewConfig,
              cls := "ui fluid button",
              alternativeText,
              display.block,
              margin := "auto",
              cursor.pointer
            )
          },
          onSubmit.preventDefault --> Observer.empty // prevent reloading the page on form submit
        )
      )
    } yield elem

  def login(state: GlobalState, defaultUsername:String = "")(implicit ctx: Ctx.Owner) =
    apply(state)(
      header = "Login with existing account",
      submitText = "Login",
      submitAction = (user, pw) =>
        Client.auth.login(user, pw).map {
          case AuthResult.BadPassword => Some("Wrong Password")
          case AuthResult.BadUser     => Some("Username does not exist")
          case AuthResult.Success     => None
        },
      alternativeHeader = "New to Woost?",
      alternativeView = username => View.Signup(username),
      alternativeText = "Create an account",
      defaultUsername = defaultUsername,
    )

  def signup(state: GlobalState, defaultUsername:String = "")(implicit ctx: Ctx.Owner) =
    apply(state)(
      header = "Create an account",
      submitText = "Signup",
      submitAction = (user, pw) =>
        Client.auth.register(user, pw).map {
          case AuthResult.BadPassword => Some("Insufficient password")
          case AuthResult.BadUser     => Some("Username already taken")
          case AuthResult.Success     => None
        },
      alternativeHeader = "Already have an account?",
      alternativeView = username => View.Login(username),
      alternativeText = "Login with existing account",
      defaultUsername = defaultUsername,
    )
}
