package wust.framework

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.Done
import akka.http.scaladsl.Http
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.util.ByteString

import scala.concurrent.{ Promise, Future }

object AkkaHelper {
  implicit class PeekableSource[T, M](val src: Source[T, M]) extends AnyVal {
    def peekMaterializedValue: (Source[T, M], Future[M]) = {
      val p = Promise[M]
      val s = src.mapMaterializedValue { m =>
        p.trySuccess(m)
        m
      }
      (s, p.future)
    }
  }
}
import AkkaHelper._

class AkkaWebsocketConnection(implicit system: ActorSystem) extends WebsocketConnection {
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  private val (outgoing, queue) = {
    val bufferSize = 250
    val overflowStrategy = OverflowStrategy.fail
    val src = Source.queue[Message](bufferSize, overflowStrategy)
    src.peekMaterializedValue
  }


  def send(bytes: ByteBuffer): Unit = queue.foreach { queue =>
    val message = BinaryMessage(ByteString(bytes))
    queue offer message
  }

  def run(location: String, listener: WebsocketListener) = {
    // Future[Done] is the materialized value of Sink.foreach,
    // emitted when the stream completes
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: BinaryMessage.Strict => listener.onMessage(message.getStrictData.asByteBuffer)
        //TODO: streamed
      }

    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(location))

    // the materialized value is a tuple with
    // upgradeResponse is a Future[WebSocketUpgradeResponse] that
    // completes or fails when the connection succeeds or fails
    // and closed is a Future[Done] with the stream completion from the incoming sink
    val (upgradeResponse, closed) =
      outgoing
        .viaMat(webSocketFlow)(Keep.right)
        .toMat(incoming)(Keep.both)
        .run()

    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) Done
      else {
        //TODO: error handling
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    connected.onComplete(_ => listener.onConnect())
    closed.foreach(_ => listener.onClose())
  }
}
