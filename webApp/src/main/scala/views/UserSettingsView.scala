package wust.webApp.views

import fontAwesome.{freeBrands, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Components._
import wust.webApp.views._

object UserSettingsView extends View {
  override val viewKey = "usersettings"
  override val displayName = "User Settings"
  override def isContent = false

  override def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      padding := "20px",
      Rx {
        val user = state.user()
        VDomModifier(
          header(user)(marginBottom := "50px"),
          slackButton(user)
        )
      }
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

  private def slackButton(user: UserInfo): VNode = {
    button(
      cls := "ui button",
      div(
        Styles.flex,
        fontSize := "25px",
        woostIcon( marginRight := "10px" ),
        (freeSolid.faExchangeAlt:VNode)(marginRight := "10px"),
        (freeBrands.faSlack:VNode),
        marginBottom := "5px",
      ),
      div("Sync with Slack"),
      onClick --> sideEffect(linkWithSlack())
    )
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
