package wust.webApp

import io.circe.Decoder.state
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import wust.sdk.PostColor._
import rx._
import wust.ids._
import wust.graph._
import wust.sdk.{ChangesHistory, SyncMode, PostColor}
import wust.util.RichBoolean
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.{LoginView, View}
import views.PageStyle

import scala.scalajs.js.Date

object MainViewParts {
  val titleBanner: VNode = {
      div(
        "Woost",
        fontWeight.bold,
        fontSize := "20px",
        marginBottom := "10px"
      )
  }

  def upButton(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    span(
      state.upButtonTargetPage.map(_.toSeq.map(upTarget =>
          button("↑", width := "2.5em", onClick(upTarget) --> state.page.toHandler)
      ))
    )
  }

  def syncStatus(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    val isOnline = Observable.merge(Client.observable.connected.map(_ => true), Client.observable.closed.map(_ => false))
    div(
      isOnline.map { isOnline =>
        span(
          if (isOnline) Seq(asVDomModifier("On"), color := "white", title := "The connection to the server is established.")
          else Seq(asVDomModifier("Off"), color := "red", title := "The connection to the server has stopped. Will try to reconnect.")
        )
      },
      " (",
      state.syncMode.map { mode =>
        span(
          mode.toString,
          cursor.pointer,
          title := "Click to switch syncing mode (Live/Local). Live mode automatically synchronizes all changes online. Local mode will keep all your changes locally and hide incoming events.",
          if (mode == SyncMode.Live) Seq(color := "white")
          else Seq(color := "grey"),
          onClick.map(_ => (if (mode == SyncMode.Live) SyncMode.Local else SyncMode.Live):SyncMode) --> state.syncMode
        )
      },
      ")",
      state.eventProcessor.changesInTransit.map { changes =>
        span(
          " ⬤ ", // middle dot
          if (changes.isEmpty) Seq(color := "green", title := "Everything is synchronized.")
          else Seq(color := "blue", title := "Some changes are only local, just wait until they are send online.")
        )
      }
    )
  }

  def login(state: GlobalState)(implicit ctx:Ctx.Owner) = button("Login", onClick(LoginView: View) --> state.view)
  val logout = button("Logout", onClick --> sideEffect { Client.auth.logout(); () })

  def authentication(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = div(
    state.currentUser.map {
      case user: User.Assumed => login(state)
      case user: User.Implicit => login(state)
      case user: User.Real => logout
    }
  )

  def undoRedo(state:GlobalState):VNode = {
    val historySink = ObserverSink(state.eventProcessor.history.action)
    div(
      state.eventProcessor.changesHistory.startWith(Seq(ChangesHistory.empty)).map { history =>
        div(
          display.flex,
          style("justify-content") := "space-evenly",
          button("Undo", title := "Undo last change", onClick(ChangesHistory.Undo) --> historySink, disabled := !history.canUndo),
          button("Redo", title := "Redo last undo change", onClick(ChangesHistory.Redo) --> historySink, disabled := !history.canRedo)
        )
      }
    )
  }

  def newGroupButton(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    def groupTitle = {
      var today = new Date()
      // January is 0!
      val title = s"Group: ${today.getMonth+1}-${today.getDate}"
      val sameNamePosts = state.highLevelPosts.now.filter(_.content.startsWith(title))
      if (sameNamePosts.isEmpty) title
      else s"$title (${sameNamePosts.size})"
    }
    button("New Group",
      onClick --> sideEffect{ _ =>
        val post = Post(groupTitle, state.currentUser.now.id)
        for {
          _ <- state.eventProcessor.changes.onNext(GraphChanges.addPost(post))
        } {
          state.view() = View.default
          state.page() = Page(post.id)
          state.highLevelPosts.update(post :: _)
        }

        ()
      })
  }

  def newGroupPage(state: GlobalState)(implicit owner:Ctx.Owner):VNode = {
    div(
      display.flex, justifyContent.spaceAround, flexDirection.column, alignItems.center,
      newGroupButton(state)(owner)(padding := "20px", marginBottom := "10%")
    )
  }

  def channels(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      color := "#C4C4CA",
      Rx {
        state.highLevelPosts().map{p => div(
          padding := "5px 3px",
          p.content,
          cursor.pointer,
          onChannelClick(p.id)(state),
          title := p.id,
          state.page().parentIds.contains(p.id).ifTrueSeq(Seq(
            color := state.pageStyle().darkBgColor.toHex,
            backgroundColor := state.pageStyle().darkBgColorHighlight.toHex
          ))
        )}
      }
    )
  }

  def channelIcons(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      color := "#C4C4CA",
      Rx {
        state.highLevelPosts().map{p => div(
          margin := "0",
          padding := "3px",
          width := "30px",
          height := "30px",
          cursor.pointer,
          onChannelClick(p.id)(state),
          backgroundColor := PageStyle.Color.baseBg.copy(h = PostColor.genericBaseHue(p.id)).toHex, //TODO: make different post color tones better accessible
            opacity := (if(state.page().parentIds.contains(p.id)) 1.0 else 0.75),
          Avatar.post(p.id)(
          )
        )}
      }
      )
  }

  private def onChannelClick(id: PostId)(state: GlobalState)(implicit ctx: Ctx.Owner) = onClick.map { e =>
    val page = state.page.now
    //TODO if (e.shiftKey) {
    val newParents = if (e.ctrlKey) {
      val filtered = page.parentIds.filterNot(_ == id)
      if (filtered.size == page.parentIds.size) page.parentIds :+ id
      else if (filtered.nonEmpty) filtered
      else Seq(id)
    } else Seq(id)

    page.copy(parentIds = newParents)
    } --> sideEffect { page =>
      state.view() = View.default
      state.page() = page
      //TODO: Why does Var.set not work?
      // Var.set(
      //   state.page -> page,
      //   state.view -> view
      // )
    }

  //def feedbackForm(state: GlobalState)(implicit ctx: Ctx.Owner) = {
  //  val lockToGroup = state.viewConfig.now.lockToGroup

  //  //TODO: Don't hardcode feedback postId
  //  val feedbackPostId = PostId("82")

  //  val show = Var(false)
  //  val activeDisplay = display := show.map(if (_) "block" else "none")
  //  val inactiveDisplay = display := show.map(if (_) "none" else "block")

  //  def submitInsert(field: HTMLTextAreaElement) = {
  //    val text = field.value
  //    //TODO better handling of missing(?) feedbacknode
  //    val newPost = Post.newId(text)
  //    state.persistence.addChanges(addPosts = Set(newPost), addContainments = Set(Containment(feedbackPostId, newPost.id)))
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
  //    if (!lockToGroup) button("show all feedback", tpe := "button", float.right, onclick := { () => state.graphSelection() = GraphSelection.Union(Set(feedbackPostId)) }) else span(),
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
    button("Settings", width := "100%", onClick(wust.webApp.views.UserSettingsView:View) --> state.view)
  }

  def user(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div("User: ", state.currentUser.map(u => s"${u.id}, ${u.name}"))
  }

  def dataImport(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    val urlImporter = Handler.create[String].unsafeRunSync()

    def importGithubUrl(url: String): Unit = Client.api.importGithubUrl(url)

    def importGitterUrl(url: String): Unit = Client.api.importGitterUrl(url)

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
      button("GitHub", width := "100%", onClick(urlImporter) --> sideEffect((url: String) => importGithubUrl(url))),
      button("Gitter", width := "100%", onClick(urlImporter) --> sideEffect((url: String) => importGitterUrl(url))),
    )
  }


}
