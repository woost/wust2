package frontend.views

import scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.d3v4
import org.scalajs.dom._
import frontend.Client
import frontend.Color._
import graph._
import autowire._
import boopickle.Default._
import scala.xml.Node
import mhtml.emptyHTML

object Views {
  def post(p: Post) = genericPost(p, postDefaultColor, None)

  def genericPost(post: Post, color: d3v4.Color, afterTitle: Option[Node]) =
    //TODO: share style with graphview
    <div style={ s"padding: 3px 5px; margin: 3px; border-radius: 3px; max-width: 10em; border: 1px solid #444; background-color: ${color.toString}" }>
      { post.title }
      { afterTitle.getOrElse(emptyHTML) }
    </div>

  // times symbol
  def parents(containsIds: Seq[AtomId], graph: Graph) =
    <div style="display: flex">
      {
        containsIds.map { containsId =>
          val contains = graph.containments(containsId)
          val parent = graph.posts(contains.parentId)
          genericPost(parent, baseColor(parent.id), Some(
            <span onclick={ (_: Event) => Client.api.deleteContainment(contains.id).call(); () } style="padding: 0 0 0 3px; cursor: pointer">
              ×
            </span>
          ))
        }
      }
    </div>
}
