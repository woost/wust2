package wust.frontend.views

import org.scalajs.d3v4
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.Pipe
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Tag
import scala.math.Ordering

import org.scalajs.dom.{window, document, console}
import org.scalajs.dom.raw.{Text, Element, HTMLElement}
import scalatags.JsDom.all._
import scala.scalajs.js
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{Event, KeyboardEvent}
import collection.breakOut

object BoardView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val columnsRx: Rx[Seq[(Post, Seq[Post])]] = Rx {
      val graph = state.displayGraphWithoutParents().graph
      val columns = graph.postIds.filter(!graph.hasParents(_)).map(graph.postsById(_))(breakOut)
      columns.map{ column =>
        val items = graph.transitiveChildren(column.id).map(graph.postsById(_))(breakOut)
        (column, items)
      }
    }

    div(
      columnsRx.map{ columns =>
        div(
          display.flex,
          columns.map{
            case (column, items) =>
              div(
                flexGrow := "1",
                border := "1px solid #BBB",
                margin := "10px",
                padding := "3px",
                backgroundColor := "#F8F8F8", // basecolor
                h2(
                  column.title,
                  textAlign := "center",
                ),
                items.map{ item =>
                  div(
                    item.title,
                    backgroundColor := "#FFF",
                    border := "1px solid #BBB",
                    padding := "15px 10px",
                    margin := "5px"
                  )
                }
              ).render
          }
        ).render
      }
    )
  }
}
