package wust.webApp.views

import monix.reactive.Observer
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.{AuthResult, AuthUser}
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, PageChange, View}
import wust.webApp.views.Elements._
import cats.effect.IO
import org.scalajs.dom
import wust.graph.Page
import wust.util._

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.{Failure, Success}


// an html view for the authentication. That is login and signup.
object AuthView {
  private case class UserValue(username: String = "", email: String = "", password: String = "")
  private val userValue = Var(UserValue())

  def apply(state: GlobalState)(
      header: String,
      submitText: String,
      needsEmail: Boolean,
      submitAction: UserValue => Future[Option[String]],
      alternativeHeader: String,
      alternativeView: View,
      alternativeText: String,
      autoCompletePassword: String
  )(implicit ctx: Ctx.Owner): VNode = {
    val errorMessageHandler = Handler.unsafe[String]
    var element: dom.html.Form = null
    def actionSink() = {
      if (element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) submitAction(userValue.now).onComplete {
        case Success(None)        =>
          userValue() = UserValue()
          state.viewConfig() = state.viewConfig.now.redirect
        case Success(Some(vnode)) =>
          errorMessageHandler.onNext(vnode)
        case Failure(t)           =>
          errorMessageHandler.onNext(s"Unexpected error: $t")
      }
    }

    div(
      onSubmit.foreach(_.preventDefault()),
      padding := "10px",
      maxWidth := "400px",
      maxHeight := "400px",
      margin := "auto",
      form(
        onDomMount foreach { e => element = e.asInstanceOf[dom.html.Form] },
        onSubmit.preventDefault --> Observer.empty, // prevent reloading the page on form submit

        h2(header),
        div(
          cls := "ui fluid input",
          keyed,
          input(
            placeholder := "Username",
            value <-- userValue.map(_.username),
            tpe := "text",
            required := true,
            attr("autocomplete") := "username",
            display.block,
            margin := "auto",
            onInput.value foreach { str => userValue.update(_.copy(username = str)) },
            onDomMount.asHtml --> inNextAnimationFrame { e => if(userValue.now.username.isEmpty) e.focus() }
          )
        ),
        needsEmail.ifTrue[VDomModifier](div(
          cls := "ui fluid input",
          keyed,
          input(
            placeholder := "Email",
            value <-- userValue.map(_.email),
            tpe := "email",
            required := true,
            display.block,
            margin := "auto",
            onInput.value foreach { str => userValue.update(_.copy(email = str)) },
            onDomMount.asHtml --> inNextAnimationFrame { e => if(userValue.now.username.nonEmpty) e.focus() }
          )
        )),
        div(
          cls := "ui fluid input",
          keyed,
          input(
            placeholder := "Password",
            value <-- userValue.map(_.password),
            tpe := "password",
            required := true,
            attr("autocomplete") := autoCompletePassword,
            display.block,
            margin := "auto",
            onInput.value foreach { str => userValue.update(_.copy(password = str)) },
            onEnter foreach actionSink(),
            onDomMount.asHtml --> inNextAnimationFrame { e => if(userValue.now.username.nonEmpty && (!needsEmail || userValue.now.email.nonEmpty)) e.focus() }
          )
        ),
        button(
          cls := "ui fluid primary button",
          submitText,
          display.block,
          margin := "auto",
          marginTop := "5px",
          onClick foreach actionSink()
        ),
        errorMessageHandler.map { errorMessage =>
          div(
            cls := "ui negative message",
            div(cls := "header", s"$submitText failed"),
            p(errorMessage)
          )
        },
        Rx {
          state.user() match {
            case AuthUser.Implicit(_, name, _) => div(
              marginTop := "10px",
              fontSize := "10px",
              span("You already created content as an unregistered user. If you login or register, the content will be moved into your account. "),
              a(
                href := "#",
                color := "tomato",
                marginLeft := "auto",
                "Discard content",
                onClick.preventDefault foreach {
                  Client.auth.logout()
                  state.viewConfig() = state.viewConfig.now.copy(pageChange = PageChange(Page.empty, needsGet = false), redirectTo = None)
                  ()
                }
              )
            )
            case _ => VDomModifier.empty

          }
        },
        div(cls := "ui divider"),
        h3(alternativeHeader, textAlign := "center"),
        state.viewConfig.map { cfg =>
          div(
            onClick(cfg.copy(view = alternativeView)) --> state.viewConfig,
            cls := "ui fluid button",
            alternativeText,
            display.block,
            margin := "auto",
            cursor.pointer
          )
        },
        marginBottom := "20px",
      )
    )
  }

  def login(state: GlobalState)(implicit ctx: Ctx.Owner) =
    apply(state)(
      header = "Login with existing account",
      submitText = "Login",
      needsEmail = false,
      submitAction = userValue =>
        Client.auth.login(userValue.username, userValue.password).map {
          case AuthResult.BadPassword => Some("Wrong Password")
          case AuthResult.BadUser     => Some("Username does not exist")
          case AuthResult.BadEmail    => None
          case AuthResult.Success     => None
        },
      alternativeHeader = "New to Woost?",
      alternativeView = View.Signup,
      alternativeText = "Create an account",
      autoCompletePassword = "current-password"
    )

  def signup(state: GlobalState)(implicit ctx: Ctx.Owner) =
    apply(state)(
      header = "Create an account",
      submitText = "Signup",
      needsEmail = true,
      submitAction = userValue =>
        Client.auth.register(name = userValue.username, email = userValue.email, password = userValue.password).map {
          case AuthResult.BadPassword => Some("Insufficient password")
          case AuthResult.BadUser     => Some("Username already taken")
          case AuthResult.BadEmail    => Some("Email address already taken")
          case AuthResult.Success     => None
        },
      alternativeHeader = "Already have an account?",
      alternativeView = View.Login,
      alternativeText = "Login with existing account",
      autoCompletePassword = "new-password"
    )
}
