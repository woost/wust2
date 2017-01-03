package frontend

import diode._
import diode.react._

import graph._

case class RootModel(
  graph: Graph = Graph.empty,
  respondingTo: Option[AtomId] = None
)

case class SetGraph(graph: Graph) extends Action
case class AddPost(post: Post) extends Action
case class RemovePost(id: AtomId) extends Action
case class AddRespondsTo(respondsTo: RespondsTo) extends Action
case class SetRespondingTo(target: Option[AtomId]) extends Action

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  def initialModel = RootModel()

  val globalHandler = new ActionHandler(zoomRW(m => m)((m, v) => v)) {
    override def handle = {
      case SetRespondingTo(targetOpt) => updated(value.copy(respondingTo = targetOpt))
    }
  }

  val graphHandler = new ActionHandler(zoomRW(_.graph)((m, v) => m.copy(graph = v))) {
    override def handle = {
      case SetGraph(graph) => updated(graph)
      case AddPost(post) =>
        updated(value.copy(
          posts = value.posts + (post.id -> post)
        ))
      case RemovePost(id) =>
        updated(value.remove(id))
      case AddRespondsTo(respondsTo) =>
        updated(value.copy(
          respondsTos = value.respondsTos + (respondsTo.id -> respondsTo)
        ))
    }
  }
  override val actionHandler = composeHandlers(globalHandler, graphHandler)
}
