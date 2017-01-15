package api

import java.nio.ByteBuffer
import scala.concurrent.Future

import graph._

trait Api {
  def getPost(id: AtomId): Future[Option[Post]]
  def deletePost(id: AtomId): Future[Boolean]
  def getGraph(): Future[Graph]
  def addPost(msg: String): Future[Post]
  def connect(from: AtomId, to: AtomId): Future[Option[Connects]]
  def deleteConnection(id: AtomId): Future[Boolean]
  def respond(to: AtomId, msg: String): Future[Option[(Post, Connects)]]
  // def getComponent(id: Id): Future[Graph]
}

sealed trait Channel
object Channel {
  case object Graph extends Channel

  def fromEvent(event: ApiEvent) = event match {
    case _: NewPost => Graph
    case _: DeletePost => Graph
    case _: DeleteConnection => Graph
    case _: NewConnection => Graph
  }
}

sealed trait ApiError
case object InternalServerError extends ApiError
case class NotFound(path: Seq[String]) extends ApiError
case object Unauthorized extends ApiError

sealed trait ApiEvent
case class NewPost(post: Post) extends ApiEvent
case class DeletePost(id: AtomId) extends ApiEvent
case class DeleteConnection(id: AtomId) extends ApiEvent
case class NewConnection(edge: Connects) extends ApiEvent //TODO or containment

sealed trait Authorize
case class PasswordAuth(name: String, password: String) extends Authorize
