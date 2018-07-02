package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.{ObserverSink, Sink}
import rx._
import wust.graph._
import wust.ids._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views._

object UserSettingsView extends View {
  override val key = "usersettings"
  override val displayName = "User Settings"

  override def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    import state._
    div(
      height := "100%",
      div(
        user.map(u => listSettings(u)),
        margin := "0 auto",
        maxWidth := "48rem",
        height := "100%",
        display.flex,
        flexDirection.column,
        justifyContent.flexStart,
        alignItems.stretch,
        alignContent.stretch
      )
    )
  }

  def listSettings(user: UserInfo): VNode = {
    val linkGithub = Handler.create[String].unsafeRunSync()

    // TODO: Api calls
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

    def linkWithGitter(userId: UserId) = {
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
      println(s"Link Gitter with userId: $userId")
    }

    def linkWithSlack(userId: UserId) = {
      println(s"Link Slack with userId: $userId")
    }

    // TODO: Show button if not linked, else show linked data
    div(
      p(s"UserId: ${user.id.toString}"),
      p(s"Username: ${user.name}"),
      div(
        p("Connect Woost with a Service"),
        button("Link with GitHub", onClick --> sideEffect(linkWithGithub())),
        br(),
        button(
          "Link with Gitter",
          onClick(user.id) --> sideEffect((userId: UserId) => linkWithGitter(userId))
        ),
        br(),
        button(
          "Link with Slack",
          onClick(user.id) --> sideEffect((userId: UserId) => linkWithSlack(userId))
        ),
      )
    )
  }

}
