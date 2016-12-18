package backend

import scala.concurrent.ExecutionContext.Implicits.global

import api._
import boopickle.Default._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._

import framework._

class ApiImpl(emit: ApiEvent => Unit) extends Api {
  private var counter = 0

  def change(delta: Int) = {
    counter += delta
    emit(NewCounterValue(counter))
    counter
  }
}

object Server extends WebsocketServer[Channel, ApiEvent] with App {
  val channelPickler = implicitly[Pickler[Channel]]
  val eventPickler = implicitly[Pickler[ApiEvent]]
  val router = (wire.route[Api](_))(new ApiImpl(emit))

  def emit(event: ApiEvent): Unit = emit(Channel.fromEvent(event), event)

  val route = pathSingleSlash {
    getFromResource("index-dev.html")
  } ~ pathPrefix("assets") {
    //TODO from resource
    getFromDirectory("../frontend/target/scala-2.11/")
  }

  run("localhost", 8080) foreach { binding =>
    println(s"Server online at ${binding.localAddress}")
  }
}
