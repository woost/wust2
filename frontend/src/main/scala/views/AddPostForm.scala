package wust.frontend.views

import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import dom.raw.HTMLInputElement
import dom.raw.HTMLSelectElement
import dom.Event
import dom.document
import dom.KeyboardEvent
import concurrent.Future

import autowire._
import boopickle.Default._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

import scalatags.JsDom.all._
import scalatags.rx.all._
import org.scalajs.d3v4
import rx._

import wust.graph._
import wust.frontend._, Color._

object AddPostForm {
  //TODO: use public groupid constant from config
  val publicGroupId = 1L
  def editLabel(graph: Graph, editedPostId: WriteVar[Option[PostId]], postId: PostId) = {
    div(
      "Edit Post:", button("cancel", onclick := { (_: Event) => editedPostId() = None }),
      Views.parents(postId, graph)
    )
  }

  def responseLabel(graph: Graph, postId: PostId) = {
    div(
      Views.parents(postId, graph),
      Views.post(graph.postsById(postId)),
      "respond: "
    )
  }

  val newLabel = div("New Post:")

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import state.{displayGraph => rxDisplayGraph, mode => rxMode, editedPostId => rxEditedPostId}

    rxMode.foreach { mode =>
      val input = document.getElementById("addpostfield").asInstanceOf[HTMLInputElement]
      if (input != null) {
        mode match {
          case EditMode(postId) => input.value = rxDisplayGraph.now.graph.postsById(postId).title
          case _ =>
        }
        input.focus()
      }
    }

    def label(mode: InteractionMode, graph: Graph) = mode match {
      case EditMode(postId) => editLabel(graph, rxEditedPostId, postId)
      case FocusMode(postId) => responseLabel(graph, postId)
      case _ => newLabel
    }

    def action(text: String, selection: GraphSelection, groupId: Long, graph: Graph, mode: InteractionMode): Future[Boolean] = mode match {
      case EditMode(postId) =>
        DevPrintln(s"\nUpdating Post $postId: $text")
        Client.api.updatePost(graph.postsById(postId).copy(title = text)).call()
      case FocusMode(postId) =>
        DevPrintln(s"\nRepsonding to $postId: $text")
        Client.api.respond(postId, text, selection, groupId).call().map(_ => true)
      case _ =>
        DevPrintln(s"\nCreating Post: $text")
        Client.api.addPost(text, selection, groupId).call().map(_ => true)
    }

    div(
      {
        Rx {
          div(
            display.flex,
            div(
              label(rxMode(), rxDisplayGraph().graph),
              input(`type` := "text", id := "addpostfield", onkeyup := { (e: KeyboardEvent) =>
                val input = e.target.asInstanceOf[HTMLInputElement]
                val text = input.value
                val groupId = state.selectedGroup()
                if (e.keyCode == KeyCode.Enter && text.trim.nonEmpty) {
                  action(text, state.graphSelection(), groupId, rxDisplayGraph.now.graph, rxMode.now).foreach { success =>
                    if (success) {
                      input.value = ""
                      rxEditedPostId() = None
                    }
                  }
                }
                ()
              })
            ),

            div(" in group: ", state.currentGroups.map { gs =>
              select {
                val groupsIdsWithNames = (publicGroupId, "public") +: gs.map(g => (g.id, g.users.map(_.name).mkString(", ")))
                groupsIdsWithNames.map {
                  case (groupId, name) =>
                    val opt = option(name, value := groupId)
                    if (groupId == state.selectedGroup()) opt(selected)
                    else opt
                }
              }(
                onchange := { (e: Event) =>
                  val groupId = e.target.asInstanceOf[HTMLSelectElement].value.toLong
                  state.selectedGroup() = groupId
                }
              ).render
            }),

            button("new group", onclick := { () =>
              Client.api.addUserGroup().call().foreach { userGroup =>
                state.selectedGroup() = userGroup.id
              }
            }),
            if (state.selectedGroup() != publicGroupId) {
              val field = input(placeholder := "invite user by id").render
              div(field, button("invite", onclick := { () =>
                Try(field.value.toLong).foreach { userId =>
                  println(s"group: ${state.selectedGroup()}, user: ${userId}")
                  Client.api.addMember(state.selectedGroup(), userId).call().foreach { _ =>
                    field.value = ""
                  }
                }
              }))
            } else div()
          ).render
        }
      }
    )
  }
}
