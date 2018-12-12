package wust.webApp.views

import fontAwesome.{IconDefinition, freeBrands, freeSolid}
import googleAnalytics.Analytics
import monix.reactive.Observable
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api._
import wust.css.Styles
import wust.ids._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import Elements._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import cats.effect.IO
import org.scalajs.dom

import scala.util.control.NonFatal
import scala.concurrent.Future
import scala.scalajs.js
import scala.util.{Failure, Success}

object UserSettingsView {

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      padding := "20px",
      Rx {
        val user = state.user()
        VDomModifier(
          header(user)(marginBottom := "50px"),
          accountSettings(state, user).apply(marginBottom := "50px"),
          pluginSettings(user).apply(marginBottom := "50px"),
          uploadSettings(state, user)
        )
      }
    )
  }


  private def uploadSettings(state: GlobalState, ser: UserInfo)(implicit ctx: Ctx.Owner): VNode = {
    val fileUploads = Var[Option[Seq[UploadedFile]]](None)
    div(
      b("Uploaded Files:"),
      br,
      div(
        marginLeft := "10px",
        width := "500px",
        Rx {
          if (fileUploads().isDefined) VDomModifier.empty
          else button(cls := "ui button", "Show all", onClick.foreach {
            Client.api.getUploadedFiles.onComplete {
              case Success(files) =>
                fileUploads() = Some(files)
              case Failure(t) =>
                scribe.warn("Cannot list file uploads for user", t)
                UI.toast("Sorry, the file-upload service is currently unreachable. Please try again later!")
            }
          })
        },
        fileUploads.map(_.map { allUploads =>
          val fullSize = allUploads.map(_.size).sum
          val fullSizeMb = fullSize.toDouble / 1024 / 1024
          val freeSize = FileUploadConfiguration.maxUploadBytesPerUser - fullSize
          val maxSizeMb = FileUploadConfiguration.maxUploadBytesPerUser / 1024 / 1024

          VDomModifier(
            div(f"Used ${fullSizeMb}%1.1f MB out of ${maxSizeMb} MB.", if (freeSize < FileUploadConfiguration.maxUploadBytesPerFile) color := "tomato" else VDomModifier.empty),
            allUploads.map { case UploadedFile(nodeId, size, file) =>
              div(
                marginTop := "5px",
                marginBottom := "5px",
                backgroundColor := "rgba(255, 255, 255, 0.3)",
                Styles.flex,
                flexDirection.row,
                justifyContent.spaceBetween,
                alignItems.center,
                Components.renderUploadedFile(state, nodeId, file),
                div(f"(${size.toDouble / 1024 / 1024}%1.1f MB)"),
                button(cls := "ui negative button", "Delete file", onClick.foreach {
                  val shouldDelete = dom.window.confirm("Are you sure you want to delete this file upload?")
                  if(shouldDelete) {
                    Client.api.deleteFileUpload(file.key.split("/")(1)).foreach { success =>
                      if(success) fileUploads.update(_.map(_.filterNot(_.file.key == file.key)))
                    }
                  }
                })
              )
            }
          )
        })
      )
    )
  }

  private def pluginSettings(user: UserInfo)(implicit ctx: Ctx.Owner): VNode = div(
    b("Plugins:"),
    br,
    div(
      marginLeft := "10px",
      Observable.fromFuture(slackButton(user))
    )
  )

  private def accountSettings(state: GlobalState, user: UserInfo)(implicit ctx: Ctx.Owner): VNode = {

    div(
      width := "300px",
      b("Account settings:"),
      br,
      changeEmail(state).apply(
        marginLeft := "10px",
      ),
      br,
      changePassword(user).apply(
        marginLeft := "10px",
      )
    )
  }

  private def changeEmail(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val userDetail = Var[Option[UserDetail]](None)
    val detailsUnavailable = Var(true)
    state.user.foreach { user =>
      Client.auth.getUserDetail(user.id).onComplete {
        case Success(detail) =>
          Var.set(
            detailsUnavailable -> false,
            userDetail -> detail
          )
        case Failure(err) =>
          scribe.info("Cannot get UserDetail", err)
          Var.set(
            detailsUnavailable -> false,
            userDetail -> None
          )
      }
    }

    var element: dom.html.Form = null
    val email = Handler.unsafe[String]
    val errorHandler = Handler.unsafe[Option[String]](None)
    val actionSink = { email: String =>
      userDetail.now match {
        case Some(detail) if email.nonEmpty && detail.email.fold(true)(_ != email) => Client.auth.updateUserEmail(detail.userId, email).onComplete {
          case Success(success) =>
            if (success) {
              userDetail() = Some(detail.copy(email = Some(email), verified = false))
              UI.toast("Successfully changed Email address. Please check your email inbox to verify the address. ", level = UI.ToastLevel.Success)
            }

            val error = if (success) None else Some("Email address already taken")
            errorHandler.onNext(error)
          case Failure(err) =>
            errorHandler.onNext(Some(s"Unexpected error: $err"))
        }
        case _ => ()
      }
    }
    form(
      onDomMount.foreach(e => element = e.asInstanceOf[dom.html.Form]),
      onSubmit.foreach(_.preventDefault()),
      div(
        cls := "ui fluid input",
        input(
          placeholder := "Email address",
          tpe := "email",
          disabled <-- detailsUnavailable,
          value <-- userDetail.map(_.fold("")(_.email.getOrElse(""))),
          onChange.value --> email,
          onEnter.value foreach { email =>
            if (element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
              actionSink(email)
            }
          }
        )
      ),
      errorHandler.map {
        case None => VDomModifier(userDetail.map(_.collect { case UserDetail(userId, Some(email), false) => div(
          cls := "ui yellow message",
          div(
            cls := "header", s"Email address is unverified. Check your inbox for the verification email. Or ",
            a(
              href := "#",
              marginLeft := "auto",
              "resend verification email",
              onClick.preventDefault foreach {
                Client.auth.resendEmailVerification(userId).foreach { case () =>
                  UI.toast(s"Send out verification email to '${email}'", level = UI.ToastLevel.Success)
                }
              }
            )
          )
        )}))
        case Some(problem) => div(
          cls := "ui negative message",
          div(cls := "header", problem)
        )
      },
      button(
        "Change Email",
        cls := "ui fluid primary button",
        display.block,

        onClick(email).foreach { email =>
          if (element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
            actionSink(email)
          }
        }
      )
    )
  }

  private def changePassword(user: UserInfo)(implicit ctx: Ctx.Owner) = {
    var element: dom.html.Form = null
    val password = Handler.unsafe[String]
    val errorHandler = Handler.unsafe[Option[String]]
    val clearHandler = errorHandler.collect { case None => "" }
    val actionSink = { password: String =>
      if (password.nonEmpty) Client.auth.changePassword(password).onComplete {
        case Success(success) =>
          if (success) {
            UI.toast("Successfully changed password", level = UI.ToastLevel.Success)
          }

          val error = if (success) None else Some("Something went wrong")
          errorHandler.onNext(error)
        case Failure(err) =>
          errorHandler.onNext(Some(s"Unexpected error: $err"))
      }
    }
    form(
      onDomMount.foreach(e => element = e.asInstanceOf[dom.html.Form]),
      onSubmit.foreach(_.preventDefault()),
      div(
        emitter(clearHandler) --> password,
        cls := "ui fluid input",
        input(
          placeholder := "New password",
          tpe := "password",
          value <-- clearHandler,
          onChange.value --> password,
          onEnter.value foreach actionSink)
      ),
      errorHandler.map {
        case None => VDomModifier.empty
        case Some(problem) => div(
          cls := "ui negative message",
          div(cls := "header", problem)
        )
      },
      button(
        "Change Password",
        cls := "ui fluid primary button",
        display.block,
        onClick(password).foreach { email =>
          if(element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
            actionSink(email)
          }
        }
      )
    )
  }

  private def header(user: UserInfo): VNode = {
    div(
      Styles.flex,
      alignItems.center,
      Avatar.user(user.id)(
        cls := "avatar",
        Styles.flexStatic,
        width := "50px",
        height := "50px",
        padding := "4px",
      ),
      div(marginLeft := "20px", user.name, fontSize := "30px")
    )
  }

  private def showPluginAuth(userId: UserId, token: Authentication.Token) = {
    Client.slackApi.getAuthentication(userId, token).map{
      case Some(pluginAuth) =>
      case _                  =>
    }
  }

  private def genConnectButton(icon: IconDefinition, platformName: String)(isActive: Boolean) = {

    val modifiers = if(isActive){
      List(
        cls := "ui button green",
        div(s"Synchronized with $platformName"),
        onClick foreach(linkWithSlack()),
//        onClick foreach(showPluginAuth()),
      )
    } else {
      List(
        cls := "ui button",
        div(s"Sync with $platformName now"),
        onClick foreach(linkWithSlack()),
      )
    }

    div(
      button(
        div(
          Styles.flex,
          justifyContent.center,
          fontSize := "25px",
          woostIcon(marginRight := "10px"),
          (freeSolid.faExchangeAlt: VNode) (marginRight := "10px"),
          (icon: VNode),
          marginBottom := "5px",
        ),
        modifiers,
      ),
    )
  }

  private def slackPlaceholder = div(
    "Slack Plugin not enabled",
  )

  private def slackButton(user: UserInfo): Future[VNode] = {
    val syncButton = genConnectButton(freeBrands.faSlack, "Slack") _
    Client.slackApi.isAuthenticated(user.id)
      .map(activated => syncButton(activated))
      .recover { case NonFatal(e) =>
        scribe.warn("Failed to check authentication from slack api", e)
        slackPlaceholder
      }
  }

  def linkWithGithub() = {
    Client.auth.issuePluginToken().foreach { auth =>
      scribe.info(s"Generated plugin token: $auth")
      val connUser = Client.githubApi.connectUser(auth.token)
      connUser foreach {
        case Some(url) =>
          org.scalajs.dom.window.location.href = url
        case None      =>
          scribe.info(s"Could not connect user: $auth")
      }
    }
  }

  def linkWithGitter() = {
    Client.auth.issuePluginToken().foreach { auth =>
      scribe.info(s"Generated plugin token: $auth")
      val connUser = Client.gitterApi.connectUser(auth.token)
      connUser foreach {
        case Some(url) =>
          org.scalajs.dom.window.location.href = url
        case None      =>
          scribe.info(s"Could not connect user: $auth")
      }
    }
  }

  def linkWithSlack() = {
    Client.auth.issuePluginToken().foreach { auth =>
      scribe.info(s"Generated plugin token: $auth")
      val connUser = Client.slackApi.connectUser(auth.token)
      connUser foreach {
        case Some(url) =>
          scribe.info(s"Received url: $url")
          org.scalajs.dom.window.location.href = url
        case None      =>
          scribe.info(s"Could not connect user: $auth")
      }
    }
  }

  private def listSettings(user: UserInfo): VNode = {

    // TODO: Show button if not linked, else show linked data
    div(
      p(s"UserId: ${ user.id.toString }"),
      p(s"Username: ${ user.name }"),
      div(
        p("Connect Woost with a Service"),

        button(
          "Link with GitHub",
          onClick foreach(linkWithGithub())),
        br(),

        button(
          "Link with Gitter",
          onClick foreach(linkWithGitter())
        ),
        br(),

        button(
          "Link with Slack",
          onClick foreach(linkWithSlack())
        ),

      )
    )
  }
}
