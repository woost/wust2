package framework

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import scala.io.StdIn

import autowire.Core.Request
import boopickle.Default._
import java.nio.ByteBuffer

import framework.message._

object AutowireWebsocketServer extends autowire.Server[ByteBuffer, Pickler, Pickler] {
  def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
}

trait WebsocketServer {
  //TODO: broadcast
  def router: AutowireWebsocketServer.Router

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

  //TODO: serve index html
  val requestHandler: HttpRequest => HttpResponse = {
    //TODO: simpler upgrade router implementation
    //http://doc.akka.io/docs/akka-http/current/scala/http/websocket-support.html
    case req @ HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(messageHandler)
        case None => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  val bindingFuture = Http().bindAndHandleSync(requestHandler, interface = "localhost", port = 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
}
