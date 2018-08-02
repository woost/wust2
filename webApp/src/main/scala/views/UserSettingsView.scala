package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views._

object UserSettingsView extends View {
  override val viewKey = "usersettings"
  override val displayName = "User Settings"
  override def isContent = false

  override def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    import state._
    div(
      height := "100%",
      div(
        user.map(u => listSettings(u)),
        margin := "0 auto",
        maxWidth := "48rem",
        height := "100%",
        Styles.flex,
        flexDirection.column,
        justifyContent.flexStart,
        alignItems.stretch,
        alignContent.stretch
      )
    )
  }

  def listSettings(user: UserInfo): VNode = {
    val linkGithub = Handler.create[String].unsafeRunSync()

    def linkWithGithub() = {
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

    def linkWithGitter() = {
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

    // TODO: Show button if not linked, else show linked data
    div(
      p(s"UserId: ${user.id.toString}"),
      p(s"Username: ${user.name}"),
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
