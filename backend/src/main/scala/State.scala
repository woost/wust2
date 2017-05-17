package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.ids._
import wust.util.Pipe
import wust.graph._

import scala.concurrent.{ ExecutionContext, Future }

// TODO: crashes coverage @derive(copyF)
case class State(auth: Option[JWTAuthentication], graph: Graph) {
  val user = auth.map(_.user)
  def copyF(auth: Option[JWTAuthentication] => Option[JWTAuthentication] = identity, graph: Graph => Graph = identity) = copy(auth = auth(this.auth), graph = graph(this.graph))
}
object State {
  def initial = State(auth = None, graph = Graph.empty)
}

object StateInterpreter {
  def onEvent(state: State, event: ApiEvent): State = {
    state.copyF(graph = GraphUpdate.onEvent(_, event))
  }

  def triggeredEvents(state: State, event: ApiEvent): Seq[Future[ApiEvent]] = event match {
    case e @ (_: NewPost | _: UpdatedPost | _: NewConnection | _: NewContainment) => Seq(Future.successful(e))
    case NewMembership(_, Membership(userId, groupId), _) =>
      def currentUserInvolved = state.auth.map(_.user.id == userId).getOrElse(false)
      def ownGroupInvolved = state.graph.groupsById.isDefinedAt(groupId)
      if (currentUserInvolved) {
        Nil
        // query all members of groupId
      } else if (ownGroupInvolved) {
        Nil
        // only forward new membership and user
      } else Nil
    case other => Seq(Future.successful(other)) //TODO:
  }

  def validate(state: State): State = state.copyF(auth = _.filterNot(JWT.isExpired))
}

class StateInterpreter(db: Db) {
  def stateEvents(state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] = {
    Seq(
      state.auth
        .map(_.toAuthentication |> LoggedIn)
        .getOrElse(LoggedOut)).map(Future.successful _) ++ Seq(
        db.graph.getAllVisiblePosts(state.user.map(_.id))
          .map(forClient(_).consistent)
          .map(ReplaceGraph(_)))
  }

  def stateChangeEvents(prevState: State, state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] =
    (prevState.auth == state.auth) match {
      case true => Seq.empty
      case false => stateEvents(state)
    }
}
