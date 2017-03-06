package api

import java.nio.ByteBuffer
import scala.concurrent.Future

import graph._

trait AuthApi {
  def register(name: String, password: String): Future[Boolean]
}

trait Api {
  def getPost(id: PostId): Future[Option[Post]]
  def deletePost(id: PostId): Future[Boolean]
  def getGraph(): Future[Graph]
  def addPost(msg: String): Future[Post]
  def updatePost(post: Post): Future[Boolean]
  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects]
  def contain(childId: PostId, parentId: PostId): Future[Contains]
  def deleteConnection(id: ConnectsId): Future[Boolean]
  def deleteContainment(id: ContainsId): Future[Boolean]
  def respond(to: PostId, msg: String): Future[(Post, Connects)]
  // def getComponent(id: Id): Future[Graph]
}

sealed trait Channel
object Channel {
  case object Graph extends Channel

  def fromEvent(event: ApiEvent) = event match {
    case _: NewPost => Graph
    case _: UpdatedPost => Graph
    case _: NewConnection => Graph
    case _: NewContainment => Graph
    case _: DeletePost => Graph
    case _: DeleteConnection => Graph
    case _: DeleteContainment => Graph
  }
}

sealed trait ApiError
case object InternalServerError extends ApiError
case class NotFound(path: Seq[String]) extends ApiError
case object Unauthorized extends ApiError

sealed trait ApiEvent
case class NewPost(post: Post) extends ApiEvent
case class UpdatedPost(post: Post) extends ApiEvent
case class NewConnection(edge: Connects) extends ApiEvent
case class NewContainment(edge: Contains) extends ApiEvent
case class DeletePost(id: PostId) extends ApiEvent
case class DeleteConnection(id: ConnectsId) extends ApiEvent
case class DeleteContainment(id: ContainsId) extends ApiEvent

sealed trait Authorize
case class PasswordAuth(name: String, password: String) extends Authorize
