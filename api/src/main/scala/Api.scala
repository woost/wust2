package api

import java.nio.ByteBuffer

import graph._
import pharg._

trait Api {
  def change(delta: Int): Int

  def getPost(id: Id): Post
  def getGraph(): Graph
  def addPost(msg: String): (Id, Post)
  def connect(from: Id, to: Id): (Edge[Id], Connects)
  def getComponent(id: Id): Graph
}

sealed trait Channel
object Channel {
  case object Counter extends Channel
  case object Graph extends Channel

  def fromEvent(event: ApiEvent) = event match {
    case _:NewCounterValue => Counter
    case _:NewPost => Graph
    case _:NewConnects => Graph
  }
}

sealed trait ApiEvent
case class NewCounterValue(fromUser: String, newValue: Int) extends ApiEvent
case class NewPost(id: Id, post: Post) extends ApiEvent
case class NewConnects(edge: Edge[Id], connects: Connects) extends ApiEvent

sealed trait Authorize
case class PasswordAuth(name: String, password: String) extends Authorize
