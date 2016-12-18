package backend

import scala.concurrent.ExecutionContext.Implicits.global

import api._
import boopickle.Default._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._

import framework._

class ApiImpl(send: ApiEvent => Unit) extends Api {
  private var counter = 0

  def change(delta: Int) = {
    counter += delta
    send(NewCounterValue(counter))
    counter
  }
}

object Server extends WebsocketServer[ApiEvent] with App {
  val pickler = implicitly[Pickler[ApiEvent]]
  val router = (wire.route[Api](_))(new ApiImpl(send))

  private val indexFile = {
    val is = getClass.getResourceAsStream("/index-dev.html")
    Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

  val route = (pathSingleSlash & get) {
    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, indexFile))
  }

  run("localhost", 8080) foreach { binding =>
    println(s"Server online at ${binding.localAddress}")
  }

  println("Press RETURN to stop...")
  io.StdIn.readLine()
}
