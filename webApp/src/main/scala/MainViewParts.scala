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
import wust.sdk.{ChangesHistory, SyncMode}
import wust.sdk.NodeColor._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import wust.webApp.views._

import scala.scalajs.js
import scala.scalajs.js.Date

object MainViewParts {

  def postTag(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = {
    span(
      node.data.str, //TODO trim! fit for tag usage...
      onClick --> sideEffect { e =>
        state.page() = Page(Seq(node.id)); e.stopPropagation()
      },
      backgroundColor := computeTagColor(node.id),
      fontSize.small,
      color := "#fefefe",
      borderRadius := "2px",
      padding := "0px 3px",
      marginRight := "3px"
    )
  }

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
        val nowInAWeek = dateFns.addWeeks(new js.Date(js.Date.now()), 1)
        val post = new Node.Content(
          NodeId.fresh,
          NodeData.PlainText(newGroupTitle(state)),
          NodeMeta(
            DeletedDate.NotDeleted,
            JoinDate.Until(EpochMilli(nowInAWeek.getTime().toLong)),
            joinLevel = AccessLevel.ReadWrite
          )
        )
        for {
          _ <- state.eventProcessor.changes
            .onNext(GraphChanges.addNodeWithParent(post, user.channelNodeId))
        } {
          if (!state.view.now.isContent) state.view() = View.default
          state.page() = Page(post.id)
        }
      }
    )
  }

  //def feedbackForm(state: GlobalState)(implicit ctx: Ctx.Owner) = {
  //  val lockToGroup = state.viewConfig.now.lockToGroup

  //  //TODO: Don't hardcode feedback nodeId
  //  val feedbackNodeId = NodeId("82")

  //  val show = Var(false)
  //  val activeDisplay = display := show.map(if (_) "block" else "none")
  //  val inactiveDisplay = display := show.map(if (_) "none" else "block")

  //  def submitInsert(field: HTMLTextAreaElement) = {
  //    val text = field.value
  //    //TODO better handling of missing(?) feedbacknode
  //    val newPost = Post.newId(text)
  //    state.persistence.addChanges(addPosts = Set(newPost), addContainments = Set(Containment(feedbackNodeId, newPost.id)))
  //    field.value = ""
  //    Analytics.sendEvent("feedback", "submit", "")
  //    false
  //  }
  //  val feedbackField = textareaWithEnter(submitInsert)(
  //    rows := 5,
  //    cols := 30,
  //    placeholder := "Missing features? Suggestions? You found a bug? What do you like? What is annoying? Press Enter to submit."
  //  ).render

  //  show.foreach {
  //    if (_)
  //      setTimeout(100) {
  //        feedbackField.focus()
  //      }
  //  }

  //  val feedbackForm = form(
  //    feedbackField, br(),
  //    if (!lockToGroup) button("show all feedback", tpe := "button", float.right, onclick := { () => state.graphSelection() = GraphSelection.Union(Set(feedbackNodeId)) }) else span(),
  //    input (tpe := "submit", value := "submit"),
  //    onsubmit := { () =>
  //      submitInsert(feedbackField)
  //      false
  //    }
  //  )

  //  div(
  //    div(
  //      inactiveDisplay,
  //      position.fixed, bottom := 250, right := 0, boxSizing.`border-box`,
  //      padding := "5px", background := "#F8F8F8", border := "1px solid #DDD", borderBottom := "none",
  //      "Give short feedback",
  //      css("transform") := "rotate(-90deg) translate(0,-100%)",
  //      css("transform-origin") := "100% 0",
  //      cursor.pointer,
  //      onclick := { () => show() = true }
  //    ),
  //    div(
  //      activeDisplay,
  //      position.fixed, bottom := 150, right := 0, boxSizing.`border-box`,
  //      padding := "5px", background := "#F8F8F8", border := "1px solid #DDD", borderRight := "none",
  //      div("x", cursor.pointer, float.right, onclick := { () => show() = false }),
  //      div("Feedback"),
  //      feedbackForm
  //    )
  //  )
  //}

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
