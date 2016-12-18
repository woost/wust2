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
  implicit val pickler: Pickler[EV]
  val router: AutowireServer.Router

  val wire = AutowireServer

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private def sendBytes(msg: ByteBuffer) {
    println(msg)
  }

  private val messageHandler =
    Flow[Message]
      .mapAsync(parallelism = 4) {
        case bm: BinaryMessage if bm.isStrict =>
          val buffer = bm.getStrictData.asByteBuffer
          val wsMsg = Unpickle[ClientMessage].fromBytes(buffer)
          wsMsg match {
            case CallRequest(seqId, path, args) =>
              router(Request(path, args)).map { result =>
                val response = Response(seqId, result)
                val encoded = Pickle.intoBytes(response : ServerMessage)
                BinaryMessage(ByteString(encoded))
              }
            case NotifyRequest(event) =>
              println(event)
              Future { TextMessage("OK") } // TODO no response
          }
      }

  private val commonRoute = (path("ws") & get) { // TODO on root or configurable?
    handleWebSocketMessages(messageHandler)
  }

  def send(event: EV) {
    val bytes = Pickle.intoBytes(event)
    val notification = Pickle.intoBytes(Notification(bytes) : ServerMessage)
    sendBytes(notification)
  }

  val route: Route

  def run(interface: String, port: Int): Future[ServerBinding] = {
    Http().bindAndHandle(commonRoute ~ route, interface = interface, port = port)
  }
}
