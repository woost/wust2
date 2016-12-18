package api

import java.nio.ByteBuffer

trait Api {
  def change(delta: Int): Int
}

sealed trait Channel
object Channel {
  case object Counter extends Channel
}

//TODO: which events belong to which channel in type system?

sealed trait ApiEvent
case class NewCounterValue(newValue: Int) extends ApiEvent
