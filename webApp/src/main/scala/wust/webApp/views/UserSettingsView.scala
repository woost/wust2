package wust.webApp.views

import fontAwesome.IconDefinition
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import outwatch.reactive.handler._
import rx._
import wust.api._
import wust.css.Styles
import wust.graph._
import wust.ids.{ Feature, _ }
import wust.webApp._
import wust.webApp.jsdom.FormValidator
import wust.webApp.state.{ FeatureState, GlobalState }
import wust.webApp.views.Components._
import wust.webUtil.Elements._
import wust.webUtil.UI.ToastLevel
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Elements, UI }

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
import wust.facades.segment.Segment

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

  def apply(implicit owner: Ctx.Owner): VNode = {
    val userDetail = Var[Option[UserDetail]](None)

    div(
      padding := "20px",
      overflow.auto,
      Rx {
        val user = GlobalState.user()
        VDomModifier(
          header(user, userDetail).apply(marginBottom := "50px"),
          UI.accordion(
            Seq(
              accordionEntry("Account Settings", accountSettings(user, userDetail), active = true),
              accordionEntry(span(i(Icons.plugin), b(" Plugins")), pluginSettings(user)),
              accordionEntry(span(i(Icons.files), b(" Uploaded Files")), uploadSettings(user)),
              accordionEntry(b("§ Legal Information"), LegalNotice.information),
            ),
            styles = "fluid",
            exclusive = false,
          )
        )
      }
    )
  }

  private def accordionEntry(title: VDomModifier, content: VDomModifier, active: Boolean = false): UI.AccordionEntry = {
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

  private def uploadSettings(ser: UserInfo)(implicit ctx: Ctx.Owner): VNode = {
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
                  Segment.trackError("Cannot list file uploads for user", t.getMessage())
                  UI.toast("Sorry, the file-upload service is currently unreachable. Please try again later!")
              }
            }
          )
        },
        fileUploads.map(_.map { allUploads =>
          val fullSize = allUploads.map(_.size).sum
          val fullSizeMb = fullSize.toDouble / 1024 / 1024
          val freeSize = FileUploadConfiguration.maxUploadBytesPerUser - fullSize
          val maxSizeMb = FileUploadConfiguration.maxUploadBytesPerUser / 1024 / 1024

          VDomModifier(
            div(f"Used ${fullSizeMb}%1.1f MB out of ${maxSizeMb} MB.", if (freeSize < FileUploadConfiguration.maxUploadBytesPerFile) color := "tomato" else VDomModifier.empty),
            allUploads.map {
              case UploadedFile(nodeId, size, file) =>
                div(
                  marginTop := "5px",
                  marginBottom := "5px",
                  backgroundColor := "rgba(255, 255, 255, 0.3)",
                  Styles.flex,
                  flexDirection.row,
                  justifyContent.spaceBetween,
                  alignItems.center,
                  UploadComponents.renderUploadedFile(nodeId, file),
                  div(f"(${size.toDouble / 1024 / 1024}%1.1f MB)"),
                  button(cls := "ui negative button", "Delete file", onClick.foreach {
                    val shouldDelete = dom.window.confirm("Are you sure you want to delete this file upload?")
                    if (shouldDelete) {
                      Client.api.deleteFileUpload(file.key.split("/")(1)).foreach { success =>
                        if (success) fileUploads.update(_.map(_.filterNot(_.file.key == file.key)))
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
    getEnabledOAuthClientServices().map { enabledServices =>
      OAuthClientService.all.map { service =>
        div(
          margin := "10px 30px 10px 0px",
          minWidth := "200px",
          oAuthClientServiceButton(user, service, enabledServices(service))
        )
      }
    },
    div(
      margin := "10px 30px 10px 0px",
      minWidth := "200px",
      slackButton(user)
    ),
  )

  private def accountSettings(user: UserInfo, userDetail: Var[Option[UserDetail]])(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      flexWrap.wrap,
      div(
        margin := "10px 30px 10px 0px",
        minWidth := "200px",
        maxWidth := "400px",
        changeEmail(user, userDetail)
      ),
      div(
        margin := "10px 30px 10px 0px",
        minWidth := "200px",
        maxWidth := "400px",
        changePassword(user)
      )
    )
  }

  private def changeEmail(user: UserInfo, userDetail: Var[Option[UserDetail]])(implicit ctx: Ctx.Owner) = {
    val detailsUnavailable = Var(true)
    Client.auth.getUserDetail(user.id).onComplete {
      case Success(detail) =>
        Var.set(
          detailsUnavailable -> false,
          userDetail -> detail
        )
      case Failure(err) =>
        scribe.info("Cannot get UserDetail", err)
        Segment.trackError("Cannot get UserDetail", err.getMessage())
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
              UI.toast("Successfully changed email address. Please check your email to verify the address.", level = UI.ToastLevel.Success)
            }

            val error = if (success) None else Some("Email address already taken")
            errorHandler.onNext(error)
          case Failure(err) =>
            errorHandler.onNext(Some(s"Unexpected error: $err"))
            Segment.trackError("Change Email Error", err.getMessage())
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
            if (FormValidator.reportValidity(element)) {
              actionSink(email)
            }
          }
        )
      ),
      errorHandler.map {
        case None => VDomModifier(userDetail.map(_.collect {
          case UserDetail(userId, Some(email), false) => UI.message(
            header = Some("Last Step: Verify your email address"),
            content = Some(VDomModifier(
              div(
                s"Your email address is not verified yet. Check your emails (and spam) for the verification email. Or ",
                a(
                  href := "#",
                  marginLeft := "auto",
                  "resend verification email",
                  onClick.preventDefault foreach {
                    Client.auth.resendEmailVerification(userId).foreach {
                      case () =>
                        UI.toast(s"Sent verification email to '${email}'", level = UI.ToastLevel.Success)
                    }
                  }
                ),
                ". If you misspelled it, simply enter your correct email address in the field and click the ", b("Change email"), " button again."
              )
            ))
          )
        }))
        case Some(problem) =>
          div(
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
          if (FormValidator.reportValidity(element)) {
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
          onEnter.value foreach actionSink
        )
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
          if (element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
            actionSink(email)
          }
        }
      )
    )
  }

  private def header(user: AuthUser, userDetail: Var[Option[UserDetail]])(implicit ctx: Ctx.Owner): VNode = {
    val nodeUser = user.toNode
    val editMode = Var(false)
    div(
      Styles.flex,
      alignItems.center,
      Avatar.user(nodeUser, size = "50px", enableDrag = false)(
        Styles.flexStatic,
        padding := "4px",
      ),
      editableNode(nodeUser, editMode).apply(cls := "username", marginLeft := "20px", marginBottom := "0px", fontSize := "30px", alignItems.center, cls := "enable-text-selection"),
      button(
        cls := "ui button primary tiny",
        marginLeft := "20px",
        "Edit username",
        onClick.use(true) --> editMode
      ),

      Rx {
        if (userDetail().exists(_.verified)) {
          div(
            marginLeft := "20px",

            Styles.flex,
            alignItems.center,

            div(
              marginRight := "10px",

              div("Upload your own Avatar"),

              nodeUser.data.imageFile.map { _ =>
                div(
                  "Or reset your Avatar",
                  textDecoration.underline,
                  onClickDefault.useLazy(GraphChanges(addNodes = Array(nodeUser.copy(data = nodeUser.data.copy(imageFile = None))))) --> GlobalState.eventProcessor.changes
                )
              }
            ),

            div(
              UploadComponents.uploadField(acceptType = Some("image/*")).transform(_.concatMapAsync(file => file.uploadKey).collect {
                case Some(key) =>
                  GraphChanges(addNodes = Array(nodeUser.copy(data = nodeUser.data.copy(imageFile = Some(key)))))
              }) --> GlobalState.eventProcessor.changes,
            )
          )
        } else {
          div(
            margin := "15px",
            opacity := 0.5,
            maxWidth := "200px",
            "You can upload a profile picture once you have signed up."
          )
        }
      }
    )
  }

  private def showPluginAuth(userId: UserId, token: Authentication.Token) = {
    Client.slackApi.getAuthentication(userId, token).map{
      case Some(pluginAuth) =>
      case _                =>
    }
  }

  private def genConnectButton(icon: IconDefinition, platformName: String)(isActive: Boolean) = {

    val modifiers = if (isActive) {
      List[VDomModifier](
        cls := "ui button green",
        div(s"Synchronized with $platformName"),
        onClick foreach (linkWithSlack()),
      //        onClick foreach(showPluginAuth()),
      )
    } else {
      List[VDomModifier](
        cls := "ui button",
        div(s"Sync with $platformName now"),
        onClick foreach (linkWithSlack()),
      )
    }

    div(
      button(
        div(
          Styles.flex,
          justifyContent.center,
          fontSize := "25px",
          WoostLogoComponents.woostIcon(marginRight := "10px"),
          (Icons.exchange: VNode) (marginRight := "10px"),
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
      "Enable slack plugin",
      onClick foreach {
        FeatureState.use(Feature.EnableSlackPlugin)
      },
      onClick foreach {
        UI.toast("Thanks for your interest in the slack plugin. We counted a vote for you to prioritize it.")
      }
    ),
    div(
      fontSize := "10px",
      textAlign.center,
      "Currently inactive"
    ),
  )

  private def slackButton(user: UserInfo): Future[VNode] = {
    val syncButton = genConnectButton(Icons.slack, "Slack") _
    Client.slackApi.isAuthenticated(user.id)
      .map(activated => syncButton(activated))
      .recover {
        case NonFatal(e) =>
          scribe.warn("Failed to check authentication from Slack api", e)
          slackPlaceholder
      }
  }

  private def getEnabledOAuthClientServices(): Future[Set[OAuthClientService]] = {
    Client.auth.getOAuthClients().recover { case _ => Set.empty[OAuthClientService] }.map(_.toSet)
  }

  private def oAuthClientServiceButton(user: UserInfo, service: OAuthClientService, initiallyEnabled: Boolean)(implicit ctx: Ctx.Owner): VNode = {
    val isEnabled = Var(initiallyEnabled)
    div(
      Styles.flex,
      flexDirection.column,
      button(
        Rx {
          if (isEnabled()) VDomModifier(
            cls := "ui button",
            s"Disable ${service.identifier} plugin",
          )
          else VDomModifier(
            cls := "ui button green",
            s"Enable ${service.identifier} plugin",
          )
        },
        onClick foreach { _ =>
          if (isEnabled.now)
            Client.auth.deleteOAuthClient(service).foreach { success =>
              if (success) isEnabled() = false
            }
          else Client.auth.getOAuthConnectUrl(service).onComplete {
            case Success(Some(redirectUrl)) => dom.window.location.href = redirectUrl
            case _                          => UI.toast(s"Sorry, the OAuth Service for '${service.identifier}' is currently not available. Please try again later.", level = ToastLevel.Error)
          }
          Segment.trackEvent("Enabled Plugin", js.Dynamic.literal(plugin = service.identifier))
        }
      ),
      div(
        fontSize := "10px",
        textAlign.center,
        Rx {
          if (isEnabled()) "Currently active" else "Currently inactive"
        }
      )
    )
  }

  private def linkWithGithub() = {
    Client.auth.issuePluginToken().foreach { auth =>
      scribe.info(s"Generated plugin token: $auth")
      val connUser = Client.githubApi.connectUser(auth.token)
      connUser foreach {
        case Some(url) =>
          org.scalajs.dom.window.location.href = url
        case None =>
          scribe.info(s"Could not connect user: $auth")
      }
    }
  }

  private def linkWithGitter() = {
    Client.auth.issuePluginToken().foreach { auth =>
      scribe.info(s"Generated plugin token: $auth")
      val connUser = Client.gitterApi.connectUser(auth.token)
      connUser foreach {
        case Some(url) =>
          org.scalajs.dom.window.location.href = url
        case None =>
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
        case None =>
          scribe.info(s"Could not connect user: $auth")
      }
    }
  }

  private def listSettings(user: UserInfo): VNode = {

    // TODO: Show button if not linked, else show linked data
    div(
      p(s"UserId: ${user.id.toString}"),
      p(s"Username: ${user.name}", cls := "username"),
      div(
        p("Connect Woost with a Service"),

        button(
          "Link with GitHub",
          onClick foreach (linkWithGithub())
        ),
        br(),

        button(
          "Link with Gitter",
          onClick foreach (linkWithGitter())
        ),
        br(),

        button(
          "Link with Slack",
          onClick foreach (linkWithSlack())
        ),

      )
    )
  }
}
