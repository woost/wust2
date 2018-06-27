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
import wust.graph.Graph

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.util.{Failure, Success}
import scala.collection.breakOut


class BrowserLogHandler(implicit ec: ExecutionContext) extends LogHandler[Future] {
  import covenant.core.util.LogHelper._

  override def logRequest(path: List[String], arguments: Product, result: Future[_]): Unit = {
    val watch = StopWatch.started

    val baseHue = 0.75*Math.PI + scala.util.Random.nextDouble()*Math.PI // green to pink without red/orange/yellow
    val boxBgColor = HCL(baseHue, 50, 63).toHex
    val boxStyle = s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold"
    val color = HCL(baseHue, 20, 93).toHex

    // log request
    console.log(
      s"%c ➚ ${path.mkString(".")} %c ${arguments.productIterator.toList.mkString(", ")}",
      boxStyle ,
      s"background: $color"
    )



    // log result
    result.onComplete { result =>
      val time = watch.readMillis
      val timeColor = time match {
        case t if t < 100 => "#888"
        case t if t < 1000 => "#EB9800"
        case _ => "#F20500"
      }
      val timeStyle = s"color: $timeColor; background: #EEE; border-radius: 3px; padding: 2px 6px; font-weight: bold"



      def logInGroup (code: => Unit):Unit = {
        console.asInstanceOf[js.Dynamic].groupCollapsed(s"%c ➘ ${path.mkString(".")} %c${watch.readHuman}", boxStyle, timeStyle)
        code
        console.asInstanceOf[js.Dynamic].groupEnd()
      }

      result match {
        case Success(response) =>
          response match {
            case graph:Graph => // graph is always grouped
              val rows = (graph.outgoingEdges.map{case (nodeId, edges) =>
                val node = graph.nodesById(nodeId)
                val es = edges.map( edge =>
                  s"${edge.data.tpe} ${edge.targetId.toCuidString.takeRight(4)}")(breakOut):List[String]
                (node.data.str :: node.data.tpe :: node.id.toCuidString.takeRight(4) :: es).toJSArray
              }(breakOut):List[js.Array[String]]).sortBy(_(0)).toJSArray

              logInGroup{
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
              logInGroup{
                console.log(s"%c $response", s"background: $color")
              }
          }
        case Failure(throwable) =>
          console.log(
            s"%c ➘ ${path.mkString(".")} %c error: ${throwable.getMessage} %c${watch.readHuman}",
            boxStyle + "; border: 3px solid #C83D3A", s"background: #FFF0F0; color: #FF0B0B",
            timeStyle
          )
      }
    }
  }
}

private[sdk] trait NativeWustClient {
  def apply(location: String)(implicit ec: ExecutionContext) =
    new WustClientFactory(WsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config, new BrowserLogHandler))
}
