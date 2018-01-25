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
  def chooseTaskPost(posts: List[PostId]): Result[List[PostId]]
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
  def dbUserOpt: Option[User.Persisted] = userOpt collect { case u: User.Persisted => u }
}
object Authentication {
  type Token = String

  sealed trait UserProvider extends Authentication {
    def user: User
    def userOpt = Some(user)
  }
  case object None extends Authentication {
    def userOpt = Option.empty[User]
  }
  case class Assumed(user: User.Assumed) extends UserProvider
  case class Verified(user: User.Persisted, expires: Long, token: Token) extends UserProvider {
    override def toString = s"Authentication.Verified($user)"
  }
}

sealed trait ApiError
object ApiError {
  case class ProtocolError(msg: String) extends ApiError
  sealed trait HandlerFailure extends ApiError
  case object InternalServerError extends HandlerFailure
  case object Unauthorized extends HandlerFailure
  case object Forbidden extends HandlerFailure
}

sealed trait ApiEvent
object ApiEvent {
  sealed trait Public extends ApiEvent
  sealed trait Private extends ApiEvent
  sealed trait GraphContent extends ApiEvent
  sealed trait AuthContent extends ApiEvent

  case class NewUser(user: User) extends GraphContent with Public with Private
  case class NewGroup(group: Group) extends GraphContent with Public with Private
  case class NewMembership(membership: Membership) extends GraphContent with Public with Private
  case class NewGraphChanges(changes: GraphChanges) extends GraphContent with Public {
    override def toString = s"NewGraphChanges(#changes: ${changes.size})"
  }
  case class LoggedIn(auth: Authentication.Verified) extends AuthContent with Private
  case object LoggedOut extends AuthContent with Private
  case class ReplaceGraph(graph: Graph) extends GraphContent with Private {
    override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
  }

  def separateByScope(events: Seq[ApiEvent]): (List[Private], List[Public]) =
    events.foldRight((List.empty[Private], List.empty[Public])) { case (ev, (privs, pubs)) =>
      val newPrivs = ev match {
        case (ev: Private) => ev :: privs
        case _ => privs
      }
      val newPubs = ev match {
        case ev: Public => ev :: pubs
        case _ => pubs
      }
      (newPrivs, newPubs)
    }

  def separateByContent(events: Seq[ApiEvent]): (List[GraphContent], List[AuthContent]) =
    events.foldRight((List.empty[GraphContent], List.empty[AuthContent])) {
      case (ev: GraphContent, (gs, as)) => (ev :: gs, as)
      case (ev: AuthContent, (gs, as)) => (gs, ev :: as)
    }
}
