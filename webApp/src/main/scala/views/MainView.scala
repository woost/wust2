package wust.webApp.views

import wust.utilWeb._
import wust.utilWeb.views._
import wust.sdk.{ChangesHistory, SyncMode}
import wust.webApp._
import cats.effect.IO
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.events.window
import rx._
import wust.api._
import wust.utilWeb.Color._
import wust.webApp.views.graphview.GraphView
import wust.utilWeb.Analytics
import wust.utilWeb.outwatchHelpers._
import outwatch.dom.dsl.styles.extra._
import outwatch.ObserverSink

object MainView {
  import MainViewParts._

  def upButton(state: GlobalState): VNode = {
    //TODO: outwatch child <-- Option[VNode]
    span(
      children <-- state.upButtonTargetPage.map(_.toSeq.map(upTarget =>
          button("↑", width := "2.5em", onClick(upTarget) --> state.page)
      ))
    )
  }

  def showPage(state: GlobalState): VNode = {
    div(
      children <-- state.pageParentPosts.map { posts => posts.toSeq.map { post =>
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

  //def groupSelector(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
  //  div(" group: ", state.rawGraph.map { graph =>
  //    select {
  //      // only looking at memberships is sufficient to list groups, because the current user is member of each group
  //      val groupNames = graph.usersByGroupId.mapValues { users =>
  //        val userNames = users.map { id =>
  //          val user = graph.usersById(id)
  //          if (user.isImplicit)
  //            user.name.split("-").take(2).mkString("-") // shorten to "anon-987452"
  //          else
  //            user.name
  //        }
  //        val names = userNames.take(3).mkString(", ")
  //        val more = if (users.size > 3) ", ..." else ""
  //        val count = users.size
  //        s"$names$more ($count)"
  //      }

  //      val publicOption = option("public", value := "public")
  //      // val newGroupOption = option("create new private group", value := "newgroup")
  //      val groupOptions = groupNames.map {
  //        case (groupId, name) =>
  //          // val opt = option(s"${groupId.id}: $name", value := groupId.id)
  //          val opt = option(s"$name", value := Tag.unwrap(groupId))
  //          if (state.selectedGroupId().contains(groupId)) opt(selected)
  //          else opt
  //      }

  //      publicOption +: groupOptions.toSeq
  //    }(
  //      onchange := { (e: Event) =>
  //        val value = e.target.asInstanceOf[HTMLSelectElement].value
  //        if (value == "public") {
  //          state.selectedGroupId() = None
  //        } else if (value == "newgroup") {
  //          Client.api.addGroup().foreach { group =>
  //            state.selectedGroupId() = Option(group)
  //          }
  //        } else { // probably groupId
  //          val id = Option(value).filter(_.nonEmpty).map(_.toLong)
  //          state.selectedGroupId() = id.map(GroupId(_))
  //        }
  //      }
  //    ).render
  //  }).render
  //}

  //def newGroupButton(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
  //  button("new group", onclick := { () =>
  //    val groupNameOpt = Option(window.prompt("Enter a name for the group", "New Group"))
  //    groupNameOpt.foreach { groupName =>
  //      Client.api.addGroup().onComplete {
  //        case Success(groupId) =>
  //          val newPost = Post.newId(groupName)
  //          state.persistence.addChanges(
  //            addPosts = Set(newPost),
  //            addOwnerships = Set(Ownership(newPost.id, groupId))
  //          )

  //          Var.set(
  //            VarTuple(state.selectedGroupId, Option(groupId)),
  //            VarTuple(state.graphSelection, GraphSelection.Union(Set(newPost.id)))
  //          )
  //          Analytics.sendEvent("group", "created", "success")
  //        case Failure(_) =>
  //          Analytics.sendEvent("group", "created", "failure")
  //      }
  //    }
  //  }).render
  //}

  //def undoButton(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
  //  val disableAttr = if (state.persistence.canUndo()) None else Some(attr("disabled") := "")
  //  button(
  //    "↶",
  //    disableAttr,
  //    onclick := { () =>
  //      state.persistence.undoChanges()
  //    }
  //  ).render
  //}

  //def redoButton(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
  //  val disableAttr = if (state.persistence.canRedo()) None else Some(attr("disabled") := "")
  //  button(
  //    "↷",
  //    disableAttr,
  //    onclick := { () =>
  //      state.persistence.redoChanges()
  //    }
  //  ).render
  //}

  //def inviteUserToGroupField(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
  //  (if (state.selectedGroupId().isDefined) {
  //    val field = input(tpe := "text", placeholder := "invite user by name").render
  //    form(field, input(tpe := "submit", value := "invite"), onsubmit := { () =>
  //      val userName = field.value
  //      state.selectedGroupId().foreach(Client.api.addMemberByName(_, userName).foreach { success =>
  //        field.value = ""
  //        Analytics.sendEvent("group", "invitebyname", if (success) "success" else "failure")
  //      })

  //      false
  //    }).render
  //  } else div().render)
  //}

  //def currentGroupInviteLink(state: GlobalState)(implicit ctx: Ctx.Owner): Rx[Option[String]] = {
  //  val inviteLink = Var[Option[String]](None)
  //  Rx {
  //    state.selectedGroupId() match {
  //      case Some(groupId) =>
  //        Client.api.getGroupInviteToken(groupId).foreach {
  //          //TODO: we should not construct absolute paths here
  //          case Some(token) => inviteLink() = Some(s"${location.href.split("#").head}#${ViewConfig.toHash(state.viewConfig())}&invite=$token")
  //          case None        =>
  //        }
  //      case None => inviteLink() = None
  //    }
  //  }
  //  inviteLink
  //}

  def viewSelection(state:GlobalState, allViews: Seq[View]): VNode = {
    //TODO: instead of select show something similar to tabs (require only one click to change)
    val viewHandler = Handler.create[View]().unsafeRunSync()

    div(
      managed(IO(viewHandler.debug("selected view"))),
      managed(IO(viewHandler.foreach(currentView => Analytics.sendEvent("view", "select", currentView.toString)))),
      managed(state.view <-- viewHandler),
      display.flex,
      allViews.map{ view =>
        div(
          view.displayName,
          padding := "8px",
          cursor.pointer,
          backgroundColor <-- state.view.map(selectedView => if (selectedView == view) "#EAEAEA" else "transparent"),
          onClick(view) --> viewHandler,
        )
      }
    )
    // select(
    //   onInput.value.map(View.fromString) --> viewHandler,
    //   allViews.map{view =>
    //     option(
    //       view.displayName,
    //       value := view.key,
    //       selected <-- state.view.map(_ == view)
    //     )
    //   }
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

  //def invitePopup(state: GlobalState)(implicit ctx: Ctx.Owner) = {
  //  val show = Var(false)
  //  val activeDisplay = display := show.map(if (_) "block" else "none")
  //  val inactiveDisplay = display := show.map(if (_) "none" else "block")

  //  div(
  //    div(
  //      inactiveDisplay,
  //      button("invite...", onclick := { () => show() = !show.now })
  //    ),
  //    div(
  //      activeDisplay,
  //      zIndex := 100,
  //      cls := "shadow",
  //      position.fixed, top := 5, left := 5, boxSizing.`border-box`,
  //      padding := "15px", background := "#F8F8F8", border := "1px solid #DDD",
  //      div("x", cursor.pointer, float.right, onclick := { () => show() = false }),
  //      h4("Invite participants to group"),
  //      groupSelector(state),
  //      inviteUserToGroupField(state),
  //      currentGroupInviteLink(state).map(linkOpt => linkOpt.map{ link =>
  //        val linkField = input(tpe := "text", value := link, readonly).render
  //        div(
  //          linkField,
  //          button("copy", onclick := { () => linkField.select(); document.execCommand("Copy") })
  //        )
  //      }.getOrElse(span()).render)
  //    )
  //  )
  //}

   def topBar(state: GlobalState, allViews: Seq[View]): VNode = {
//     val lockToGroup = state.viewConfig.now.lockToGroup
//     if (lockToGroup) {
//       div(
//         padding := "5px", background := "#F8F8F8", borderBottom := "1px solid #DDD",
//         display.flex, alignItems.center, justifyContent.spaceBetween,
//
//         div(
//           display.flex, alignItems.center, justifyContent.flexStart,
//
//           upButton(state),
//           showPage(state)
//         ),
//
//         if (viewPages.size > 1) div("view: ")(viewSelection(state, viewPages))
//         else div(),
//
//         syncStatus(state),
//         div(
//           undoButton(state),
//           redoButton(state)
//         )
//       )
//     } else {
       div(
         padding := "5px", background := "#FAFAFA", borderBottom := "1px solid #DDD",
         display := "flex", alignItems := "center", justifyContent := "spaceBetween",

         div(
           display := "flex", alignItems := "center", justifyContent := "flexStart",

           upButton(state),
           showPage(state),
           // viewSelection(state, allViews),
//           groupSelector(state),
//           invitePopup(state),
//           newGroupButton(state)
         ),


        syncStatus(state),
//         div(
//           undoButton(state),
//           redoButton(state)
//         ),
//
//         div(
//           display.flex, alignItems.center, justifyContent.flexEnd,
//           UserView.topBarUserStatus(state)
//         )
       )
//     }
   }

  // def bottomBar(state: GlobalState)(implicit ctx: Ctx.Owner) = {
  //   div(
  //     padding := "5px", background := "#F8F8F8", borderTop := "1px solid #DDD"
  //   )
  // }
  //
  def sidebar(state: GlobalState): VNode = {
    div(
      backgroundColor <-- state.pageStyle.map(_.darkBgColor),
      padding := "15px",
      color := "white",
      titleBanner,
      br(),
      userStatus(state),
      br(),
      dataImport(state),
      br(),
      child <-- RestructuringTaskGenerator.renderStudy(state),
      br(),
      channels(state),
      br(),
      settingsButton(state),
      br(),
      br(),
      syncStatus(state)
    )
  }

  def settingsButton(state: GlobalState): VNode = {
    button("Settings", width := "100%", onClick(wust.webApp.views.UserSettingsView) --> state.view),
  }

  def channels(state: GlobalState): VNode = {
      div(
        color := "#C4C4CA",
        "#channels"
      )
  }

  def user(state: GlobalState): VNode = {
    div( "User: ", child <-- state.currentUser.map(u => s"${u.id}, ${u.name}" ))
  }

  def dataImport(state:GlobalState): VNode = {
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
      input(tpe := "text", width:= "100%", onInput.value --> urlImporter),
      button("GitHub", width := "100%", onClick(urlImporter) --> sideEffect((url: String) => importGithubUrl(url))),
      button("Gitter", width := "100%", onClick(urlImporter) --> sideEffect((url: String) => importGitterUrl(url))),
    )
  }


  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit owner: Ctx.Owner): VNode = {
    div(
      height := "100%",
      div(
//              height := "100%",
        id := "pagegrid",
//        sidebar(state),
        ChatView(state),
        new GraphView().apply(state)
      ),

      // feedbackForm (state),
      DevOnly { DevView.devPeek(state) },
      // DevOnly { DevView.jsError(state) }
    )
  }
}
