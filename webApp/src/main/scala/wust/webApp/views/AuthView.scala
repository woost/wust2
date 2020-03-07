package wust.webApp.views

import org.scalajs.dom
import outwatch._
import outwatch.dsl._
import outwatch.reactive.handler._
import rx._
import wust.api.{AuthResult, AuthUser, Password}
import wust.css.Styles
import wust.graph.Page
import wust.ids._
import wust.util._
import wust.webApp._
import wust.webApp.jsdom.FormValidator
import wust.webApp.state.{FeatureState, GlobalState, PageChange}
import wust.webApp.views.Components._
import wust.webUtil.Elements._
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import wust.facades.segment.Segment


// an html view for the authentication. That is login and signup.
object AuthView {
  private final case class UserValue(username: String = "", email: String = "", password: String = "")
  private val userValue = Var(UserValue())

  sealed trait StatusMessage
  object StatusMessage {
    case class Success(header: String, msg: String) extends StatusMessage
    case class Error(header: String, msg: String) extends StatusMessage
  }

  def apply(
      header: String,
      submitText: String,
      needUserName: Boolean,
      submitAction: UserValue => Future[Option[String]],
      alternativeHeader: String,
      alternativeView: View,
      alternativeText: String,
      autoCompletePassword: String,
      showPasswordReset: Boolean
  )(implicit ctx: Ctx.Owner): VNode = {
    val forgotPasswordMode = Var(false)
    val errorMessageHandler = Handler.unsafe[StatusMessage]
    var element: dom.html.Form = null
    def actionSink() = {
      if (FormValidator.reportValidity(element)) submitAction(userValue.now).onComplete {
        case Success(None)        =>
          userValue() = UserValue()
          GlobalState.urlConfig.update(_.redirect)
        case Success(Some(error)) =>
          errorMessageHandler.onNext(StatusMessage.Error(s"$submitText failed", error))
        case Failure(t)           =>
          errorMessageHandler.onNext(StatusMessage.Error(s"$submitText failed", "Sorry, there was an unexpected error. Please try again later."))
      }
    }

    GlobalState.user.foreach { user =>
      userValue.update(_.copy(username = user.name))
    }

    div(
      Styles.flex,
      overflow.auto,
      div(
        margin.auto, // horizontal and vertical centering
        onSubmit.foreach(_.preventDefault()),
        padding := "10px",
        maxWidth := "400px",
        form(
          Styles.flex,
          flexDirection.column,

          onDomMount foreach { e => element = e.asInstanceOf[dom.html.Form] },
          onSubmit.preventDefault.discard, // prevent reloading the page on form submit

          forgotPasswordMode.map {
            case false => h2(header)
            case true => VDomModifier(
              h2("Password Reset"),
              h5("Enter your email address and we will send you a link to reset your password.")
            )
          },

          needUserName.ifTrue[VDomModifier](div(
            cls := "ui fluid input",
            input(
              placeholder := "Username",
              value <-- userValue.map(_.username),
              tpe := "text",
              required := true,
              display.block,
              margin := "auto",
              onInput.value foreach { str => userValue.update(_.copy(username = str.trim)) },
              onDomMount.asHtml --> inNextAnimationFrame[dom.html.Element] { e => if(userValue.now.username.isEmpty) e.focus() }
            )
          )),

          div(
            cls := "ui fluid input",
            input(
              placeholder := "Email",
              value <-- userValue.map(_.email),
              tpe := "email",
              required := true,
              attr("autocomplete") := "username",
              display.block,
              margin := "auto",
              onInput.value foreach { str => userValue.update(_.copy(email = str)) },
              onDomMount.asHtml --> inNextAnimationFrame[dom.html.Element] { e => if(!needUserName || userValue.now.username.nonEmpty) e.focus() }
            )
          ),

          forgotPasswordMode.map {
            case true => VDomModifier.ifTrue(showPasswordReset)(div(
              marginTop := "10px",
              Styles.flex,
              justifyContent.spaceBetween,
              div(
                cls := "ui button",
                s"Go back to $submitText",
                cursor.pointer,
                onClick.stopPropagation.use(false) --> forgotPasswordMode
              ),
              div(
                disabled <-- userValue.map(_.email.isEmpty),
                cls := "ui button primary",
                "Send Password Reset Mail",
                cursor.pointer,
                onClick.stopPropagation.foreach {
                  if (FormValidator.reportValidity(element)) {
                    Client.auth.resetPassword(EmailAddress(userValue.now.email)).foreach {
                      case true =>
                        errorMessageHandler.onNext(StatusMessage.Success("Password Reset Mail was sent out", "Check your email for a link to reset your password"))
                        forgotPasswordMode() = false
                      case false =>
                        errorMessageHandler.onNext(StatusMessage.Error("Password Reset Mail failed", "Sorry, we cannot find this email address."))
                    }
                  }
                }
              )
            ))
            case false => VDomModifier(
              div(
                cls := "ui fluid input",
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
                  onDomMount.asHtml --> inNextAnimationFrame[dom.html.Element] { e => if((!needUserName || userValue.now.username.nonEmpty) && userValue.now.email.nonEmpty) e.focus() }
                )
              ),
              discardContentMessage,
              button(
                cls := "ui fluid primary button",
                submitText,
                display.block,
                margin := "auto",
                marginTop := "5px",
                onClick foreach actionSink()
              ),
              VDomModifier.ifTrue(showPasswordReset)(b(
                alignSelf.flexEnd,
                margin := "4px",
                textDecoration := "underline",
                "Forgot password?",
                cursor.pointer,
                onClick.stopPropagation.use(true) --> forgotPasswordMode
              )),
            )
          },

          errorMessageHandler.map {
            case StatusMessage.Error(header, errorMessage) => div(
              cls := "ui negative message",
              div(cls := "header", header),
              p(errorMessage)
            )
            case StatusMessage.Success(header, errorMessage) => div(
              cls := "ui positive message",
              div(cls := "header", header),
              p(errorMessage)
            )
          },

          div(cls := "ui divider"),
          h3(alternativeHeader, textAlign := "center"),
          GlobalState.urlConfig.map { cfg =>
            div(
              onClick.use(cfg.focus(alternativeView)) --> GlobalState.urlConfig,
              cls := "ui fluid button",
              alternativeText,
              display.block,
              margin := "auto",
              cursor.pointer
            )
          },

          h4("Having Problems with Login or Signup?", textAlign := "center", marginTop := "40px"),
          div("Please contact ", woostTeamEmailLink, textAlign := "center"),
          marginBottom := "20px",
        )
      )
    )
  }

  def discardContentMessage(implicit ctx:Ctx.Owner) = {
    Rx {
      GlobalState.user() match {
        // User.Implicit user means, that the user already created content, else it would be User.Assumed.
        case AuthUser.Implicit(_, name, _, _) => UI.message(
          // msgType = "warning",
          header = Some("Discard created content?"),
          content = Some(VDomModifier(
            span("You already created content as an unregistered user. If you login or register, the content will be moved into your account. If you don't want to keep it you can "),
            a(
              href := "#",
              color := "#de2d0e",
              marginLeft := "auto",
              "discard all content now",
              onClick.preventDefault foreach {
                if(dom.window.confirm("This will delete all your content, you created as an unregistered user. Do you want to continue?")) {
                  GlobalState.urlConfig.update(cfg => cfg.copy(redirectTo = None, pageChange = PageChange(Page.empty))) // clear page, so we do not access an old page anymore
                  Client.auth.logout()
                }
                ()
              }
              ),
            "."
          ))
      )
        case _ => VDomModifier.empty

      }
    },
  }

  def login(implicit ctx: Ctx.Owner) = {
    apply(
      header = "Login",
      submitText = "Login",
      needUserName = false,
      submitAction = {userValue =>
        Client.auth.login(EmailAddress(userValue.email), Password(userValue.password)).map {
          case AuthResult.BadPassword  => Some("Wrong password.")
          case AuthResult.BadEmail     => Some("No account with this email address exists. Please check spelling and capitalization.")
          case AuthResult.InvalidEmail => Some("Email address is invalid")
          case AuthResult.Success      =>
            FeatureState.use(Feature.Login)
            Segment.trackSignedIn()
            None
        }
      },
      alternativeHeader = "New to Woost?",
      alternativeView = View.Signup,
      alternativeText = "Create an account",
      autoCompletePassword = "current-password",
      showPasswordReset = true
    )
  }

  def signup(implicit ctx: Ctx.Owner) = {
    apply(
      header = "Create an account",
      submitText = "Signup",
      needUserName = true,
      submitAction = {userValue =>
        Client.auth.register(name = userValue.username, email = EmailAddress(userValue.email), password = Password(userValue.password)).map {
          case AuthResult.BadPassword  => Some("Insufficient password")
          case AuthResult.BadEmail     => Some("Email address already taken")
          case AuthResult.InvalidEmail => Some("Email address is invalid")
          case AuthResult.Success      =>
            FeatureState.use(Feature.Signup)
            Segment.trackSignedUp()
            None
        }
      },
      alternativeHeader = "Already have an account?",
      alternativeView = View.Login,
      alternativeText = "Login",
      autoCompletePassword = "new-password",
      showPasswordReset = false
    )
  }
}
