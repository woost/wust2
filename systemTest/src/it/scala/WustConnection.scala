import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import org.specs2.execute.{AsResult, Failure, Result, ResultExecution}
import org.specs2.mutable
import org.specs2.specification.AroundEach

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object WustConnection {
  private implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val hostname = "localhost"
  val httpPort = sys.env("WUST_WEB_PORT").toInt
  val wsPort = sys.env("WUST_CORE_PORT").toInt
  val httpUrl = s"http://$hostname:$httpPort"
  val wsUrl = s"ws://$hostname:$wsPort/ws"

  lazy val nginxHttpConnection = Http().outgoingConnection(hostname, httpPort)
  lazy val wsHttpConnection = Http().outgoingConnection(hostname, wsPort)
  def wsConnection(flow: Flow[Message, Message, Future[Done]]) = Http().singleWebSocketRequest(WebSocketRequest(wsUrl), flow)

  def ws(sink: Sink[Message, Future[Done]], source: Source[Message, NotUsed]): (Future[WebSocketUpgradeResponse], Future[Done]) = {
    wsConnection(Flow.fromSinkAndSourceMat(sink, source)(Keep.left))
  }

  def wsGet(path: String): Future[HttpResponse] = {
    val request = RequestBuilding.Get(path)
    Source.single(request).via(wsHttpConnection).runWith(Sink.head)
  }

  def get(path: String): Future[HttpResponse] = {
    val request = RequestBuilding.Get(path)
    Source.single(request).via(nginxHttpConnection).runWith(Sink.head)
  }

  def retry(n: Int, sleepMillis: Int = 0)(fun: => Boolean): Boolean = fun match {
    case true => true
    case false =>
      if (n > 1) {
        if (sleepMillis > 0) Thread.sleep(sleepMillis)
        retry(n - 1, sleepMillis)(fun)
      }
      else false
  }

  def pathIsUp(get: => Future[HttpResponse], validate: HttpResponse => Boolean) =
    retry(10, sleepMillis = 1000)(Await.ready(get, 5.second).value.get.filter(validate).isSuccess)

  lazy val ready = {
    println("Waiting for Woost to be up...")
    pathIsUp(get("/"), r => r.status.isSuccess) &&
      pathIsUp(wsGet("/ws"), _.status == StatusCodes.BadRequest)
  }
}

trait WustReady extends mutable.Specification with AroundEach {
  def around[T: AsResult](t: => T): Result = {
    if (WustConnection.ready) ResultExecution.execute(AsResult(t))
    else Failure("Woost is down")
  }
}

trait Browser extends mutable.After {
  import java.util.logging.Level

  import org.openqa.selenium.logging.LogType
  import org.openqa.selenium.phantomjs.PhantomJSDriver

  import scala.collection.JavaConversions._

  val browser = new PhantomJSDriver {
    def errors: List[String] = {
      val logs = manage.logs.get(LogType.BROWSER)

      // errors in logger
      val errors = logs.filter(Level.SEVERE).toList

      // some exceptions are logged as stacktraces with loglevel warning
      // look for something that looks like a stacktrace
      val warningStacktraces = logs.filter(Level.WARNING).toList.filter { warning =>
        val head :: tail = warning.getMessage.split("\n").toList
        head.startsWith("TypeError: ") || tail.exists(_.matches("  [^ ]+ \\(http://.*/.*\\.js:[0-9]+:[0-9]+\\)"))
      }

      (errors ++ warningStacktraces).map(_.getMessage)
    }
  }

  browser.get(WustConnection.httpUrl)

  override def after = browser.quit()
}
