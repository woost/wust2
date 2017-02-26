package frontend.views

import org.scalajs.dom._
import org.scalajs.dom.ext.KeyCode

import autowire._
import boopickle.Default._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import mhtml._
import graph._
import org.scalajs.d3v4
import frontend._
import frontend.Color._

object AddPostForm {
  def editLabel(graph: Graph, editedPostId: WriteVar[Option[AtomId]], postId: AtomId) = {
    <div>
      Edit Post:<button onclick={ (_: Event) => editedPostId := None }>×</button>
      { responseLabel(graph, postId) }
    </div>
  }

  def responseLabel(graph: Graph, postId: AtomId) = {
    val post = graph.posts(postId)
    <div>
      { Views.parents(graph.incidentParentContains(post.id).toSeq, graph) }
      { Views.post(post) }
    </div>
  }

  val newLabel = <div>New Post:</div>

  def component(state: GlobalState) = {
    import state._

    //TODO: onattached -> store domnode -> focus
    mode.foreach { mode =>
      val input = document.getElementById("addpostfield").asInstanceOf[raw.HTMLInputElement]
      if (input != null) {
        mode match {
          case EditMode(postId) => input.value = graph.value.posts(postId).title
          case _ =>
        }
        input.focus()
      }
    }

    <div>
      {
        mode.map {
          case EditMode(postId) => editLabel(graph.value, editedPostId, postId)
          case FocusMode(postId) => responseLabel(graph.value, postId)
          case _ => newLabel
        }
      }
      <input type="text" id="addpostfield" onkeyup={ (e: KeyboardEvent) =>
        val input = e.target.asInstanceOf[raw.HTMLInputElement]
        val text = input.value
        if (e.keyCode == KeyCode.Enter && text.trim.nonEmpty) {
          val fut = mode.value match {
            case EditMode(postId) => Client.api.updatePost(graph.value.posts(postId).copy(title = text)).call()
            case FocusMode(postId) => Client.api.respond(postId, text).call().map(_.isDefined)
            case _ => Client.api.addPost(text).call().map(_ => true)
          }

          fut.foreach { success =>
            if (success) {
              input.value = ""
              editedPostId := None
            }
          }
        }
        ()
      }/>
    </div>
  }
}
