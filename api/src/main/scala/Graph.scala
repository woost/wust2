package api

import scalajs.js
import scala.scalajs.js.annotation._

package object graph {
  //TODO: different types of ids to restrict RespondsTo in/out
  type AtomId = Long

  case class Graph(
    posts: Map[AtomId, Post],
    respondsTos: Map[AtomId, RespondsTo]
  )
  object Graph {
    def empty = Graph(Map.empty, Map.empty)
  }

  case class Post(id: AtomId, title: String) /*{
    @JSExport var x: js.UndefOr[Double] = js.undefined
    @JSExport var y: js.UndefOr[Double] = js.undefined
  }*/
  case class RespondsTo(id: AtomId, in: AtomId, out: AtomId) {
    @JSExport def source = in
    @JSExport def target = out
  }
}
