package api

import java.nio.ByteBuffer

import graph._

trait Api {
  def getPost(id: AtomId): Post
  def getGraph(): Graph
  def addPost(msg: String): Post
  def connect(from: AtomId, to: AtomId): RespondsTo
  // def getComponent(id: Id): Graph
}

sealed trait Channel
object Channel {
  case object Graph extends Channel

  def fromEvent(event: ApiEvent) = event match {
    case _: NewPost => Graph
    case _: NewRespondsTo => Graph
  }
}

sealed trait ApiEvent
case class NewPost(post: Post) extends ApiEvent
case class NewRespondsTo(edge: RespondsTo) extends ApiEvent

sealed trait Authorize
case class PasswordAuth(name: String, password: String) extends Authorize
