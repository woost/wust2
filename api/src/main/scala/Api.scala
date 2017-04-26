package wust.api

import wust.ids._
import wust.graph._

import scala.concurrent.Future

trait Api {
  def getPost(id: PostId): Future[Option[Post]]
  def deletePost(id: PostId): Future[Boolean]
  def getGraph(selection: GraphSelection): Future[Graph]
  def addPost(msg: String, selection: GraphSelection, groupId: GroupId): Future[Post]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: GroupId): Future[(Post, Connects)]
  def updatePost(post: Post): Future[Boolean]
  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects]
  def contain(parentId: PostId, childId: PostId): Future[Contains]
  def deleteConnection(id: ConnectsId): Future[Boolean]
  def deleteContainment(id: ContainsId): Future[Boolean]
  def getUser(userId: UserId): Future[Option[User]]
  def addUserGroup(): Future[Group]
  def addMember(groupId: GroupId, userId: UserId): Future[Boolean]
  // def getComponent(id: Id): Future[Graph]
}

sealed trait ApiError
case object InternalServerError extends ApiError
case class NotFound(path: Seq[String]) extends ApiError
case object Unauthorized extends ApiError

sealed trait ApiEvent
//TODO: why does this not compile
//sealed trait DynamicApiEvent extends ApiEvent
//[error] knownDirectSubclasses of DynamicApiEvent observed before subclass DeleteConnection registered
sealed trait DynamicEvent
case class NewPost(post: Post) extends ApiEvent with DynamicEvent
case class UpdatedPost(post: Post) extends ApiEvent with DynamicEvent
case class NewConnection(edge: Connects) extends ApiEvent with DynamicEvent
case class NewContainment(edge: Contains) extends ApiEvent with DynamicEvent
case class NewOwnership(edge: Ownership) extends ApiEvent with DynamicEvent
case class NewUser(edge: User) extends ApiEvent with DynamicEvent
case class NewGroup(edge: Group) extends ApiEvent with DynamicEvent
case class NewMembership(edge: Membership) extends ApiEvent with DynamicEvent
case class DeletePost(id: PostId) extends ApiEvent with DynamicEvent
case class DeleteConnection(id: ConnectsId) extends ApiEvent with DynamicEvent
case class DeleteContainment(id: ContainsId) extends ApiEvent with DynamicEvent
case class ImplicitLogin(auth: Authentication) extends ApiEvent
case class ReplaceGraph(graph: Graph) extends ApiEvent {
  override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
}

trait AuthApi {
  def register(name: String, password: String): Future[Option[Authentication]]
  def login(name: String, password: String): Future[Option[Authentication]]
  def loginToken(token: Authentication.Token): Future[Option[Authentication]]
  def logout(): Future[Boolean]
}

case class Authentication(user: User, token: Authentication.Token)
object Authentication {
  type Token = String
}
