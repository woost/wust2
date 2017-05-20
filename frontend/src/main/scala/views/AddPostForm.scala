package wust.frontend.views

import autowire._
import boopickle.Default._
import org.scalajs.dom
import org.scalajs.dom.{Event, KeyboardEvent, document}
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}
import scala.scalajs.js.timers.setTimeout
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
import wust.util.EventTracker.sendEvent

object AddPostForm {
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

    val inputfield = input(`type` := "text").render
    rxMode.foreach { mode =>
      mode match {
        case EditMode(postId) => inputfield.value = rxDisplayGraph.now.graph.postsById(postId).title
        case _ => inputfield.value = ""
      }
      setTimeout(100) { inputfield.focus() } //TODO: why is this timeout hack needed?
    }

    def label(mode: InteractionMode, graph: Graph) = mode match {
      case EditMode(postId) => editLabel(graph, rxEditedPostId, postId)
      case FocusMode(postId) => responseLabel(graph, postId)
      case _ => newLabel
    }

    def action(text: String, selection: GraphSelection, groupId: Option[GroupId], graph: Graph, mode: InteractionMode): Future[Boolean] = mode match {
      case EditMode(postId) =>
        DevPrintln(s"\nUpdating Post $postId: $text")
        rxEditedPostId() = None
        val result = Client.api.updatePost(graph.postsById(postId).copy(title = text)).call()
        sendEvent("post", "update", "api")
        result
      case FocusMode(postId) =>
        DevPrintln(s"\nRepsonding to $postId: $text")
        val result = Client.api.respond(postId, text, selection, groupId).call().map(_ => true)
        sendEvent("post", "respond", "api")
        result
      case _ =>
        DevPrintln(s"\nCreating Post: $text")
        val result = Client.api.addPost(text, selection, groupId).call().map(_ => true)
        sendEvent("post", "create", "api")
        result
    }

    div(
      {
        Rx {
          div(
            display.flex,
            div(
              label(rxMode(), rxDisplayGraph().graph),
              {
                form(
                  inputfield,
                  onsubmit := { () =>
                    val text = inputfield.value
                    if (text.trim.nonEmpty) {
                      action(text, state.graphSelection(), state.selectedGroupId(), rxDisplayGraph.now.graph, rxMode.now).foreach { success =>
                        if (success) {
                          inputfield.value = ""
                        }
                      }
                    }
                    false
                  }
                )
              }
            )
          ).render
        }
      }
    )
  }
}
