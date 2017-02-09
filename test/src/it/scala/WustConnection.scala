import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

import akka.actor._
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.stream.scaladsl._
import akka.stream.ActorMaterializer

import org.specs2.mutable.Specification
import org.specs2.execute.{AsResult, Result, ResultExecution, Failure}
import org.specs2.specification.AroundEach

object WustConnection {
  private implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  lazy val httpConnection = Http().outgoingConnection("localhost", 80)
  def wsConnection(flow: Flow[Message, Message, Future[Done]]) = Http().singleWebSocketRequest(WebSocketRequest("ws://localhost/ws"), flow)

  def ws(sink: Sink[Message, Future[Done]], source: Source[Message, NotUsed]): (Future[WebSocketUpgradeResponse], Future[Done]) = {
    wsConnection(Flow.fromSinkAndSourceMat(sink, source)(Keep.left))
  }

  def get(path: String): Future[HttpResponse] = {
    val request = RequestBuilding.Get(path)
    Source.single(request).via(httpConnection).runWith(Sink.head)
  }

  def retry(n: Int, sleepMillis: Int = 0)(fun: => Boolean): Boolean = fun match {
    case true => true
    case false =>
      if (n > 1) {
        if (sleepMillis > 0) Thread.sleep(sleepMillis)
        retry(n - 1, sleepMillis)(fun)
      } else false
  }

  def pathIsUp(path: String, validate: HttpResponse => Boolean) =
    retry(10, sleepMillis = 1000)(Await.ready(get(path), 5.second).value.get.filter(validate).isSuccess)

  lazy val ready = {
    println("Waiting for Wust to be up...")
    pathIsUp("/", r => r.status.isSuccess) &&
      pathIsUp("/ws", _.status != StatusCodes.BadGateway)
  }
}

trait WustReady extends Specification with AroundEach {
  def around[T: AsResult](t: => T): Result = {
    if (WustConnection.ready) ResultExecution.execute(AsResult(t))
    else Failure("Wust is down")
  }
}
