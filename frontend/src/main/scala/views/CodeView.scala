package wust.frontend.views

import wust.frontend._
import wust.graph._

import org.scalajs.dom.{ window, document, console }
import org.scalajs.dom.raw.{ Text, Element, HTMLElement }
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{Event, KeyboardEvent}
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{ Event, KeyboardEvent }

@js.native
@JSGlobal
object Prism extends js.Object {
  def highlightAll(): Unit = js.native
}

// object CodeView {

//   def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
//     div(
//       state.displayGraphWithParents.map{ dg =>
//         val graph = dg.graph
//         if (graph.isEmpty) div().render
//         else {
//           val sorted = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)
//           val content = div(
//             cls := "article",
//             sorted.map { postId =>
//               val depth = graph.parentDepth(postId)
//               val tag = if (graph.children(postId).size == 0) pre()
//               else if (depth == 0) h1()
//               else if (depth == 1) h2()
//               else if (depth == 2) h3()
//               else if (depth == 3) h4()
//               else if (depth == 4) h5()
//               else if (depth == 5) h6()
//               else h6 ()
//               val focusLink = span(
//                 span("#"),
//                 attr("aria-hidden") := "true",
//                 cls := "focuslink",
//                 onclick := { () => state.graphSelection() = GraphSelection.Union(Set(postId)) }
//               ).render
//               if (graph.children(postId).size == 0)
//                 tag(
//                   attr("line-numbers").empty,
//                   code(
//                     cls := "language-java",
//                     graph.postsById(postId).title
//                   )
//                 )
//               else
//                 tag(
//                   focusLink,
//                   graph.postsById(postId).title
//                 )
//             }
//           ).render
//           setTimeout(200) {
//             Prism.highlightAll()
//           }
//           content
//         }
//       }
//     ).render
//   }
// }
