package api

import java.nio.ByteBuffer

trait Api {
  def change(delta: Int): Int
}

sealed trait Channel
object Channel {
  case object Counter extends Channel

  def fromEvent(event: ApiEvent) = event match {
    case NewCounterValue(_) => Counter
  }
}

sealed trait ApiEvent
case class NewCounterValue(newValue: Int) extends ApiEvent
