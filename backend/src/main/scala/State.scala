package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.graph._
import wust.ids._

case class State(auth: Authentication, graph: Graph) {
  override def toString = s"State($auth, posts# ${graph.posts.size})"
}
object State {
  def initial = State(auth = Authentication.Assumed.fresh, graph = Graph.empty)

  def applyEvents(state: State, events: Seq[ApiEvent]): State = {
    events.foldLeft(state)((state, event) => event match {
      case ev: ApiEvent.GraphContent => state.copy(graph = EventUpdate.applyEventOnGraph(state.graph, ev))
      case ev: ApiEvent.AuthContent => state.copy(auth = EventUpdate.createAuthFromEvent(ev))
    })
  }
}
