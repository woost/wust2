package wust.api

import wust.graph._
import wust.ids._

import scala.concurrent.Future

trait Api {
  def getPost(id: PostId): Future[Option[Post]]
  def deletePost(id: PostId): Future[Boolean]
  def getGraph(selection: GraphSelection): Future[Graph]
  def addPost(msg: String, selection: GraphSelection, groupId: Option[GroupId]): Future[Post]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: Option[GroupId]): Future[(Post, Connection)]
  def updatePost(post: Post): Future[Boolean]
  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connection]
  def createContainment(parentId: PostId, childId: PostId): Future[Containment]
  def deleteConnection(id: ConnectionId): Future[Boolean]
  def deleteContainment(id: ContainmentId): Future[Boolean]
  def getUser(userId: UserId): Future[Option[User]]
  def addGroup(): Future[Group]
  def addMember(groupId: GroupId, userId: UserId): Future[Boolean]
  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean]
  def createGroupInvite(groupId: GroupId): Future[Option[String]]
  def acceptGroupInvite(token: String): Future[Option[GroupId]]
}

case class ApiException(error: ApiError) extends Exception {
  override def toString = error.toString
}

sealed trait ApiError
case object InternalServerError extends ApiError
case class NotFound(path: Seq[String]) extends ApiError
case object Unauthorized extends ApiError

sealed trait ApiEvent
case class NewPost(post: Post) extends ApiEvent
case class UpdatedPost(post: Post) extends ApiEvent
case class NewConnection(edge: Connection) extends ApiEvent
case class NewContainment(edge: Containment) extends ApiEvent
case class NewOwnership(edge: Ownership) extends ApiEvent
case class NewUser(edge: User) extends ApiEvent
case class NewGroup(edge: Group) extends ApiEvent
case class NewMembership(edge: Membership) extends ApiEvent
case class DeletePost(id: PostId) extends ApiEvent
case class DeleteConnection(id: ConnectionId) extends ApiEvent
case class DeleteContainment(id: ContainmentId) extends ApiEvent
case class LoggedIn(auth: Authentication) extends ApiEvent
case object LoggedOut extends ApiEvent
case class ReplaceGraph(graph: Graph) extends ApiEvent {
  override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
}

trait AuthApi {
  def register(name: String, password: String): Future[Boolean]
  def login(name: String, password: String): Future[Boolean]
  def loginToken(token: Authentication.Token): Future[Boolean]
  def logout(): Future[Boolean]
}

case class Authentication(user: User, token: Authentication.Token)
object Authentication {
  type Token = String
}
