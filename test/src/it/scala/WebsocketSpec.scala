import akka.actor._
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws._

import scala.concurrent.Future

import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

class WebsocketSpec(implicit ee: ExecutionEnv) extends Specification {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  def connection(flow: Flow[Message, Message, Future[Done]]) = Http().singleWebSocketRequest(WebSocketRequest("ws://localhost/ws"), flow)

  def ws(sink: Sink[Message, Future[Done]], source: Source[Message, NotUsed]): (Future[WebSocketUpgradeResponse], Future[Done]) = {
    connection(Flow.fromSinkAndSourceMat(sink, source)(Keep.left))
  }

  "should upgrade websocket request on /ws" >> {
    val (upgradeResponse, _) = ws(Sink.ignore, Source.empty)

    upgradeResponse.map { upgrade =>
      upgrade.response.status must beEqualTo(StatusCodes.SwitchingProtocols)
    } await
  }
}
