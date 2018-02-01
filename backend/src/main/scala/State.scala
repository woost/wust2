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

  private def authEventToAuth(event: ApiEvent.AuthContent): Authentication = event match {
    case ApiEvent.LoggedIn(auth) => auth
    case ApiEvent.AssumeLoggedIn(auth) => auth
  }

  def applyEvents(state: State, events: Seq[ApiEvent]): State = {
    events.foldLeft(state)((state, event) => event match {
      case ev: ApiEvent.GraphContent => state.copy(graph = GraphUpdate.applyEvent(state.graph, ev))
      case ev: ApiEvent.AuthContent => state.copy(auth = authEventToAuth(ev))
    })
  }
}
