package wust.backend

import derive.derive
import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.graph._
import wust.ids._

import scala.concurrent.{ExecutionContext, Future}

@derive(copyF)
case class State(auth: Authentication, graph: Graph) {
  override def toString = s"State($auth, posts# ${graph.posts.size})"
}
object State {
  def initial = State(auth = Authentication.None, graph = Graph.empty)

  private def authEventToAuth(event: ApiEvent.AuthContent): Authentication = event match {
    case ApiEvent.LoggedIn(auth) => auth
    case ApiEvent.LoggedOut => Authentication.None
  }

  def applyEvents(state: State, events: Seq[ApiEvent]): State = {
    events.foldLeft(state)((state, event) => event match {
      case ev: ApiEvent.GraphContent => state.copyF(graph = GraphUpdate.applyEvent(_, ev))
      case ev: ApiEvent.AuthContent => state.copy(auth = authEventToAuth(ev))
    })
  }

  def filterExpiredAuth(state: State): State = state.auth match {
    case auth: Authentication.Verified if JWT.isExpired(auth) => state.copy(auth = Authentication.None)
    case _ => state
  }
}
