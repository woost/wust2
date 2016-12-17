package framework

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import autowire.Core.Request
import boopickle.Default._
import java.nio.ByteBuffer

import framework.message._

object AutowireServer extends autowire.Server[ByteBuffer, Pickler, Pickler] {
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

trait WebsocketServer[EV] {
  //TODO: broadcast
  implicit val pickler: Pickler[EV]
  def router: AutowireServer.Router

  def sendBytes(msg: ByteBuffer) {
    println(msg)
  }

  def send(event: EV) {
    val bytes = Pickle.intoBytes(event)
    val notification = Pickle.intoBytes(Notification(bytes) : ServerMessage)
    sendBytes(notification)
  }

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val messageHandler =
    Flow[Message]
      .mapAsync(4) { // TODO why 4?
        case bm: BinaryMessage if bm.isStrict => //TODO: extract serializer out of here
          val buffer = bm.getStrictData.asByteBuffer
          val call = Unpickle[CallRequest].fromBytes(buffer)
          router(Request(call.path, call.args)).map { result =>
            val response = Response(call.seqId, result)
            val encoded = Pickle.intoBytes(response : ServerMessage)
            BinaryMessage(ByteString(encoded))
          }
      }

  val route: Route
  val commonRoute = (path("ws") & get) {
    handleWebSocketMessages(messageHandler)
  }

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(commonRoute ~ route, interface = interface, port = port)
  }
}
