package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.ids._
import wust.util.Pipe
import wust.graph.Graph

import scala.concurrent.{ExecutionContext, Future}

// TODO: crashes coverage @derive(copyF)
case class State(auth: Option[JWTAuthentication], graph: Graph) {
  val user = auth.map(_.user)
  def copyF(auth: Option[JWTAuthentication] => Option[JWTAuthentication] = identity, graph: Graph => Graph = identity) = copy(auth = auth(this.auth), graph = graph(this.graph))
}
object State {
  def initial = State(auth = None, graph = Graph.empty)
}

object StateInterpreter {
  def applyEvent(state: State, event: ApiEvent): State = {
    state.copyF(graph = GraphUpdate.onEvent(_, event))
  }

  def allowsEvent(state: State, event: ApiEvent): Boolean = event match {
    case NewPost(_) => true
    case UpdatedPost(_) => true
    case NewConnection(_) => true
    case NewContainment(_) => true
    case NewOwnership(_) => true
    case NewUser(_) => true
    case NewGroup(edge) => true //TODO: for who?
    case NewMembership(edge) =>
      state.auth.map(_.user.id == edge.userId).getOrElse(false) || state.graph.groupsById.isDefinedAt(edge.groupId)
    case DeletePost(_) => true
    case DeleteConnection(_) => true
    case DeleteContainment(_) => true
    case _ => true//false
  }

  def filterValid(state: State): State = state.copyF(auth = _.filterNot(JWT.isExpired))
}

class StateInterpreter(db: Db) {
  def stateEvents(state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] = {
    Seq (
      state.auth
        .map(_.toAuthentication |> LoggedIn)
        .getOrElse(LoggedOut)
    ).map(Future.successful _) ++ Seq (
      db.graph.getAllVisiblePosts(state.user.map(_.id))
        .map(forClient(_).consistent)
        .map(ReplaceGraph(_))
    )
  }

  def stateChangeEvents(prevState: State, state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] =
    (prevState.auth == state.auth) match {
      case true => Seq.empty
      case false => stateEvents(state)
    }
}
