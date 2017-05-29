package wust.api

import wust.graph._
import wust.ids._

import scala.concurrent.Future

trait Api {
  def getPost(id: PostId): Future[Option[Post]]
  def deletePost(id: PostId, selection: GraphSelection): Future[Boolean]
  def getGraph(selection: GraphSelection): Future[Graph]
  def addPost(msg: String, selection: GraphSelection, groupId: Option[GroupId]): Future[Option[PostId]]
  def addPostInContainment(msg: String, parentId: PostId, groupId: Option[GroupId]): Future[Option[PostId]]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: Option[GroupId]): Future[Option[(Post, Connection)]]
  def updatePost(post: Post): Future[Boolean]
  def connect(sourceId: PostId, targetId: PostId): Future[Option[Connection]]
  def createContainment(parentId: PostId, childId: PostId): Future[Option[Containment]]
  def createSelection(postId: PostId, selection: GraphSelection): Future[Boolean]
  def deleteConnection(id: Connection): Future[Boolean]
  def deleteContainment(id: Containment): Future[Boolean]
  def getUser(userId: UserId): Future[Option[User]]
  def addGroup(): Future[GroupId]
  def addMember(groupId: GroupId, userId: UserId): Future[Boolean]
  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean]
  def getGroupInviteToken(groupId: GroupId): Future[Option[String]]
  def recreateGroupInviteToken(groupId: GroupId): Future[Option[String]]
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
case class NewConnection(connection: Connection) extends ApiEvent
case class NewContainment(containment: Containment) extends ApiEvent
case class NewOwnership(ownership: Ownership) extends ApiEvent
case class NewUser(user: User) extends ApiEvent
case class NewGroup(group: Group) extends ApiEvent
case class NewMembership(membership: Membership) extends ApiEvent
case class DeletePost(id: PostId) extends ApiEvent
case class DeleteConnection(connection: Connection) extends ApiEvent
case class DeleteContainment(containment: Containment) extends ApiEvent
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
