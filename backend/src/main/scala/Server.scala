package example

import scala.concurrent.ExecutionContext.Implicits.global

import api._
import boopickle.Default._

object ApiImpl extends Api with WebsocketServer with App {
  val router = AutowireWebsocketServer.route[Api](this)

  private var counter = 0

  def change(delta: Int) = {
    counter += delta
    // server ! Broadcast(SetCounter(counter))
    counter
  }
}
