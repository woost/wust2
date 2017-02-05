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
  focusedPost: Option[AtomId] = None,
  activeTab: Tab = Tab.Graph
)

case class SetGraph(graph: Graph) extends Action
case class SwitchTab(tab: Tab) extends Action
case class SetFocusedPost(target: Option[AtomId]) extends Action

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  def initialModel = RootModel()

  val globalHandler = new ActionHandler(zoomRW(m => m)((m, v) => v)) {
    override def handle = {
      case SetFocusedPost(targetOpt) => updated(value.copy(focusedPost = targetOpt))
      case SwitchTab(active: Tab) => updated(value.copy(activeTab = active))
    }
  }

  val graphHandler = new ActionHandler(zoomTo(_.graph)) {
    override def handle = {
      case SetGraph(graph) => updated(graph)
      //TODO: update focusedPost
      case NewPost(post) =>
        updated(value.copy(
          posts = value.posts + (post.id -> post)
        ))
      case DeletePost(id) =>
        updated(value.removePost(id))
      //TODO: update focusedPost
      case DeleteConnection(id) =>
        updated(value.removeConnection(id))
      case DeleteContainment(id) =>
        updated(value.removeContainment(id))
      case NewConnection(connects) =>
        updated(value.copy(
          connections = value.connections + (connects.id -> connects)
        ))
      case NewContainment(contains) =>
        updated(value.copy(
          containments = value.containments + (contains.id -> contains)
        ))
    }
  }
  override val actionHandler = composeHandlers(globalHandler, graphHandler)
}
