package wust.api

import wust.graph._
import wust.ids._

trait Api[Result[_]] {
  def changeGraph(changes: List[GraphChanges]): Result[Boolean]

  def getPost(id: PostId): Result[Option[Post]]
  def getGraph(selection: Page): Result[Graph]
  def getUser(userId: UserId): Result[Option[User]]
  def addGroup(): Result[GroupId]
  def addMember(groupId: GroupId, userId: UserId): Result[Boolean]
  def addMemberByName(groupId: GroupId, userName: String): Result[Boolean]
  def getGroupInviteToken(groupId: GroupId): Result[Option[String]]
  def recreateGroupInviteToken(groupId: GroupId): Result[Option[String]]
  def acceptGroupInvite(token: String): Result[Option[GroupId]]

  def importGithubUrl(url: String): Result[Boolean]
  def importGitterUrl(url: String): Result[Boolean]
}

trait AuthApi[Result[_]] {
  //TODO: simplify implicit login by handshake with a token or userid and an initial graph. persist new implicit user when used first time.
  def register(name: String, password: String): Result[Boolean]
  def login(name: String, password: String): Result[Boolean]
  def loginToken(token: Authentication.Token): Result[Boolean]
  def logout(): Result[Boolean]
}

case class Authentication(user: User, token: Authentication.Token)
object Authentication {
  type Token = String
}

sealed trait ApiError
object ApiError {
  sealed trait GenericFailure extends ApiError
  sealed trait HandlerFailure extends ApiError

  case class NotFound(path: Seq[String]) extends GenericFailure
  case class ProtocolError(msg: String) extends GenericFailure

  case object InternalServerError extends HandlerFailure
  case object Unauthorized extends HandlerFailure
}

sealed trait ApiEvent
object ApiEvent {
  sealed trait Public extends ApiEvent
  sealed trait Private extends ApiEvent

  //TODO: move into object ApiEvent
  final case class NewUser(user: User) extends Public with Private
  final case class NewGroup(group: Group) extends Public with Private
  final case class NewMembership(membership: Membership) extends Public with Private
  final case class NewGraphChanges(changes: GraphChanges) extends Public {
    override def toString = s"NewGraphChanges(#changes: ${changes.size})"
  }
  final case class LoggedIn(auth: Authentication) extends Private
  final case object LoggedOut extends Private
  final case class ReplaceGraph(graph: Graph) extends Private {
    override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
  }

  def separate(events: Seq[ApiEvent]): (List[Private], List[Public]) =
    events.foldRight((List.empty[Private], List.empty[Public])) {
      case (ev: Private, (privs, pubs)) => (ev :: privs, pubs)
      case (ev: Public, (privs, pubs)) => (privs, ev :: pubs)
    }
}
