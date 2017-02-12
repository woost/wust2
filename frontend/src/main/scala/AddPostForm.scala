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
  def component(rxGraph: Rx[Graph], focusedPostId: SourceVar[Option[AtomId], Option[AtomId]]) = {
    def graph = rxGraph.value
    <div>
      {
        focusedPostId.target.map {
          case Some(postId) =>
            val post = graph.posts(postId)
            <div>
              { views.parents(graph.incidentParentContains(post.id).toSeq, graph) }
              { views.post(post) }
            </div>
          case None => <div>New Post:</div>
        }
      }
      {
        //TODO: onattached -> store domnode -> focus
        focusedPostId.foreach { _ =>
          val input = document.getElementById("addpostfield").asInstanceOf[raw.HTMLInputElement]
          if (input != null) input.focus()
        }
        ""
      }
      <input type="text" id="addpostfield" onkeyup={ (e: KeyboardEvent) =>
        val input = e.target.asInstanceOf[raw.HTMLInputElement]
        val text = input.value
        if (e.keyCode == KeyCode.Enter && text.trim.nonEmpty) {
          val fut = focusedPostId.value match {
            case Some(postId) => Client.api.respond(postId, text).call().map(_.isDefined)
            case None => Client.api.addPost(text).call().map(_ => true)
          }

          fut.foreach { success =>
            if (success) input.value = ""
          }
        }
        ()
      }/>
    </div>
  }
}
