package wust.sdk

import wust.api._
import wust.api.serialize.Boopickle._
import covenant.ws._
import chameleon.ext.boopickle._
import boopickle.Default._
import java.nio.ByteBuffer

import colorado.HCL
import covenant.core.util.StopWatch
import sloth.LogHandler

import scala.concurrent.{ExecutionContext, Future}
import org.scalajs.dom.console


class BrowserLogHandler(implicit ec: ExecutionContext) extends LogHandler[Future] {
  import covenant.core.util.LogHelper._

  override def logRequest(path: List[String], arguments: Product, result: Future[_]): Unit = {
    val watch = StopWatch.started

    val baseHue = 0.75*Math.PI + scala.util.Random.nextDouble()*Math.PI // green to pink without red/orange/yellow
    val color = HCL(baseHue, 20, 93).toHex
    console.log(s"%c--> ${requestLogLine(path, arguments)}.", s"background: $color")
    result.onComplete { result =>
      console.log(s"%c<-- ${requestLogLine(path, arguments, result)}. Took ${watch.readHuman}.", s"background: $color")
    }
  }
}

private[sdk] trait NativeWustClient {
  def apply(location: String)(implicit ec: ExecutionContext) =
    new WustClientFactory(WsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config, new BrowserLogHandler))
}
