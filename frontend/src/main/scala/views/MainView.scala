package wust.frontend.views

import autowire._
import boopickle.Default._
import org.scalajs.dom.{Event, document, console}
import org.scalajs.dom.window.location
import wust.util.tags._
import rx._
import rxext._
import wust.frontend.Color._
import wust.frontend.views.graphview.GraphView
import wust.frontend.{DevOnly, GlobalState}
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import wust.ids._
import wust.api._
import wust.graph._
import wust.frontend.{RichPostFactory, Client}
import wust.util.EventTracker.sendEvent
import scala.util.Try
import scalaz.Tag
import scala.scalajs.js.timers.setTimeout

import scalatags.JsDom.all._
import scalatags.rx.all._

//TODO: let scalatagst-rx accept Rx(div()) instead of only Rx{(..).render}
object MainView {
  def upButton(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    //TODO: handle containment cycles
    (state.graphSelection() match {
      case GraphSelection.Root => span()
      case GraphSelection.Union(parentIds) =>
        val newParentIds = parentIds.flatMap(state.rawGraph().parents)
        val newParentNames = newParentIds.map(state.rawGraph().postsById(_).title).mkString(", ")
        val buttonTitle = if (newParentIds.nonEmpty) s"Focus $newParentNames" else "Remove Focus"
        button("↑", padding := "1px", title := buttonTitle,
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
        Views.post(post)(
          backgroundColor := baseColor(post.id).toString
        // ,span(
        //   "×",
        //   padding := "0 0 0 3px",
        //   cursor.pointer,
        //   onclick := { () => state.graphSelection.updatef { _ remove parentId } }
        // )
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
      Client.api.addGroup().call().foreach { group =>
        state.selectedGroupId() = Option(group)
      }
      sendEvent("group", "created", "collaboration")
    }).render
  }

  def inviteUserToGroupField(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    (if (state.selectedGroupId().isDefined) {
      val field = input(placeholder := "invite user by name").render
      form(field, input(tpe := "submit", value := "invite"), onsubmit := { () =>
        val userName = field.value
        state.selectedGroupId().foreach(Client.api.addMemberByName(_, userName).call().foreach { success =>
          println(success)
          field.value = ""
        })

        sendEvent("group", "invitebyname", "collaboration")
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
            case Some(token) => inviteLink() = Some(s"${location.href.split("#").head}#${ViewConfig.toHash(state.viewConfig())}&invite=$token")
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
    }, pages.map { page =>
      val attrs = if (page == state.viewPage()) Seq(selected) else Seq.empty
      option(page.toString, value := ViewPage.toString(page))(attrs: _*).render
    }).render
  }

  def feedbackForm(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    //TODO: Don't hardcode feedback postId
    val feedbackPostId = PostId("82")

    val feedbackField = textarea(
      rows := 5,
      cols := 30,
      placeholder := "Missing features? Suggestions? You found a bug? What do you like? What is annoying?"
    ).render

    val show = Var(false)
    val activeDisplay = display := show.map(if (_) "block" else "none")
    val inactiveDisplay = display := show.map(if (_) "none" else "block")

    show.foreach {
      if (_)
        setTimeout(300) {
          feedbackField.focus()
        }
    }

    div(
      div(
        inactiveDisplay,
        position.fixed, bottom := 250, right := 0, boxSizing.`border-box`,
        padding := "5px", background := "rgba(247,247,247,0.8)", border := "1px solid #DDD", borderBottom := "none",
        "Give short feedback",
        css("transform") := "rotate(-90deg) translate(0,-100%)",
        css("transform-origin") := "100% 0",
        cursor.pointer,
        onclick := { () => show() = true }
      ),
      div(
        activeDisplay,
        position.fixed, bottom := 150, right := 0, boxSizing.`border-box`,
        padding := "5px", background := "rgba(247,247,247,0.8)", border := "1px solid #DDD", borderRight := "none",
        div("x", cursor.pointer, float.right, onclick := { () => show() = false }),
        div("Feedback"),
        form(
          feedbackField, br(),
          button("show all feedback", tpe := "button", float.right, onclick := { () => state.graphSelection() = GraphSelection.Union(Set(feedbackPostId)) }),
          input (tpe := "submit", value := "submit"),
          onsubmit := { () =>
            val text = feedbackField.value
            if (text.nonEmpty) {
              Client.api.addPost(Post.newId(text), GraphSelection.Union(Set(feedbackPostId)), groupId = None).call().foreach { success =>
                if (success)
                  feedbackField.value = ""
                else {
                  Client.api.addPost(Post.newId(text), GraphSelection.Root, groupId = None).call().foreach { success =>
                    if (success)
                      feedbackField.value = ""
                  }
                }
              }
              sendEvent("feedback", "submit", "api")
            }
            false
          }
        )
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
        position.fixed, top := 5, left := 5, boxSizing.`border-box`,
        padding := "15px", background := "rgba(247,247,247,0.9)", border := "1px solid #DDD",
        div("x", cursor.pointer, float.right, onclick := { () => show() = false }),
        h4("Invite participants to group"),
        groupSelector(state),
        inviteUserToGroupField(state),
        currentGroupInviteLink(state).map(linkOpt => linkOpt.map{ link =>
          val linkField = input(tpe := "text", value := link, size := (link.size + 1), readonly).render
          div(
            linkField,
            button("copy", onclick := { () => linkField.select(); document.execCommand("Copy") })
          )
        }.getOrElse(span()).render)
      )
    )
  }

  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val router = new ViewPageRouter(state.viewPage)

    val viewPages =
      ViewPage.Graph -> GraphView(state, disableSimulation) ::
        ViewPage.Tree -> TreeView(state) ::
        Nil

    div(
      fontFamily := "sans-serif",

      div(
        position.fixed, width := "100%", top := 0, left := 0, boxSizing.`border-box`,
        padding := "5px", background := "rgba(247,247,247,0.8)", borderBottom := "1px solid #DDD",
        display.flex, alignItems.center, justifyContent.spaceBetween,

        div(
          display.flex, alignItems.center, justifyContent.flexStart,

          upButton(state),
          focusedParents(state),
          groupSelector(state),
          invitePopup(state),
          newGroupButton(state)
        ),

        if (viewPages.size > 1) div("view: ")(viewSelection(state, viewPages.map(_._1)))
        else div(),

        div(
          display.flex, alignItems.center, justifyContent.flexEnd,

          UserView.topBarUserStatus(state)
        )
      ),

      router.map(viewPages),

      feedbackForm(state),

      div(
        position.fixed, width := "100%", bottom := 0, left := 0, boxSizing.`border-box`,
        padding := "5px", background := "rgba(247,247,247,0.8)", borderTop := "1px solid #DDD",
        AddPostForm(state)
      ),

      DevOnly { DevView(state) }
    )
  }
}
