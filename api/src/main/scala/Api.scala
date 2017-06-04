package wust.api

import wust.graph._
import wust.ids._

import scala.concurrent.Future

trait Api {
  def changeGraph(changes: GraphChanges): Future[Boolean]

  def getPost(id: PostId): Future[Option[Post]]
  def getGraph(selection: GraphSelection): Future[Graph]
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
case class NewUser(user: User) extends ApiEvent
case class NewGroup(group: Group) extends ApiEvent
case class NewMembership(membership: Membership) extends ApiEvent
case class NewGraphChanges(changes: GraphChanges) extends ApiEvent
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
