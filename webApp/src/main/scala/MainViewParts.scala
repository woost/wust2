package wust.webApp

import fontAwesome._
import fontAwesome.freeSolid._
import outwatch.ObserverSink
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.AuthUser
import wust.graph._
import wust.ids._
import wust.sdk.{ChangesHistory}
import wust.sdk.NodeColor._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import wust.webApp.views._

import scala.scalajs.js
import scala.scalajs.js.Date

object MainViewParts {

  def newGroupTitle(state: GlobalState) = {
    var today = new Date()
    // January is 0!
    val title =
      s"Group ${today.getMonth + 1}-${today.getDate} ${today.getHours()}:${today.getMinutes()}"
    val sameNamePosts = state.channels.now.filter(_.data.str.startsWith(title))
    if (sameNamePosts.isEmpty) title
    else s"$title ${('A' - 1 + sameNamePosts.size).toChar}"
  }

  def newGroupButton(state: GlobalState, label: String = "New Group")(
      implicit ctx: Ctx.Owner
  ): VNode = {
    button(
      cls := "ui button",
      label,
      onClick --> sideEffect { ev =>
        ev.target.asInstanceOf[dom.html.Element].blur()
        val user = state.user.now

        val nextPage = Page.NewGroup(NodeId.fresh)
        //TODO why does Var.set not work properly here with scalarx?
        // if (state.view.now.isContent) state.page() = nextPage
        // else Var.set(, state.view -> View.default)
        state.page() = nextPage
        if (!state.view.now.isContent) state.view() = View.default
      }
    )
  }

  def settingsButton(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    button(
      "Settings",
      width := "100%",
      onClick(wust.webApp.views.UserSettingsView: View) --> state.view
    )
  }

  def user(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div("User: ", state.user.map(u => s"${u.id}, ${u.name}"))
  }

  def dataImport(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    val urlImporter = Handler.create[String].unsafeRunSync()

    def importGithubUrl(url: String): Unit = Client.githubApi.importContent(url)
    def importGitterUrl(url: String): Unit = Client.gitterApi.importContent(url)

    def connectToGithub(): Unit = Client.auth.issuePluginToken().foreach { auth =>
      scribe.info(s"Generated plugin token: $auth")
      val connUser = Client.githubApi.connectUser(auth.token)
      connUser foreach {
        case Some(url) =>
          org.scalajs.dom.window.location.href = url
        case None =>
          scribe.info(s"Could not connect user: $auth")
      }
    }

    div(
      fontWeight.bold,
      fontSize := "20px",
      marginBottom := "10px",
      "Constant synchronization",
      button("Connect to GitHub", width := "100%", onClick --> sideEffect(connectToGithub())),
      "One time import",
      input(tpe := "text", width := "100%", onInput.value --> urlImporter),
      button(
        "GitHub",
        width := "100%",
        onClick(urlImporter) --> sideEffect((url: String) => importGithubUrl(url))
      ),
      button(
        "Gitter",
        width := "100%",
        onClick(urlImporter) --> sideEffect((url: String) => importGitterUrl(url))
      ),
    )
  }

}
