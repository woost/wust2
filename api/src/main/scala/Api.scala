package api

import java.nio.ByteBuffer

trait Api {
  def change(delta: Int): Int
}

sealed trait ApiEvent
case class NewCounterValue(newValue: Int) extends ApiEvent
