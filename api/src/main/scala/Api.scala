package wust.api

import wust.graph._
import wust.ids._

import scala.concurrent.Future

trait Api {
  def changeGraph(changes: List[GraphChanges]): Future[Boolean]

  def getPost(id: PostId): Future[Option[Post]]
  def getGraph(selection: Page): Future[Graph]
  def getUser(userId: UserId): Future[Option[User]]
  def addGroup(): Future[GroupId]
  def addMember(groupId: GroupId, userId: UserId): Future[Boolean]
  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean]
  def getGroupInviteToken(groupId: GroupId): Future[Option[String]]
  def recreateGroupInviteToken(groupId: GroupId): Future[Option[String]]
  def acceptGroupInvite(token: String): Future[Option[GroupId]]

  def importGithubUrl(url: String): Future[Boolean]
  def importGitterUrl(url: String): Future[Boolean]
  def getRestructuringTask(): Future[RestructuringTask]
}

case class ApiException(error: ApiError) extends Exception
sealed trait ApiError
object ApiError {
  case object InternalServerError extends ApiError
  case class NotFound(path: Seq[String]) extends ApiError
  case class ProtocolError(msg: String) extends ApiError
  case object Unauthorized extends ApiError
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
}


trait AuthApi {
  //TODO: simplify implicit login by handshake with a token or userid and an initial graph. persist new implicit user when used first time.
  def register(name: String, password: String): Future[Boolean]
  def login(name: String, password: String): Future[Boolean]
  def loginToken(token: Authentication.Token): Future[Boolean]
  def logout(): Future[Boolean]
}

case class Authentication(user: User, token: Authentication.Token)
object Authentication {
  type Token = String
}
