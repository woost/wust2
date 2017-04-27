package wust.frontend.views

import autowire._
import boopickle.Default._
import org.scalajs.dom
import org.scalajs.dom.{Event, KeyboardEvent, document}
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}
import rx._
import wust.frontend._
import wust.ids._
import wust.graph._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try
import scalatags.JsDom.all._
import scalatags.rx.all._
import collection.breakOut

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
    import state.{displayGraph => rxDisplayGraph, editedPostId => rxEditedPostId, mode => rxMode}

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

    def action(text: String, selection: GraphSelection, groupId: GroupId, graph: Graph, mode: InteractionMode): Future[Boolean] = mode match {
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
                val groupId = state.selectedGroupId()
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

            div(" in group: ", state.rawGraph.map { graph =>
              select {
                // only looking at memberships is sufficient to list groups, because the current user is member of each group
                val groupsIdsWithNames: Seq[(GroupId, String)] = (graph.usersByGroupId - publicGroupId).mapValues(_.map(userId => graph.usersById(userId).name).mkString(", ")).toSeq
                ((publicGroupId, "public") +: groupsIdsWithNames).map {
                  case (groupId, name) =>
                    val opt = option(name, value := groupId)
                    if (groupId == state.selectedGroupId()) opt(selected)
                    else opt
                }
              }(
                onchange := { (e: Event) =>
                  val groupId = e.target.asInstanceOf[HTMLSelectElement].value.toLong
                  state.selectedGroupId() = groupId
                }
              ).render
            }),

            button("new group", onclick := { () =>
              Client.api.addUserGroup().call().foreach { userGroup =>
                state.selectedGroupId() = userGroup.id
              }
            }),
            if (state.selectedGroupId() != publicGroupId) {
              val field = input(placeholder := "invite user by id").render
              div(field, button("invite", onclick := { () =>
                Try(field.value.toLong).foreach { userId =>
                  println(s"group: ${state.selectedGroupId()}, user: $userId")
                  Client.api.addMember(state.selectedGroupId(), userId).call().foreach { _ =>
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
