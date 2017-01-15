package frontend

import diode._
import diode.react._

import graph._, api._

sealed trait Tab
object Tab {
  case object Graph extends Tab
  case object List extends Tab
}

case class RootModel(
  graph: Graph = Graph.empty,
  respondingTo: Option[AtomId] = None,
  activeTab: Tab = Tab.Graph
)

case class SetGraph(graph: Graph) extends Action
case class SwitchTab(tab: Tab) extends Action
case class SetRespondingTo(target: Option[AtomId]) extends Action

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  def initialModel = RootModel()

  val globalHandler = new ActionHandler(zoomRW(m => m)((m, v) => v)) {
    override def handle = {
      case SetRespondingTo(targetOpt) => updated(value.copy(respondingTo = targetOpt))
      case SwitchTab(active: Tab) => updated(value.copy(activeTab = active))
    }
  }

  val graphHandler = new ActionHandler(zoomRW(_.graph)((m, v) => m.copy(graph = v))) {
    override def handle = {
      case SetGraph(graph) => updated(graph)
      case NewPost(post) =>
        updated(value.copy(
          posts = value.posts + (post.id -> post)
        ))
      case DeletePost(id) =>
        updated(value.remove(id))
      case NewConnects(respondsTo) =>
        updated(value.copy(
          respondsTos = value.respondsTos + (respondsTo.id -> respondsTo)
        ))
    }
  }
  override val actionHandler = composeHandlers(globalHandler, graphHandler)
}
