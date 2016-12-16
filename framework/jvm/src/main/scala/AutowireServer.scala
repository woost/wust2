package framework

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.server.Directives._
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

  private val indexFile = {
    val is = getClass.getResourceAsStream("/index-dev.html")
    Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

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
  val route = (pathSingleSlash & get) {
    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, indexFile))
  } ~ (path("ws") & get) {
    handleWebSocketMessages(messageHandler)
  }

  val bindingFuture = Http().bindAndHandle(route, interface = "localhost", port = 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
}
