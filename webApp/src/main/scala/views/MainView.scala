package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.sdk.PostColor._
import wust.utilWeb._
import wust.utilWeb.outwatchHelpers._
import wust.utilWeb.views._
import wust.webApp.views.graphview.GraphView

object MainView {

  import MainViewParts._
  //TODO: outwatch child <-- Option[VNode]

  def showPage(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    div(
      state.pageParentPosts.map { posts =>
        posts.toSeq.map { post =>
          span(
            post.content,
            backgroundColor := baseColor(post.id).toString,
            fontWeight.bold,
            margin := "2px", padding := "1px 5px 1px 5px",
            borderRadius := "2px"
          )
        }
      }
    )
  }

//  def viewSelection(state: GlobalState, allViews: Seq[View]): VNode = {
//    //TODO: instead of select show something similar to tabs (require only one click to change)
//    val viewHandler = Handler.create[View]().unsafeRunSync()
//
//    div(
//      managed(IO(viewHandler.debug("selected view"))),
//      managed(IO(viewHandler.foreach(currentView => Analytics.sendEvent("view", "select", currentView.toString)))),
//      managed(state.view <-- viewHandler),
//      display.flex,
//      allViews.map { view =>
//        div(
//          view.displayName,
//          padding := "8px",
//          cursor.pointer,
//          backgroundColor <-- state.view.map(selectedView => if (selectedView == view) "#EAEAEA" else "transparent"),
//          onClick(view) --> viewHandler,
//        )
//      }
//    )
//  }

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

//  def topBar(state: GlobalState, allViews: Seq[View])(implicit owner: Ctx.Owner): VNode = {
//    div(
//      padding := "5px", background := "#FAFAFA", borderBottom := "1px solid #DDD",
//      display := "flex", alignItems := "center", justifyContent := "spaceBetween",
//
//      div(
//        display := "flex", alignItems := "center", justifyContent := "flexStart",
//
//        upButton(state),
//        showPage(state)
//      ),
//
//
//      syncStatus(state)
//    )
//  }

  def sidebar(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
      padding := "15px",
      color := "white",
      titleBanner,
      upButton(state),
      channels(state),
      newGroupButton(state),
      authentication(state),
      br(),
      //      dataImport(state),
      br(),
      //      RestructuringTaskGenerator.renderStudy(state),
      br(),
      br(),
      //      settingsButton(state),
      br(),
      br(),
      syncStatus(state)
    )
  }

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


  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      div(
        id := "pagegrid",
        sidebar(state),
        backgroundColor <-- state.pageStyle.map(_.accentLineColor.toHex),
        div(
          height := "100%",
          backgroundColor <-- state.pageStyle.map(_.bgColor.toHex),

          Rx {
            if (state.page().parentIds.nonEmpty) {
              state.view().apply(state)(owner)
            } else {
              div(
                display.flex, justifyContent.spaceAround, flexDirection.column, alignItems.center,
                newGroupButton(state)(owner)(padding := "20px", marginBottom := "10%")
              )
            }
          }
        )
      ),

      // feedbackForm (state),
      DevOnly {
        DevView.devPeek(state, List(
          div(
            div("Raw Graph. Click to show.", padding := "3px", position.absolute, color := "#999"),
            backgroundColor := "#DDD",
            width := "200px",
            height := "200px",
            border := "1px solid #999",
            margin := "5px",
            GraphView(state, state.rawGraph, controls = false)(owner)(
            )))
        )
      }
      // DevOnly { DevView.jsError(state) }
    )
  }
}
