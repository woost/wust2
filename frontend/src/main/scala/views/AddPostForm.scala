package frontend.views

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
import graph._
import org.scalajs.d3v4
import frontend._
import frontend.Color._
import rx._

object AddPostForm {
  def editLabel(graph: Rx[Graph], editedPostId: WriteVar[Option[PostId]], postId: PostId)(implicit ctx: Ctx.Owner) = {
    div(
      "Edit Post:",
      button("×", onclick := { (_: Event) => editedPostId := None }),
      responseLabel(graph, postId)
    )
  }

  def responseLabel(graph: Rx[Graph], postId: PostId)(implicit ctx: Ctx.Owner) = {
    div(
      Rx { Views.parents(postId, graph()).render },
      Rx { Views.post(graph().posts(postId)).render }
    )
  }

  val newLabel = div("New Post:")

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import state.{graph => rxGraph, mode => rxMode, editedPostId => rxEditedPostId}

    //TODO: onattached -> store domnode -> focus
    rxMode.foreach { mode =>
      val input = document.getElementById("addpostfield").asInstanceOf[HTMLInputElement]
      if (input != null) {
        mode match {
          case EditMode(postId) => input.value = rxGraph.now.posts(postId).title
          case _ =>
        }
        input.focus()
      }
    }

    div(
      {
        val label: InteractionMode => dom.html.Div = {
          case EditMode(postId) => editLabel(rxGraph, rxEditedPostId, postId).render
          case FocusMode(postId) => responseLabel(rxGraph, postId).render
          case _ => newLabel.render
        }
        rxMode.map(label(_))
      },
      {
        //TODO: pattern matching is broken inside Rx
        def action(text: String, graph: Graph, mode: InteractionMode): Future[Boolean] = mode match {
          case EditMode(postId) => Client.api.updatePost(graph.posts(postId).copy(title = text)).call()
          case FocusMode(postId) => Client.api.respond(postId, text).call().map(_ => true)
          case _ => Client.api.addPost(text).call().map(_ => true)
        }
        Rx {
          input(`type` := "text", id := "addpostfield", onkeyup := { (e: KeyboardEvent) =>
            val input = e.target.asInstanceOf[HTMLInputElement]
            val text = input.value
            if (e.keyCode == KeyCode.Enter && text.trim.nonEmpty) {
              action(text, rxGraph.now, rxMode.now).foreach { success =>
                if (success) {
                  input.value = ""
                  rxEditedPostId := None
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
