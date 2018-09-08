package wust.webApp.views

import fontAwesome.{IconDefinition, freeBrands, freeSolid}
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
          accountSettings(user),
          Observable.fromFuture(slackButton(user)),
        )
      }
    )
  }

  private def accountSettings(user: UserInfo)(implicit ctx: Ctx.Owner): VNode = div(
    width := "200px",
    b("Account settings"),
    br(),
    changePassword(user)
  )

  private def changePassword(user: UserInfo)(implicit ctx: Ctx.Owner) = for {
    password <- Handler.create[String]
    successHandler = Var(true)
    actionSink = sideEffect[String] { password =>
      Client.auth.changePassword(password).foreach(successHandler() = _)
    }
    modifiers <- VDomModifier(
      div(
        cls := "ui fluid input",
        input(
          placeholder := "New password",
          tpe := "password",
          value <-- successHandler.collect { case true => "" },
          onChange.value --> password,
          onEnter.value --> actionSink)
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
        onClick(password) --> actionSink
      )
    )
  } yield modifiers

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
        onClick --> sideEffect(linkWithSlack()),
//        onClick --> sideEffect(showPluginAuth()),
      )
    } else {
      List(
        cls := "ui button",
        div(s"Sync with $platformName now"),
        onClick --> sideEffect(linkWithSlack()),
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

  private def slackButton(user: UserInfo): Future[VNode] = {
    val syncButton = genConnectButton(freeBrands.faSlack, "Slack") _
    Client.slackApi.isAuthenticated(user.id).map(activated => syncButton(activated))
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
          onClick --> sideEffect(linkWithGithub())),
        br(),

        button(
          "Link with Gitter",
          onClick --> sideEffect(linkWithGitter())
        ),
        br(),

        button(
          "Link with Slack",
          onClick --> sideEffect(linkWithSlack())
        ),

      )
    )
  }
}
