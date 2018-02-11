package wust.webApp.views

import outwatch.Sink
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import wust.webApp.Color._
import wust.graph._
import wust.ids.{PostId, UserId}
import Rendered._
import wust.util.outwatchHelpers._

object UserSettingsView extends View {
  override val key = "usersettings"
  override val displayName = "User Settings"

  override def apply(state: GlobalState): VNode = {
    import state._

    val newPostSink = eventProcessor.enriched.changes.redirect { (o: Observable[String]) =>
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

    // TODO: Api calls
    def linkWithGithub(userId: UserId) = {
      println(s"Link Github with userId: $userId")
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
        button("Link with GitHub", onClick(user.id) --> sideEffect((userId: UserId) => linkWithGithub(userId))),
        br(),
        button("Link with Gitter", onClick(user.id) --> sideEffect((userId: UserId) => linkWithGitter(userId))),
        br(),
        button("Link with Slack", onClick(user.id) --> sideEffect((userId: UserId) => linkWithSlack(userId))),
      )
    )
  }

}
