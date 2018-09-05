package wust.sdk

import sloth._
import covenant.core.DefaultLogHandler
import mycelium.client._
import mycelium.core._
import mycelium.core.message._
import chameleon._
import cats.data.EitherT
import wust.api._, wust.api.serialize.Boopickle._
import covenant.ws._
import chameleon.ext.boopickle._
import boopickle.Default._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import java.nio.ByteBuffer
import scala.concurrent.duration._
import scala.concurrent.{Future, ExecutionContext}
import monix.eval.Task

private[sdk] trait NativeWustClient {
  def withTask(location: String)(implicit system: ActorSystem, materializer: ActorMaterializer): WustClientFactory[Task] = {
    import system.dispatcher
    new WustClientFactory(createWsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config))
  }

  def apply(location: String)(implicit system: ActorSystem, materializer: ActorMaterializer): WustClientFactory[Future] = {
    import system.dispatcher
    new WustClientFactory(WsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config))
  }

  //TODO: configure?
  private val defaultBufferSize = 100
  private val defaultOverflowStrategy = OverflowStrategy.fail

  private def createWsClient[PickleType, Event, ErrorType](
    uri: String,
    config: WebsocketClientConfig,
    logger: LogHandler[Task]
  )(implicit
    system: ActorSystem,
    materializer: ActorMaterializer,
    builder: AkkaMessageBuilder[PickleType],
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, Event, ErrorType], PickleType]
  ): WsClient[PickleType, Task, Event, ErrorType, ClientException] = {
    val connection = new AkkaWebsocketConnection(defaultBufferSize, defaultOverflowStrategy)
    fromConnection(uri, connection, config, logger)
  }
  private def createWsClient[PickleType, Event, ErrorType](
    uri: String,
    config: WebsocketClientConfig
  )(implicit
    system: ActorSystem,
    materializer: ActorMaterializer,
    builder: AkkaMessageBuilder[PickleType],
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, Event, ErrorType], PickleType]
  ): WsClient[PickleType, Task, Event, ErrorType, ClientException] = {
    createWsClient[PickleType, Event, ErrorType](uri, config, new LogHandler[Task])
  }

  private def fromConnection[PickleType, Event, ErrorType](
    uri: String,
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig,
    logger: LogHandler[Task]
  )(implicit
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, Event, ErrorType], PickleType]
  ) = new WsClient[PickleType, Task, Event, ErrorType, ClientException](uri, connection, config) {

    def sendWith(sendType: SendType, requestTimeout: FiniteDuration) = {
      val transport = new RequestTransport[PickleType, Task] {
        def apply(request: Request[PickleType]): Task[PickleType] = {
          Task.deferFutureAction(implicit scheduler => mycelium.send(request.path, request.payload, sendType, requestTimeout)).flatMap {
            case Right(res) => Task.pure(res)
            case Left(err) => Task.raiseError(new Exception(s"Websocket request failed: $err"))
          }
        }
      }

      Client[PickleType, Task, ClientException](transport, logger)
    }
  }
}
