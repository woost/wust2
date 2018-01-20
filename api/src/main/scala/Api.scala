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
  def assumeLogin(id: UserId): Result[Boolean]
  def register(name: String, password: String): Result[Boolean]
  def login(name: String, password: String): Result[Boolean]
  def loginToken(token: Authentication.Token): Result[Boolean]
  def logout(): Result[Boolean]
}

sealed trait Authentication {
  def userOpt: Option[User]
}
object Authentication {
  type Token = String

  sealed trait UserProvider extends Authentication {
    def user: User
    final def userOpt = Some(user)
  }
  case object None extends Authentication {
    def userOpt = Option.empty
  }
  case class Assumed(user: User.Assumed) extends UserProvider
  case class Verified(user: User.Persisted, token: Token) extends UserProvider {
    override def toString = s"Authentication.Verified(${user.id})"
  }
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

  sealed trait GraphContent extends ApiEvent
  sealed trait AuthContent extends ApiEvent

  //TODO: move into object ApiEvent
  final case class NewUser(user: User) extends Public with Private with GraphContent
  final case class NewGroup(group: Group) extends Public with Private with GraphContent
  final case class NewMembership(membership: Membership) extends Public with Private with GraphContent
  final case class NewGraphChanges(changes: GraphChanges) extends Public with GraphContent {
    override def toString = s"NewGraphChanges(#changes: ${changes.size})"
  }
  final case class LoggedIn(auth: Authentication.Verified) extends Private with AuthContent
  final case object LoggedOut extends Private with AuthContent
  final case class ReplaceGraph(graph: Graph) extends Private with GraphContent {
    override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
  }

  def separate(events: Seq[ApiEvent]): (List[Private], List[Public]) =
    events.foldRight((List.empty[Private], List.empty[Public])) {
      case (ev: Private, (privs, pubs)) => (ev :: privs, pubs)
      case (ev: Public, (privs, pubs)) => (privs, ev :: pubs)
    }
}
