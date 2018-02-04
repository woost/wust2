package wust.sdk

import wust.api._, serialize.Boopickle._
import wust.ids._
import wust.util.time.StopWatch

import monix.execution.Cancelable
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import boopickle.Default._
import sloth.core._
import sloth.mycelium._
import sloth.client._
import mycelium.client._
import chameleon.ext.boopickle._
import cats.implicits._
import shapeless._

import java.nio.ByteBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success,Failure}

case class ApiException(error: ApiError) extends Exception(s"Api returned error: $error")
object ApiException {
  implicit def clientFailureConvert = new ClientFailureConvert[ApiException] {
    def convert(failure: ClientFailure): ApiException = ApiException(ApiError.ClientError(failure.toString))
  }
}

class WustClient(client: Client[ByteBuffer, Future, ApiException]) {
  val api = client.wire[Api[Future]]
  val auth = client.wire[AuthApi[Future]]
}
trait WustClientOps {
  val clientFactory: WustClientFactory
  def apply(sendType: SendType = SendType.WhenConnected, requestTimeout: FiniteDuration = 30 seconds) = clientFactory.sendWith(sendType, requestTimeout)
  lazy val nowOrFail = apply(SendType.NowOrFail)
  lazy val highPriority = apply(SendType.WhenConnected.highPriority)
  lazy val lowPriority = apply(SendType.WhenConnected.lowPriority)
  lazy val defaultPriority = apply(SendType.WhenConnected)
  lazy val api: Api[Future] = defaultPriority.api
  lazy val auth: AuthApi[Future] = defaultPriority.auth
}

class WustIncidentHandler(implicit ec: ExecutionContext) extends IncidentHandler[ApiEvent] {
  private val eventSubject = PublishSubject[Seq[ApiEvent]]()
  final val eventObservable: Observable[Seq[ApiEvent]] = eventSubject

  final override def onEvents(events: Seq[ApiEvent]): Unit = eventSubject.onNext(events).onComplete {
    case Success(_) =>
    case Failure(t) => scribe.warn(s"Failed to push events into event subject: $t")
  }
}

class WustClientFactory private(ws: WebsocketClient[ByteBuffer, ApiEvent, ApiError])(implicit ec: ExecutionContext) {
  def sendWith(sendType: SendType, requestTimeout: FiniteDuration): WustClient = {
    val transport = ws.toTransport(sendType, requestTimeout, onError = err => new ApiException(err))
    val client = Client[ByteBuffer, Future, ApiException](transport, new ClientLogHandler)
    new WustClient(client)
  }
}
private[sdk] object WustClientFactory {
  def createAndRun(location: String, handler: IncidentHandler[ApiEvent], connection: WebsocketConnection[ByteBuffer])(implicit ec: ExecutionContext): WustClientFactory = {
    val config = WebsocketClientConfig(pingInterval = 100 seconds) // needs to be in sync with idle timeout of backend
    val ws = WebsocketClient[ByteBuffer, ApiEvent, ApiError](connection, config, handler)
    ws.run(location)

    new WustClientFactory(ws)
  }
}
private[sdk] class ClientLogHandler(implicit ec: ExecutionContext) extends LogHandler[Future] {
  override def logRequest(path: List[String], arguments: Any, result: Future[_]): Unit = {
    val watch = StopWatch.started
    result.onComplete { result =>
      scribe.info(s"Outgoing request (path = ${path.mkString("/")}, arguments = $arguments): $result. Took ${watch.readHuman}.")
    }
  }
}
