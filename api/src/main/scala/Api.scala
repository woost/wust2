package api

import java.nio.ByteBuffer

import graph._

trait Api {
  def getPost(id: AtomId): Post
  def deletePost(id: AtomId): Unit
  def getGraph(): Graph
  def addPost(msg: String): Post
  def connect(from: AtomId, to: AtomId): Connects
  def respond(to: AtomId, msg: String): (Post, Connects)
  // def getComponent(id: Id): Graph
}

sealed trait Channel
object Channel {
  case object Graph extends Channel

  def fromEvent(event: ApiEvent) = event match {
    case _: NewPost => Graph
    case _: DeletePost => Graph
    case _: NewConnects => Graph
  }
}

sealed trait ApiEvent
case class NewPost(post: Post) extends ApiEvent
case class DeletePost(id: AtomId) extends ApiEvent
case class NewConnects(edge: Connects) extends ApiEvent //TODO or containment

sealed trait Authorize
case class PasswordAuth(name: String, password: String) extends Authorize
