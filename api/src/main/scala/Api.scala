package wust.api

import wust.graph._

import scala.concurrent.Future

trait Api {
  def getPost(id: PostId): Future[Option[Post]]
  def deletePost(id: PostId): Future[Boolean]
  def getGraph(selection: GraphSelection): Future[Graph]
  def addPost(msg: String, selection: GraphSelection, groupId: Long): Future[Post]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: Long): Future[(Post, Connects)]
  def updatePost(post: Post): Future[Boolean]
  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects]
  def contain(parentId: PostId, childId: PostId): Future[Contains]
  def deleteConnection(id: ConnectsId): Future[Boolean]
  def deleteContainment(id: ContainsId): Future[Boolean]
  def getUser(userId: Long): Future[Option[User]]
  def getUserGroups(userId: Long): Future[Seq[UserGroup]]
  def addUserGroup(): Future[UserGroup]
  def addMember(groupId: Long, userId: Long): Future[Boolean]
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
case class DeletePost(id: PostId) extends ApiEvent with DynamicEvent
case class DeleteConnection(id: ConnectsId) extends ApiEvent with DynamicEvent
case class DeleteContainment(id: ContainsId) extends ApiEvent with DynamicEvent
case class ImplicitLogin(auth: Authentication) extends ApiEvent
case class ReplaceGraph(graph: Graph) extends ApiEvent {
  override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
}
@deprecated("use usergroups stored in graph and updated incrementally", "")
case class ReplaceUserGroups(groups: Seq[UserGroup]) extends ApiEvent

trait AuthApi {
  def register(name: String, password: String): Future[Option[Authentication]]
  def login(name: String, password: String): Future[Option[Authentication]]
  def loginToken(token: Authentication.Token): Future[Option[Authentication]]
  def logout(): Future[Boolean]
}

case class User(id: Long, name: String, isImplicit: Boolean, revision: Int) {
  def toClientUser = ClientUser(id, name)
}
object User {
  private def implicitUserName = "anon-" + java.util.UUID.randomUUID.toString
  val initialRevision = 0
  def apply(name: String): User = User(0L, name, isImplicit = false, initialRevision)
  def apply(): User = User(0L, implicitUserName, isImplicit = true, initialRevision)
}
case class ClientUser(id: Long, name: String) //TODO: derive: identify only by id //TODO: rename to User (the db-user should be DbUser or db.User)
case class UserGroup(id: Long, users: Seq[ClientUser])

case class Authentication(user: User, token: Authentication.Token) {
  override def toString = s"Authentication($user, ***)"
}
object Authentication {
  type Token = String
}
