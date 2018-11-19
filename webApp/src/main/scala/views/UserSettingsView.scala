package wust.webApp.views

import fontAwesome.{IconDefinition, freeBrands, freeSolid}
import googleAnalytics.Analytics
import monix.reactive.Observable
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.{Authentication, PluginUserAuthentication}
import wust.css.Styles
import wust.ids._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import Elements._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import cats.effect.IO

import scala.util.control.NonFatal
import scala.concurrent.Future

object UserSettingsView {

  def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      padding := "20px",
      Rx {
        val user = state.user()
        VDomModifier(
          header(user)(marginBottom := "50px"),
          accountSettings(user).apply(marginBottom := "50px"),
          pluginSettings(user),
        )
      }
    )
  }

  private def pluginSettings(user: UserInfo)(implicit ctx: Ctx.Owner): VNode = div(
    b("Plugins:"),
    div(
      marginLeft := "10px",
      Observable.fromFuture(slackButton(user))
    )
  )

  private def accountSettings(user: UserInfo)(implicit ctx: Ctx.Owner): VNode = div(
    width := "200px",
    b("Account:"),
    div(
      marginLeft := "10px",
      changePassword(user)
    )
  )

  private def changePassword(user: UserInfo)(implicit ctx: Ctx.Owner) = {
    val password = Handler.unsafe[String]
    val successHandler = Handler.unsafe(true)
    val clearHandler = successHandler.collect { case true => "" }
    val actionSink = { password: String =>
      if (password.nonEmpty) Client.auth.changePassword(password).foreach(successHandler.onNext)
    }
    VDomModifier(
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
      successHandler.map {
        case true => VDomModifier.empty
        case false => div(
          cls := "ui negative message",
          div(cls := "header", s"Changing password failed.")
        )
      },
      button(
        "Change Password",
        cls := "ui fluid primary button",
        display.block,
        onClick(password) foreach actionSink
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
