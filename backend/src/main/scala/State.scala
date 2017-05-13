package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.ids._
import wust.util.Pipe

import scala.concurrent.{ExecutionContext, Future}

// TODO: crashes coverage @derive(copyF)
case class State(auth: Option[JWTAuthentication], groupIds: Set[GroupId]) {
  val user = auth.map(_.user)
  def copyF(auth: Option[JWTAuthentication] => Option[JWTAuthentication] = identity, groupIds: Set[GroupId] => Set[GroupId] = identity) = copy(auth = auth(this.auth), groupIds = groupIds(this.groupIds))
}
object State {
  def initial = State(auth = None, groupIds = Set.empty)
}

object StateTranslator {
  def applyEvent(state: State, event: ApiEvent): State = event match {
    case NewMembership(edge) if state.auth.isDefined && edge.userId == state.auth.get.user.id =>
      state.copyF(groupIds = _ ++ Set(edge.groupId))
    case _ => state
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
      state.auth.map(_.user.id == edge.userId).getOrElse(false) || state.groupIds.contains(edge.groupId)
    case DeletePost(_) => true
    case DeleteConnection(_) => true
    case DeleteContainment(_) => true
    case _ => true//false
  }
}

class StateChange(db: Db, jwt: JWT, enableImplicit: Boolean) {
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

  def createImplicitAuth()(implicit ec: ExecutionContext) = enableImplicit match {
    case true => db.user.createImplicitUser().map(u => jwt.generateAuthentication(forClient(u))).map(Option.apply)
    case false => Future.successful(None)
  }

  def filterValid(state: State): State = state.copyF(auth = _.filterNot(jwt.isExpired))
}
