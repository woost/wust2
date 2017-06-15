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
import scala.scalajs.js.annotation.JSGlobal
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{Event, KeyboardEvent}

@js.native
@JSGlobal
object Prism extends js.Object {
  def highlightAll(): Unit = js.native
}

object CodeView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    //TODO: share code with displaygraph
    val articleGraph = {
      import state._
      val groupLockFilter: Graph => Graph = if (viewConfig.now.lockToGroup) {
        { graph =>
          val groupPosts = selectedGroupId.now.map(graph.postsByGroupId).getOrElse(Set.empty)
          graph.filter(groupPosts)
        }
      } else identity

      RxVar(rawGraph, Rx {
        val graph = groupLockFilter(rawGraph().consistent)
        graphSelection() match {
          case GraphSelection.Root =>
            Perspective(currentView(), graph)

          case GraphSelection.Union(parentIds) =>
            val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds // -- parentIds <--- this is the only difference to displaygraph
            val selectedGraph = graph.filter(transitiveChildren)
            Perspective(currentView(), selectedGraph)
        }
      })
    }

    div(
      articleGraph.map{ dg =>
        val graph = dg.graph
        if (graph.isEmpty) div().render
        else {
          val sorted = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)
          val content = div(
            cls := "article",
            sorted.map { postId =>
              val depth = graph.parentDepth(postId)
              val tag = if (graph.children(postId).size == 0) pre()
              else if (depth == 0) h1()
              else if (depth == 1) h2()
              else if (depth == 2) h3()
              else if (depth == 3) h4()
              else if (depth == 4) h5()
              else if (depth == 5) h6()
              else h6 ()
              val focusLink = span(
                span("#"),
                attr("aria-hidden") := "true",
                cls := "focuslink",
                onclick := { () => state.graphSelection() = GraphSelection.Union(Set(postId)) }
              ).render
              if (graph.children(postId).size == 0)
                tag(
                  attr("line-numbers").empty,
                  code(
                    cls := "language-java",
                    graph.postsById(postId).title
                  )
                )
              else
                tag(
                  focusLink,
                  graph.postsById(postId).title
                )
            }
          ).render
          setTimeout(200) {
            Prism.highlightAll()
          }
          content
        }
      }
    )
  }
}
