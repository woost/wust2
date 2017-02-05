package frontend

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import org.scalajs.d3v4
import Color._
import graph._
import autowire._
import boopickle.Default._

package object views {
  def post(p: Post) = genericPost((p, postDefaultColor, None))

  val genericPost = ReactComponentB[(Post, d3v4.Color, Option[ReactElement])]("Post")
    .render_P {
      case (post, color, afterTitle) =>
        <.div(
          post.title,
          afterTitle,
          //TODO: share style with graphview
          ^.padding := "3px 5px",
          ^.margin := "3px",
          ^.borderRadius := "3px",
          ^.maxWidth := "10em",
          ^.border := "1px solid #444",
          ^.backgroundColor := color.toString
        )
    }
    .build

  val parents = ReactComponentB[(Seq[AtomId], Graph)]("Parents")
    .render_P {
      case (containsIds, graph) =>
        <.div(
          ^.display := "flex",
          containsIds.map { containsId =>
            val contains = graph.containments(containsId)
            val parent = graph.posts(contains.parentId)
            genericPost((parent, baseColor(parent.id), Some(
              <.span(
                " \u00D7", // times symbol
                ^.padding := "0 0 0 3px",
                ^.cursor := "pointer",
                ^.onClick ==> ((e: ReactEventI) => Callback { Client.api.deleteContainment(contains.id).call() })
              )
            )))
          }
        )
    }
    .build
}
