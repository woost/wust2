package wust.webApp.views

import outwatch.{ObserverSink, Sink, dom}
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import wust.utilWeb._
import wust.sdk.PostColor._
import wust.graph._
import wust.ids.{PostId, UserId}
import wust.utilWeb.views._
import wust.utilWeb.views.Rendered._
import outwatch.dom.dsl.events.window
import wust.utilWeb.outwatchHelpers._
import rx._

object UserSettingsView extends View {
  override val key = "usersettings"
  override val displayName = "User Settings"

  override def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    import state._

    val newPostSink = ObserverSink(eventProcessor.enriched.changes).redirect { (o: Observable[String]) =>
      o.withLatestFrom(state.currentUser)((msg, user) => GraphChanges(addPosts = Set(Post(PostId.fresh, msg, user.id))))
    }

    component(
      currentUser,
      newPostSink,
      page,
      pageStyle,
      displayGraphWithoutParents.map(_.graph)
    )
  }

  def component(
                 currentUser: Observable[User],
                 newPostSink: Sink[String],
                 page: Handler[Page],
                 pageStyle: Observable[PageStyle],
                 graph: Observable[Graph]
               ): VNode = {
    div(
       height := "100%",
      backgroundColor <-- pageStyle.map(_.bgColor),

      borderRight := "2px solid",
      borderColor <-- pageStyle.map(_.accentLineColor),

      div(
        p( mdHtml(pageStyle.map(_.title)) ),

        child <-- currentUser.map(listSettings),


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

  def listSettings(user: User): VNode = {
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
        button("Link with Gitter", onClick(user.id) --> sideEffect((userId: UserId) => linkWithGitter(userId))),
        br(),
        button("Link with Slack", onClick(user.id) --> sideEffect((userId: UserId) => linkWithSlack(userId))),
      )
    )
  }

}
