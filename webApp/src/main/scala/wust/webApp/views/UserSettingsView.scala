package wust.webApp.views

import fontAwesome.IconDefinition
import monix.reactive.Observable
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api._
import wust.css.Styles
import wust.facades.googleanalytics.Analytics
import wust.ids._
import wust.webApp._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, UI}

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object UserSettingsView {

  object LegalNotice {
    @inline private val legalEntry = (subject: String, link: String) => div(
        a(
          subject,
          href := s"https://woost.space/$link",
          Elements.safeTargetBlank
        )
    )
    private val privacy = legalEntry(
      "Privacy Policy",
      "privacy-policy",
    )
    private val terms = legalEntry(
      "Terms of Service",
      "terms",
    )
    private val imprint = legalEntry(
      "Imprint",
      "imprint",
    )

    val information = div(
      Styles.flex,
      Styles.flexStatic,
      flexDirection.column,
      privacy,
      terms,
      imprint
    )
  }

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      padding := "20px",
      overflow.auto,
      Rx {
        val user = state.user()
        VDomModifier(
          header(state, user).apply(marginBottom := "50px"),
          UI.accordion(
            Seq(
              accordionEntry("Account Settings", accountSettings(state, user), active = true),
              accordionEntry( span( i(Icons.plugin), b(" Plugins") ), pluginSettings(user)),
              accordionEntry( span( i(Icons.files), b(" Uploaded Files") ), uploadSettings(state, user)),
              accordionEntry( b("§ Legal Information" ), LegalNotice.information),
            ),
            styles = "fluid",
            exclusive = false,
          )
        )
      }
    )
  }

  def accordionEntry(title: VDomModifier, content: VDomModifier, active:Boolean = false): UI.AccordionEntry = {
    UI.AccordionEntry(
      title = VDomModifier(
        Styles.flexStatic,
        marginTop := "4px",
        title,
        ),
      content = VDomModifier(
        margin := "4px 4px 12px 20px",
        padding := "0px",
        content
      ),
      active = active
    )
  }

  private def uploadSettings(state: GlobalState, ser: UserInfo)(implicit ctx: Ctx.Owner): VNode = {
    val fileUploads = Var[Option[Seq[UploadedFile]]](None)
    div(
      margin := "10px 30px 10px 0px",
      div(
        width := "500px",
        Rx {
          if (fileUploads().isDefined) VDomModifier.empty
          else button(
            cls := "ui button",
            marginTop := "6px",
            "Show all",
            onClick.foreach {
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
                UploadComponents.renderUploadedFile(state, nodeId, file),
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
    Styles.flex,
    flexWrap.wrap,
    div(
      margin := "10px 30px 10px 0px",
      minWidth := "200px",
      Observable.fromFuture(slackButton(user))
    )
  )

  private def accountSettings(state: GlobalState, user: UserInfo)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      flexWrap.wrap,
      div(
        margin := "10px 30px 10px 0px",
        minWidth := "200px",
        changeEmail(state, user)
      ),
      div(
        margin := "10px 30px 10px 0px",
        minWidth := "200px",
        changePassword(user)
      )
    )
  }

  private def changeEmail(state: GlobalState, user: UserInfo)(implicit ctx: Ctx.Owner) = {
    val userDetail = Var[Option[UserDetail]](None)
    val detailsUnavailable = Var(true)
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

    var element: dom.html.Form = null
    val email = Handler.unsafe[String]
    val errorHandler = Handler.unsafe[Option[String]](None)
    val actionSink = { email: String =>
      userDetail.now match {
        case Some(detail) if email.nonEmpty && detail.email.fold(true)(_ != email) => Client.auth.updateUserEmail(detail.userId, email).onComplete {
          case Success(success) =>
            if (success) {
              userDetail() = Some(detail.copy(email = Some(email), verified = false))
              UI.toast("Successfully changed email address. Please check your email inbox to verify the address.", level = UI.ToastLevel.Success)
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
          cls := "ui warning message",
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
        "Change email",
        marginTop := "6px",
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

  //TODO hidden field for username so password manager can interpret this: https://www.chromium.org/developers/design-documents/create-amazing-password-forms
  private def changePassword(user: UserInfo)(implicit ctx: Ctx.Owner) = {
    var element: dom.html.Form = null
    val password = Handler.unsafe[String]
    val errorHandler = Handler.unsafe[Option[String]]
    val clearHandler = errorHandler.collect { case None => "" }
    val actionSink = { password: String =>
      if (password.nonEmpty) Client.auth.changePassword(Password(password)).onComplete {
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
          autoComplete := "new-password",
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
        "Change password",
        cls := "ui fluid primary button",
        marginTop := "6px",
        display.block,
        onClick(password).foreach { email =>
          if(element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
            actionSink(email)
          }
        }
      )
    )
  }

  private def header(state: GlobalState, user: AuthUser)(implicit ctx: Ctx.Owner): VNode = {
    val editMode = Var(false)
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
      editableNode(state, user.toNode, editMode).apply(marginLeft := "20px", marginBottom := "0px", fontSize := "30px", alignItems.center, cls := "enable-text-selection"),
      button(
        cls := "ui button primary tiny",
        marginLeft := "20px",
        "Edit username",
        onClick(true) --> editMode
      )
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
          WoostLogoComponents.woostIcon(marginRight := "10px"),
          (Icons.sync: VNode) (marginRight := "10px"),
          (icon: VNode),
          marginBottom := "5px",
        ),
        modifiers,
      ),
    )
  }

  private def slackPlaceholder = div(
    Styles.flex,
    flexDirection.column,
    button(
      cls := "ui button green",
      marginTop := "6px",
      "Enable Slack plugin",
      onClick foreach {
        Analytics.sendEvent("slack", "enableplugin")
      },
      onClick foreach {
        UI.toast("Thanks for your interest in the slack plugin. It will be available soon.")
      }
    ),
      div(
      fontSize := "10px",
      textAlign.center,
      "Slack plugin not enabled"
    ),
  )

  private def slackButton(user: UserInfo): Future[VNode] = {
    val syncButton = genConnectButton(Icons.slack, "Slack") _
    Client.slackApi.isAuthenticated(user.id)
      .map(activated => syncButton(activated))
      .recover { case NonFatal(e) =>
        scribe.warn("Failed to check authentication from Slack api", e)
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