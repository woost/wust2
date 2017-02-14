package frontend

import org.scalajs.dom._
import org.scalajs.dom.ext.KeyCode

import autowire._
import boopickle.Default._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import mhtml._
import graph._
import org.scalajs.d3v4
import Color._

object AddPostForm {
  class InteractionMode[F,E](val focus: Rx[Option[F]], val edit: Rx[Option[E]]) {
    val rx: Rx[InteractionMode[F,E]] = edit.flatMap(_ => focus.map(_ => this))
  }
  object InteractionMode {
    def unapply[F,E](mode: InteractionMode[F,E]): Option[(Option[F],Option[E])] = Some((mode.focus.value, mode.edit.value))
  }
  object FocusMode {
    def unapply[F,E](mode: InteractionMode[F,E]): Option[F] = Some(mode) collect {
      case InteractionMode(Some(f), None) => f
    }
  }
  object EditMode {
    def unapply[F,E](mode: InteractionMode[F,E]): Option[E] = Some(mode) collect {
      case InteractionMode(_, Some(e)) => e
    }
  }

  def component(rxGraph: Rx[Graph], focusedPostId: Rx[Option[AtomId]], editedPostId: SourceVar[Option[AtomId], Option[AtomId]]) = {
    val mode = new InteractionMode(focus = focusedPostId, edit = editedPostId)
    def graph = rxGraph.value

    def editLabel(postId: AtomId) = {
      <div>
        Edit Post: <button onclick={ (_: Event) => editedPostId := None }>x</button>
        { responseLabel(postId) }
      </div>
    }

    def responseLabel(postId: AtomId) = {
      val post = graph.posts(postId)
      <div>
        { views.parents(graph.incidentParentContains(post.id).toSeq, graph) }
        { views.post(post) }
      </div>
    }

    val newLabel = <div>New Post:</div>

    //TODO: onattached -> store domnode -> focus
    mode.rx.foreach { mode =>
      val input = document.getElementById("addpostfield").asInstanceOf[raw.HTMLInputElement]
      if (input != null) {
        mode match {
          case EditMode(postId) => input.value = graph.posts(postId).title
          case _ => input.value = ""
        }
        input.focus()
      }
    }

    <div>
      {
        mode.rx.map {
          case EditMode(postId) => editLabel(postId)
          case FocusMode(postId) => responseLabel(postId)
          case _ => newLabel
        }
      }
      <input type="text" id="addpostfield" onkeyup={ (e: KeyboardEvent) =>
        val input = e.target.asInstanceOf[raw.HTMLInputElement]
        val text = input.value
        if (e.keyCode == KeyCode.Enter && text.trim.nonEmpty) {
          val fut = mode match {
            case EditMode(postId) => Client.api.updatePost(graph.posts(postId).copy(title = text)).call()
            case FocusMode(postId) => Client.api.respond(postId, text).call().map(_.isDefined)
            case _ => Client.api.addPost(text).call().map(_ => true)
          }

          fut.foreach { success => if (success) {
            input.value = ""
            editedPostId := None
          }}
        }
        ()
      }/>
    </div>
  }
}
