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
  def component(rxGraph: Rx[Graph], target: Rx[Option[AtomId]]) = {
    def graph = rxGraph.value
    <div>
      {
        target.map {
          case Some(postId) =>
            val post = graph.posts(postId)
            <div>
              { views.parents(graph.incidentParentContains(post.id).toSeq, graph) }
              { views.post(post) }
            </div>
          case None => <div>New Post:</div>
        }
      }
      <input type="text" onkeyup={ (e: KeyboardEvent) =>
        val input = e.target.asInstanceOf[raw.HTMLInputElement]
        val text = input.value
        if (e.keyCode == KeyCode.Enter && text.trim.nonEmpty) {
          val fut = target.value match {
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
