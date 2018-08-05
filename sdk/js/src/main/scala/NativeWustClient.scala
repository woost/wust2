package wust.sdk

import wust.api._
import wust.api.serialize.Boopickle._
import covenant.ws._
import chameleon.ext.boopickle._
import boopickle.Default._
import java.nio.ByteBuffer

import colorado.HCL
import covenant.core.DefaultLogHandler
import covenant.core.util.StopWatch
import sloth.LogHandler

import scala.concurrent.{ExecutionContext, Future}
import org.scalajs.dom.console
import wust.graph.{Graph, Page}

import scala.scalajs.{LinkingInfo, js}
import scala.scalajs.js.JSConverters._
import scala.util.{Failure, Success}
import scala.collection.breakOut

class BrowserLogHandler(implicit ec: ExecutionContext) extends LogHandler[Future] {
  import covenant.core.util.LogHelper._

  override def logRequest(path: List[String], arguments: Product, result: Future[_]): Unit = {
    val watch = StopWatch.started

    val randomHue = 0.75 * Math.PI + scala.util.Random
      .nextDouble() * Math.PI // green to pink without red/orange/yellow, to not look like errors/warnings
    val baseHue: Double = path match {
      case List("Api", "getGraph") =>
        NodeColor
          .pageHue(arguments.productIterator.toList.head.asInstanceOf[Page])
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
              val rows = (graph.outgoingEdges.map {
                case (nodeId, edges) =>
                  val node = graph.nodesById(nodeId)
                  val es = edges.map(
                    edge =>
                      s"${edge.data.tpe.take(1)} ${edge.targetId.toBase58
                        .takeRight(3)} ${graph.nodesById(edge.targetId).data.str}"
                  )(breakOut): List[String]
                  val id = node.id.toBase58.takeRight(3)
                  val tpe = node.data.tpe.take(1)
                  val content = node.data.str
                  (s"$tpe $id" :: content :: es).toJSArray
              }(breakOut): List[js.Array[String]]).sortBy(_(0)).toJSArray

              logInGroup {
                console.asInstanceOf[js.Dynamic].table(rows)
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
        case Failure(throwable) =>
          console.log(
            s"%c ➘ ${path.mkString(".")} %c error: ${throwable.getMessage} %c${watch.readHuman}",
            boxStyle + "; border: 3px solid #C83D3A",
            s"background: #FFF0F0; color: #FF0B0B",
            timeStyle
          )
      }
    }
  }
}

private[sdk] trait NativeWustClient {
  def apply(location: String)(implicit ec: ExecutionContext) = {
    val logger = if (LinkingInfo.developmentMode) new BrowserLogHandler
    else new DefaultLogHandler[Future](identity)
    new WustClientFactory(
      WsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config, logger)
    )
  }
}
