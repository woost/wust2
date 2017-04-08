package wust.frontend.views

import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import dom.raw.HTMLInputElement
import dom.Event
import dom.document
import dom.KeyboardEvent
import concurrent.Future

import autowire._
import boopickle.Default._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import scalatags.JsDom.all._
import scalatags.rx.all._
import org.scalajs.d3v4
import rx._

import wust.graph._
import wust.frontend._, Color._

object AddPostForm {
  def editLabel(graph: Graph, editedPostId: WriteVar[Option[PostId]], postId: PostId) = {
    div(
      "Edit Post:",
      button("×", onclick := { (_: Event) => editedPostId() = None }),
      responseLabel(graph, postId)
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

    //TODO: onattached -> store domnode -> focus
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

    def action(text: String, graph: Graph, mode: InteractionMode): Future[Boolean] = mode match {
      case EditMode(postId) =>
        DevPrintln(s"\nUpdating Post $postId: $text")
        Client.api.updatePost(graph.postsById(postId).copy(title = text)).call()
      case FocusMode(postId) =>
        DevPrintln(s"\nRepsonding to $postId: $text")
        Client.api.respond(postId, text).call().map(_ => true)
      case _ => Client.api.addPost(text).call().map(_ => true)
    }

    div(
      Rx { label(rxMode(), rxDisplayGraph().graph).render },
      {
        //TODO: pattern matching is broken inside Rx
        Rx {
          input(`type` := "text", id := "addpostfield", onkeyup := { (e: KeyboardEvent) =>
            val input = e.target.asInstanceOf[HTMLInputElement]
            val text = input.value
            if (e.keyCode == KeyCode.Enter && text.trim.nonEmpty) {
              action(text, rxDisplayGraph.now.graph, rxMode.now).foreach { success =>
                if (success) {
                  input.value = ""
                  rxEditedPostId() = None
                }
              }
            }
            ()
          }).render
        }
      }
    )
  }
}
