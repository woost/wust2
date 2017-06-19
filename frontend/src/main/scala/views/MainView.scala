package wust.frontend.views

import autowire._
import boopickle.Default._
import org.scalajs.dom.{ Event, document, window, console, Element }
import org.scalajs.dom.window.location
import wust.util.tags._
import rx._
import rxext._
import wust.frontend.Color._
import wust.frontend.views.graphview.GraphView
import wust.frontend.{ DevOnly, GlobalState }
import org.scalajs.dom.raw.{ HTMLInputElement, HTMLSelectElement }
import org.scalajs.dom.raw.{ HTMLTextAreaElement }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import wust.ids._
import scalatags.JsDom.TypedTag
import wust.api._
import wust.graph._
import wust.frontend.{ RichPostFactory, Client }
import wust.util.EventTracker.sendEvent
import scala.util.{ Try, Success, Failure }
import scalaz.Tag
import scala.scalajs.js.timers.setTimeout
import wust.frontend.{ SyncStatus, SyncMode }

import scalatags.JsDom.all._
import scalatags.rx.all._

//TODO: let scalatagst-rx accept Rx(div()) instead of only Rx{(..).render}
object MainView {
  import Elements._

  def upButton(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    //TODO: handle containment cycles
    (state.graphSelection() match {
      case GraphSelection.Root => span()
      case GraphSelection.Union(parentIds) =>
        val newParentIds = parentIds.flatMap(state.rawGraph().parents)
        val newParentNames = newParentIds.map(state.rawGraph().postsById(_).title).mkString(", ")
        val buttonTitle = if (newParentIds.nonEmpty) s"Focus $newParentNames" else "Remove Focus"
        button("↑", width := "2.5em", title := buttonTitle,
          onclick := { () =>
            state.graphSelection() = if (newParentIds.nonEmpty) GraphSelection.Union(newParentIds)
            else GraphSelection.Root
          })
    }).render
  }

  def focusedParents(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    val selection = state.graphSelection()
    val graph = state.rawGraph()

    div(
      selection.parentIds.toSeq.map { parentId =>
        val post = graph.postsById(parentId)
        span(
          post.title,
          backgroundColor := baseColor(post.id).toString,
          fontWeight.bold,
          margin := "2px", padding := "1px 5px 1px 5px",
          borderRadius := "2px"
        )
      }
    ).render
  }

  def groupSelector(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    div(" group: ", state.rawGraph.map { graph =>
      select {
        // only looking at memberships is sufficient to list groups, because the current user is member of each group
        val groupNames = graph.usersByGroupId.mapValues { users =>
          val userNames = users.map { id =>
            val user = graph.usersById(id)
            if (user.isImplicit)
              user.name.split("-").take(2).mkString("-") // shorten to "anon-987452"
            else
              user.name
          }
          val names = userNames.take(3).mkString(", ")
          val more = if (users.size > 3) ", ..." else ""
          val count = users.size
          s"$names$more ($count)"
        }

        val publicOption = option("public", value := "public")
        // val newGroupOption = option("create new private group", value := "newgroup")
        val groupOptions = groupNames.map {
          case (groupId, name) =>
            // val opt = option(s"${groupId.id}: $name", value := groupId.id)
            val opt = option(s"$name", value := Tag.unwrap(groupId))
            if (state.selectedGroupId().contains(groupId)) opt(selected)
            else opt
        }

        publicOption +: groupOptions.toSeq
      }(
        onchange := { (e: Event) =>
          val value = e.target.asInstanceOf[HTMLSelectElement].value
          if (value == "public") {
            state.selectedGroupId() = None
          } else if (value == "newgroup") {
            Client.api.addGroup().call().foreach { group =>
              state.selectedGroupId() = Option(group)
            }
          } else { // probably groupId
            val id = Option(value).filter(_.nonEmpty).map(_.toLong)
            state.selectedGroupId() = id.map(GroupId(_))
          }
        }
      ).render
    }).render
  }

  def newGroupButton(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    button("new group", onclick := { () =>
      val groupNameOpt = Option(window.prompt("Enter a name for the group", "New Group"))
      groupNameOpt.foreach { groupName =>
        Client.api.addGroup().call().onComplete {
          case Success(groupId) =>
            state.selectedGroupId() = Option(groupId)
            val newPost = Post.newId(groupName)
            state.persistence.addChanges(
              addPosts = Set(newPost),
              addOwnerships = Set(Ownership(newPost.id, groupId))
            )
            state.graphSelection() = GraphSelection.Union(Set(newPost.id))
            sendEvent("group", "created", "success")
          case Failure(e) =>
            sendEvent("group", "created", "failure")
        }
      }
    }).render
  }

  def inviteUserToGroupField(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    (if (state.selectedGroupId().isDefined) {
      val field = input(tpe := "text", placeholder := "invite user by name").render
      form(field, input(tpe := "submit", value := "invite"), onsubmit := { () =>
        val userName = field.value
        state.selectedGroupId().foreach(Client.api.addMemberByName(_, userName).call().foreach { success =>
          field.value = ""
          sendEvent("group", "invitebyname", if (success) "success" else "failure")
        })

        false
      }).render
    } else div().render)
  }

  def currentGroupInviteLink(state: GlobalState)(implicit ctx: Ctx.Owner): Rx[Option[String]] = {
    val inviteLink = Var[Option[String]](None)
    Rx {
      state.selectedGroupId() match {
        case Some(groupId) =>
          Client.api.getGroupInviteToken(groupId).call().foreach {
            //TODO: we should not construct absolute paths here
            case Some(token) => inviteLink() = Some(s"${location.href.split("#").head}#${ViewConfig.toHash(state.viewConfig())}&invite=$token&locktogroup=true")
            case None        =>
          }
        case None => inviteLink() = None
      }
    }
    inviteLink
  }

  def viewSelection(state: GlobalState, pages: Seq[ViewPage])(implicit ctx: Ctx.Owner) = Rx {
    select(onchange := { (e: Event) =>
      val value = e.target.asInstanceOf[HTMLSelectElement].value
      state.viewPage() = ViewPage.fromString(value)
      sendEvent("view", "select", value)
    }, pages.map { page =>
      val attrs = if (page == state.viewPage()) Seq(selected) else Seq.empty
      option(page.toString, value := ViewPage.toString(page))(attrs: _*).render
    }).render
  }

  def devPeek(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val show = Var(false)
    val activeDisplay = display := show.map(if (_) "block" else "none")
    val inactiveDisplay = display := show.map(if (_) "none" else "block")

    val baseDiv = div(position.fixed, top := 100, right := 0, boxSizing.`border-box`,
      padding := "5px", backgroundColor := "rgba(248,240,255,0.95)", border := "1px solid #ECD7FF")

    div(
      baseDiv(
        inactiveDisplay,
        "DevView",
        css("transform") := "rotate(-90deg) translate(0,-100%)",
        css("transform-origin") := "100% 0",
        borderBottom := "none",
        cursor.pointer,
        onclick := { () => show() = true }
      ),
      baseDiv(
        activeDisplay,
        div("x", cursor.pointer, float.right, onclick := { () => show() = false }),
        DevView(state),
        borderRight := "none"
      )
    )
  }

  def feedbackForm(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val lockToGroup = state.viewConfig.now.lockToGroup

    //TODO: Don't hardcode feedback postId
    val feedbackPostId = PostId("82")

    val show = Var(false)
    val activeDisplay = display := show.map(if (_) "block" else "none")
    val inactiveDisplay = display := show.map(if (_) "none" else "block")

    def submitInsert(field: HTMLTextAreaElement) = {
      val text = field.value
      //TODO better handling of missing(?) feedbacknode
      val newPost = Post.newId(text)
      state.persistence.addChanges(addPosts = Set(newPost), addContainments = Set(Containment(feedbackPostId, newPost.id)))
      field.value = ""
      sendEvent("feedback", "submit", "")
      false
    }
    val feedbackField = textareaWithEnter(submitInsert)(
      rows := 5,
      cols := 30,
      placeholder := "Missing features? Suggestions? You found a bug? What do you like? What is annoying?"
    ).render

    show.foreach {
      if (_)
        setTimeout(100) {
          feedbackField.focus()
        }
    }

    val feedbackForm = form(
      feedbackField, br(),
      if (!lockToGroup) button("show all feedback", tpe := "button", float.right, onclick := { () => state.graphSelection() = GraphSelection.Union(Set(feedbackPostId)) }) else span(),
      input (tpe := "submit", value := "submit"),
      onsubmit := { () =>
        submitInsert(feedbackField)
        false
      }
    )

    div(
      div(
        inactiveDisplay,
        position.fixed, bottom := 250, right := 0, boxSizing.`border-box`,
        padding := "5px", background := "#F8F8F8", border := "1px solid #DDD", borderBottom := "none",
        "Give short feedback",
        css("transform") := "rotate(-90deg) translate(0,-100%)",
        css("transform-origin") := "100% 0",
        cursor.pointer,
        onclick := { () => show() = true }
      ),
      div(
        activeDisplay,
        position.fixed, bottom := 150, right := 0, boxSizing.`border-box`,
        padding := "5px", background := "#F8F8F8", border := "1px solid #DDD", borderRight := "none",
        div("x", cursor.pointer, float.right, onclick := { () => show() = false }),
        div("Feedback"),
        feedbackForm
      )
    )
  }

  def invitePopup(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val show = Var(false)
    val activeDisplay = display := show.map(if (_) "block" else "none")
    val inactiveDisplay = display := show.map(if (_) "none" else "block")

    div(
      div(
        inactiveDisplay,
        button("invite...", onclick := { () => show() = !show.now })
      ),
      div(
        activeDisplay,
        zIndex := 100,
        cls := "shadow",
        position.fixed, top := 5, left := 5, boxSizing.`border-box`,
        padding := "15px", background := "#F8F8F8", border := "1px solid #DDD",
        div("x", cursor.pointer, float.right, onclick := { () => show() = false }),
        h4("Invite participants to group"),
        groupSelector(state),
        inviteUserToGroupField(state),
        currentGroupInviteLink(state).map(linkOpt => linkOpt.map{ link =>
          val linkField = input(tpe := "text", value := link, readonly).render
          div(
            linkField,
            button("copy", onclick := { () => linkField.select(); document.execCommand("Copy") })
          )
        }.getOrElse(span()).render)
      )
    )
  }

  def syncStatus(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    val mode = state.syncMode()
    val persistStatus = state.persistence.status()
    val hasEvents = state.eventCache.hasEvents()

    div(
      select {
        SyncMode.all.map { m =>
          val s = m.toString
          val opt = option(s, value := s)
          if (mode == m) opt(selected) else opt
        }
      }(
        onchange := { (e: Event) =>
          val value = e.target.asInstanceOf[HTMLSelectElement].value
          SyncMode.fromString.lift(value).foreach { mode =>
            state.syncMode() = mode
          }
        }
      ),
      persistStatus.toString,
      " | ",
      if (hasEvents) "new-events" else "up-to-date"
    ).render
  }

  def topBar(state: GlobalState, viewPages: List[ViewPage])(implicit ctx: Ctx.Owner) = {
    val lockToGroup = state.viewConfig.now.lockToGroup
    if (lockToGroup) {
      div(
        padding := "5px", background := "#F8F8F8", borderBottom := "1px solid #DDD",
        display.flex, alignItems.center, justifyContent.spaceBetween,

        div(
          display.flex, alignItems.center, justifyContent.flexStart,

          upButton(state),
          focusedParents(state)
        ),

        if (viewPages.size > 1) div("view: ")(viewSelection(state, viewPages))
        else div()
      )
    } else {
      div(
        padding := "5px", background := "#F8F8F8", borderBottom := "1px solid #DDD",
        display.flex, alignItems.center, justifyContent.spaceBetween,

        div(
          display.flex, alignItems.center, justifyContent.flexStart,

          upButton(state),
          focusedParents(state),
          groupSelector(state),
          invitePopup(state),
          newGroupButton(state)
        ),

        if (viewPages.size > 1) div("view: ")(viewSelection(state, viewPages))
        else div(),

        syncStatus(state),

        div(
          display.flex, alignItems.center, justifyContent.flexEnd,
          UserView.topBarUserStatus(state)
        )
      )
    }
  }

  def bottomBar(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      padding := "5px", background := "#F8F8F8", borderTop := "1px solid #DDD"
    )
  }

  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val viewPages = (
      ViewPage.Graph -> (() => GraphView(state, disableSimulation)) ::
      ViewPage.List -> (() => TreeView(state)) ::
      ViewPage.Article -> (() => ArticleView(state)) ::
      ViewPage.Code -> (() => CodeView(state)) ::
      ViewPage.Chat -> (() => ChatView(state)) ::
      ViewPage.Board -> (() => BoardView(state)) ::
      Nil
    )

    val viewPagesMap: Map[ViewPage, () => TypedTag[Element]] = viewPages.toMap

    // https://jsfiddle.net/MadLittleMods/LmYay/ (flexbox 100% height: header, content, footer)
    // https://jsfiddle.net/gmxf11u5/ (flexbox 100% height: header, content (absolute positioned elements), footer)
    div(
      width := "100%",
      height := "100%",

      display.flex,
      flexDirection.column,
      justifyContent.flexStart,
      alignItems.stretch,
      alignContent.stretch,

      topBar(state, viewPages.map(_._1))(ctx)(minHeight := "min-content"),
      state.viewPage.map { x => viewPagesMap(x)()(flex := "1", overflow.auto).render },
      // bottomBar (state),

      feedbackForm (state),
      DevOnly { devPeek(state) }
    )
  }
}
