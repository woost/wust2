package wust.framework

import java.nio.ByteBuffer

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, KillSwitches, OverflowStrategy}
import akka.util.ByteString

import scala.concurrent.{Future, Promise}

object AkkaHelper {
  implicit class PeekableSource[T, M](val src: Source[T, M]) extends AnyVal {
    def peekMaterializedValue: (Source[T, M], Future[M]) = {
      val p = Promise[M]
      val s = src.mapMaterializedValue { m => p.trySuccess(m); m }
      (s, p.future)
    }
  }
}
import wust.framework.AkkaHelper._

class AkkaWebsocketConnection(implicit system: ActorSystem) extends WebsocketConnection {
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  private val (outgoing, queueWithKill) = {
    val bufferSize = 250
    val overflowStrategy = OverflowStrategy.fail
    Source
      .queue[Message](bufferSize, overflowStrategy)
      .viaMat(KillSwitches.single)(Keep.both)
      .peekMaterializedValue
  }

  def send(bytes: ByteBuffer): Unit = queueWithKill.foreach { case (queue, _) =>
    val message = BinaryMessage(ByteString(bytes))
    queue offer message
  }

  def run(location: String, listener: WebsocketListener) = {
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case message: BinaryMessage.Strict => listener.onMessage(message.getStrictData.asByteBuffer)
        //TODO: streamed
      }

    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(location))

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

    connected.foreach(_ => listener.onConnect())
    closed.foreach(_ => listener.onClose())
  }
}
