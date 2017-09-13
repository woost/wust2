package wust.framework

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.Done
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.util.ByteString

import scala.concurrent.{Promise, Future}

class AkkaWebsocketConnection(implicit system: ActorSystem) extends WebsocketConnection {
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  private val bufferSize = 100
  private val overflowStrategy = akka.stream.OverflowStrategy.dropHead
  private val (outgoing, queue) = peekMaterializedValue(Source.queue[Message](bufferSize, overflowStrategy))

  private def peekMaterializedValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
    val p = Promise[M]
    val s = src.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }
    (s, p.future)
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

    val connected = upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        //TODO: error handling
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    connected.onComplete(_ => listener.onConnect())
    closed.foreach(_ => listener.onClose())
  }
}
