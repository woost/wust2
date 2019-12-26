package wust.sdk

import java.nio.ByteBuffer

import boopickle.Default._
import chameleon.ext.boopickle._
import colorado.HCL
import covenant.core.util.StopWatch
import covenant.ws._
import monix.reactive.Observer
import org.scalajs.dom.console
import sloth.{ClientException, ClientFailure, LogHandler}
import wust.api._
import wust.api.serialize.Boopickle._
import wust.graph.{Graph, Page}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success}

class BrowserLogHandler(apiError: Observer[Unit])(implicit ec: ExecutionContext) extends LogHandler[Future] {
  import covenant.core.util.LogHelper._

  override def logRequest[T](path: List[String], arguments: Product, result: Future[T]): Future[T] = {
    val watch = StopWatch.started

    val randomHue = 0.75 * Math.PI + scala.util.Random
      .nextDouble() * Math.PI // green to pink without red/orange/yellow, to not look like errors/warnings
    val baseHue: Double = path match {
      case List("Api", "getGraph") =>
        arguments.productIterator.toList.head.asInstanceOf[Page].parentId.map{ parentId =>
          NodeColor.defaultHue(parentId)
        }
        .getOrElse(randomHue)
      case _ => randomHue
    }
    val boxBgColor = HCL(baseHue, 50, 63).toHex
    val boxStyle =
      s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold"
    val color = HCL(baseHue, 20, 93).toHex

    // log request
    console.log(
      s"%c ➚ ${path.mkString(".")} %c ${arguments.productIterator.toList.mkString(", ")}",
      boxStyle,
      s"background: $color"
    )

    // log result
    result.onComplete { result =>
      val time = watch.readMillis
      val timeColor = time match {
        case t if t < 100  => "#888"
        case t if t < 1000 => "#EB9800"
        case _             => "#F20500"
      }
      val timeStyle =
        s"color: $timeColor; background: #EEE; border-radius: 3px; padding: 2px 6px; font-weight: bold"

      def logError(t: Throwable, msg: String = "Error"): Unit = console.log(
        s"%c ➘ ${path.mkString(".")} %c $msg: ${t.getMessage} %c${watch.readHuman}",
        boxStyle + "; border: 3px solid #C83D3A",
        s"background: #FFF0F0; color: #FF0B0B",
        timeStyle
      )

      def logInGroup(code: => Unit): Unit = {
        console
          .asInstanceOf[js.Dynamic]
          .groupCollapsed(s"%c ➘ ${path.mkString(".")} %c${watch.readHuman}", boxStyle, timeStyle)
        code
        console.asInstanceOf[js.Dynamic].groupEnd()
      }

      result match {

        case Success(response) =>
          response match {
            case graph: Graph => // graph is always grouped and logged as table
              logInGroup {
                console.log(graph.toDetailedString)
              }
            case _ if response.toString.length < 80 => // short data is displayed without grouping
              console.log(
                s"%c ➘ ${path.mkString(".")} %c $response %c${watch.readHuman}",
                boxStyle,
                s"background: $color",
                timeStyle
              )
            case _ => // everything else is grouped
              logInGroup {
                console.log(s"%c $response", s"background: $color")
              }
          }

        case Failure(throwable) => throwable match {

          case ClientException(error) => error match {
            case ClientFailure.DeserializerError(t) =>
              apiError.onNext(())
              logError(t, msg = "Cannot deserialize response from server")
            case ClientFailure.TransportError(t) =>
              logError(t, msg = "Error in client transport")
          }

          case WsClient.ErrorException(error: ApiError) =>
            error match {
              case ApiError.IncompatibleApi =>
                apiError.onNext(())
                scribe.error(s"ApiError: API is incompatible!")
              case error =>
                scribe.warn(s"ApiError: $error")
            }

          case t => logError(t)
        }
      }
    }

    result
  }
}

private[sdk] trait NativeWustClient {
  def apply(location: String, apiError: Observer[Unit], enableRequestLogging: Boolean)(implicit ec: ExecutionContext): WustClientFactory[Future] = {
    val logger = if (enableRequestLogging) new BrowserLogHandler(apiError)
    else LogHandler.empty[Future] //new DefaultLogHandler[Future](identity)
    new WustClientFactory(
      WsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config, logger)
    )
  }
}
