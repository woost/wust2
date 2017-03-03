package frontend.views

import scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.d3v4
import org.scalajs.dom
import frontend.Client
import frontend.Color._
import graph._
import autowire._
import boopickle.Default._

import rx._
import scalatags.generic.Modifier
import scalatags.rx.all._
import scalatags.JsDom.all._

object Views {
  def post(post: Post, attrs: Modifier[dom.raw.Element]*) = {
    //TODO: share style with graphview
    val defaults = Seq(
      style := s"padding: 3px 5px; margin: 3px; border-radius: 3px; max-width: 10em; border: 1px solid #444"
    )
    div((defaults ++ attrs): _*)(p(post.title))
  }

  // times symbol
  def parents(containsIds: Seq[AtomId], graph: Graph) =
    div(
      style := "display: flex",
      containsIds.map { containsId =>
        val contains = graph.containments(containsId)
        val parent = graph.posts(contains.parentId)
        post(parent, color := baseColor(parent.id).toString)(
          span(
            onclick := { () => Client.api.deleteContainment(contains.id).call(); () },
            style := "padding: 0 0 0 3px; cursor: pointer",
            "×"
          )
        )
      }
    )
}
