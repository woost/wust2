package frontend

import diode._
import diode.react._

import graph._

case class RootModel(graph: Graph = Graph.empty)

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  def initialModel = RootModel()

  val graphHandler = new ActionHandler(zoomRW(_.graph)((m, v) => m.copy(graph = v))) {
    override def handle = {
      case SetGraph(graph) => updated(graph)
      case AddPost(post) =>
        updated(value.copy(
          posts = value.posts + (post.id -> post)
        ))
      case AddRespondsTo(respondsTo) =>
        updated(value.copy(
          respondsTos = value.respondsTos + (respondsTo.id -> respondsTo)
        ))
    }
  }
  override val actionHandler = composeHandlers(graphHandler)
}
