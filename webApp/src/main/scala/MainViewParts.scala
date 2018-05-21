package wust.webApp

import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids._
import wust.sdk.{ChangesHistory, PostColor, SyncMode}
import wust.util.RichBoolean
import fontAwesome.freeSolid._
import wust.webApp.outwatchHelpers._
import wust.webApp.views._
import Elements._
import fontAwesome._

import scala.scalajs
import scala.scalajs.js
import scala.scalajs.js.Date

object MainViewParts {

  def upButton(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    span(
      state.upButtonTargetPage.map(_.toSeq.map(upTarget =>
          button("↑", width := "2.5em", onClick(upTarget) --> state.page.toHandler)
      ))
    )
  }

  def syncStatus(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    val syncingIcon = fontawesome.layer(push => {
      push(fontawesome.icon(freeSolid.faCircle, Params(
        styles = scalajs.js.Dictionary[String]( "color" -> "#4EBA4C" )
      )))
      push(fontawesome.icon(freeSolid.faSync, Params(
        transform = new Transform{override val size = 10.0},
        classes = scalajs.js.Array("fa-spin"),
        styles = scalajs.js.Dictionary[String]( "color" -> "white" ),
      )))
    })

    val syncedIcon = fontawesome.layer(push => {
      push(fontawesome.icon(freeSolid.faCircle, Params(
        styles = scalajs.js.Dictionary[String]( "color" -> "#4EBA4C" )
      )))
      push(fontawesome.icon(freeSolid.faCheck, Params(
        transform = new Transform{override val size = 10.0},
        styles = scalajs.js.Dictionary[String]( "color" -> "white" ),
      )))
    })


    val isOnline = Observable.merge(Client.observable.connected.map(_ => true), Client.observable.closed.map(_ => false))
    div(
      isOnline.map { isOnline =>
        span(
          if (isOnline) Seq(freeSolid.faCloud:VNode, color := "white", title := "The connection to the server is established.")
          else Seq(freeSolid.faBolt:VNode, color := "tomato", title := "The connection to the server has stopped. Will try to reconnect."),
          marginRight := "2px"
        )
      },
      state.eventProcessor.changesInTransit.map { changes =>
        span(
          if (changes.isEmpty) Seq(
            span(syncedIcon:VNode),
            color := "#48B02C", title := "Everything is synchronized.")
          else Seq(syncingIcon:VNode, title := "Some changes are only local, just wait until they are send online.")
        )
      },
      DevOnly{ span(
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
        ")"
      ) }
    )
  }

  def login(state: GlobalState)(implicit ctx:Ctx.Owner) = state.viewConfig.map { viewConfig =>
    span(viewConfigLink(viewConfig.overlayView(SignupView))("Signup", color := "white"), " or ", viewConfigLink(viewConfig.overlayView(LoginView))("Login", color := "white"))
  }

  val logout = button("Logout", onClick --> sideEffect { Client.auth.logout(); () })

  def authentication(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = div(
    state.user.flatMap {
      case user: User.Assumed => login(state)
      case user: User.Implicit => login(state)
      case user: User.Real => Var(logout)
    }
  )

  def undoRedo(state:GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val historySink = ObserverSink(state.eventProcessor.history.action)
    div(
      state.eventProcessor.changesHistory.startWith(Seq(ChangesHistory.empty)).combineLatestMap(state.view.toObservable) { (history, view) =>
        div(
          if (view.isContent) Seq(
            display.flex,
            style("justify-content") := "space-evenly",
            button(faUndo, padding := "5px 10px", marginRight := "2px", fontSize.small, title := "Undo last change", onClick(ChangesHistory.Undo) --> historySink, disabled := !history.canUndo),
            button(faRedo, padding := "5px 10px", fontSize.small, title := "Redo last undo change", onClick(ChangesHistory.Redo) --> historySink, disabled := !history.canRedo)
          ) else Seq.empty[VDomModifier]
        )
      }
    )
  }


  def newGroupTitle(state: GlobalState) = {
    var today = new Date()
    // January is 0!
    val title = s"Group ${today.getMonth+1}-${today.getDate} ${today.getHours()}:${today.getMinutes()}"
    val sameNamePosts = state.channels.now.filter(_.content.str.startsWith(title))
    if (sameNamePosts.isEmpty) title
    else s"$title ${('A'-1+sameNamePosts.size).toChar}"
  }

  def newGroupButton(state: GlobalState, label: String = "New Group")(implicit ctx:Ctx.Owner): VNode = {
    button(
      label,
      onClick --> sideEffect{ _ =>
        val user = state.user.now
        val post = Post(PostContent.Text(newGroupTitle(state)), user.id)
        for {
          _ <- state.eventProcessor.changes.onNext(GraphChanges.addPostWithParent(post, user.channelPostId))
        } {
          if (!state.view.now.isContent) state.view() = View.default
          state.page() = Page(post.id)
        }
      })
  }

  def newGroupPage(state: GlobalState)(implicit owner:Ctx.Owner):VNode = {
    div(
      display.flex, justifyContent.spaceAround, flexDirection.column, alignItems.center,
      newGroupButton(state)(owner)(padding := "20px", marginBottom := "10%")
    )
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
    div("User: ", state.user.map(u => s"${u.id}, ${u.name}"))
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
