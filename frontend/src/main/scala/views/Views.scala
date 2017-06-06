package wust.frontend.views

import autowire._
import boopickle.Default._
import wust.frontend.{GlobalState, Client}
import wust.frontend.Color._
import wust.graph._
import wust.ids._
import wust.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scalatags.JsDom.all._

object Views {
  def post(post: Post) = div(
    post.title,
    maxWidth := "10em",
    wordWrap := "break-word",
    padding := "3px 5px",
    border := "1px solid #444",
    borderRadius := "3px"
  )

  def parents(state: GlobalState, postId: PostId, graph: Graph) = {
    val containment = graph.incidentParentContainments(postId).toSeq
    div(
      display.flex,
      containment.map { containment =>
        val parent = graph.postsById(containment.parentId)
        post(parent)(
          backgroundColor := baseColor(parent.id).toString,
          span(
            "×",
            padding := "0 0 0 3px",
            cursor.pointer,
            onclick := { () => state.persistence.addChanges(delContainments = Set(containment)) }
          )
        )
      }
    )
  }
}
