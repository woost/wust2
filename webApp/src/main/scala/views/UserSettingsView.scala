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

    def linkWithSlack(userId: UserId) = {
      println(s"Link Slack with userId: $userId")
    }

    button(
      cls := "ui button",
      div(
        Styles.flex,
        fontSize := "25px",
        img(src := "safari-pinned-tab.svg", width := "1em", height := "100%")(marginRight := "10px"),
        (freeSolid.faExchangeAlt:VNode)(marginRight := "10px"),
        (freeBrands.faSlack:VNode),
        marginBottom := "5px",
      ),
      div("Sync with Slack"),
      onClick(user.id) --> sideEffect((userId: UserId) => linkWithSlack(userId))
    )
  }

  private def listSettings(user: UserInfo): VNode = {
    val linkGithub = Handler.create[String].unsafeRunSync()

    // TODO: Api calls
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

    def linkWithGitter(userId: UserId) = {
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
      println(s"Link Gitter with userId: $userId")
    }

    def linkWithSlack(userId: UserId) = {
      println(s"Link Slack with userId: $userId")
    }

    // TODO: Show button if not linked, else show linked data
    div(
      p(s"UserId: ${ user.id.toString }"),
      p(s"Username: ${ user.name }"),
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
