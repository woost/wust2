package wust.framework

import java.nio.ByteBuffer
import java.util.{Timer, TimerTask}

trait WebsocketListener {
  def onConnect(): Unit
  def onMessage(bytes: ByteBuffer): Unit
  def onClose(): Unit
}

trait WebsocketConnection {
  def send(bytes: ByteBuffer): Unit
  def run(location: String, listener: WebsocketListener): Unit
}

class ReconnectingWebsocketConnection(connection: WebsocketConnection) extends WebsocketConnection {
  private var connectionAttempts = 0
  private def backoffInterval: Long = {
    val maxInterval = math.pow(2, connectionAttempts) * 1000.0
    val truncated = maxInterval.min(60 * 1000).toInt
    (scala.util.Random.nextDouble * truncated).toLong
  }

  def send(bytes: ByteBuffer) = connection.send(bytes)

  def run(location: String, listener: WebsocketListener) = {
    val awareListener = new WebsocketListener { wsThis =>
      def onConnect(): Unit = {
        connectionAttempts = 0
        listener.onConnect()
        println(s"websocket is open: $location")
      }
      def onMessage(bytes: ByteBuffer): Unit = listener.onMessage(bytes)
      def onClose(): Unit = {
        connectionAttempts += 1
        listener.onClose()
        println(s"websocket is closed, will attempt to reconnect in ${(backoffInterval / 1000.0).ceil} seconds")
        val timer = new Timer
        val task = new TimerTask { def run() = connection.run(location, wsThis) }
        timer.schedule(task, backoffInterval)
      }
    }

    connection.run(location, awareListener)
  }
}
