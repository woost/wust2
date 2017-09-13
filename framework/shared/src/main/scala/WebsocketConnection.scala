package wust.framework

import java.nio.ByteBuffer

import scala.collection.mutable
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
  private var connectionAttempts = 1
  private def backoffInterval: Long = {
    val maxInterval = (math.pow(2, connectionAttempts) - 1) * 1000.0
    val truncated = maxInterval.min(60 * 1000).toInt
    (scala.util.Random.nextDouble * truncated).toLong
  }

  private val messages = mutable.Queue.empty[ByteBuffer]
  private def flush(): Unit = {
    var sending = true
    //TODO: on flush, remove ping/pong from messages
    while (sending && messages.nonEmpty) {
      try {
        val bytes = messages.front
        connection.send(bytes)
        messages.dequeue()
      } catch { case _: Exception => sending = false }
    }
  }

  def send(bytes: ByteBuffer) {
    messages.enqueue(bytes)
    flush()
  }

  def run(location: String, listener: WebsocketListener) = {
    val awareListener = new WebsocketListener { wsThis =>
      def onConnect(): Unit = {
        println(s"websocket is open: $location")
        connectionAttempts = 1
        listener.onConnect()
        flush()
      }
      def onMessage(bytes: ByteBuffer): Unit = listener.onMessage(bytes)
      def onClose(): Unit = {
        println(s"websocket is closed, will attempt to reconnect in ${(backoffInterval / 1000.0).ceil} seconds")
        connectionAttempts += 1
        listener.onClose()
        val timer = new Timer
        val task = new TimerTask { def run() = connection.run(location, wsThis) }
        timer.schedule(task, backoffInterval)
      }
    }

    connection.run(location, awareListener)
  }
}
