package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.util.collection._

object PropertyView {
  import SharedViewElements._

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      overflow.auto,
      padding := "10px",

      Rx {
        val graph = state.graph()
        val subjects = state.page().parentId.flatMap(graph.nodesByIdGet)

        // https://fomantic-ui.com/elements/list.html#description
        val list = div(cls := "ui list")
        val item = div(cls := "item")
        val content = div(cls := "content")
        val header = div(cls := "header")
        val description = div(cls := "description")
        list(
          item(
            content(
              header("Data"),
              description(subjects.map(_.data).mkString(", "))
            ),
            content(
              header("Role"),
              description(subjects.map(_.role).mkString(", "))
            ),
            content(
              header("Access"),
              description(subjects.map(_.meta.accessLevel).mkString(", "))
            ),
            content(
              header("Parents"),
              description(subjects.map { node =>
                val parents = graph.parentsIdx(graph.idToIdx(node.id)).map(graph.nodes)
                parents.map(p => nodeTag(state, p))
              })
            ),
            content(
              header("Children"),
              description(subjects.map { node =>
                val parents = graph.childrenIdx(graph.idToIdx(node.id)).map(graph.nodes)
                parents.map(p => nodeCard(p)(display.inlineBlock))
              })
            ),
            content(
              header("Before"),
              description(subjects.map { node =>
                val parents = graph.beforeIdx(graph.idToIdx(node.id)).map(graph.nodes)
                parents.map(p => nodeCard(p)(display.inlineBlock))
              })
            ),
            content(
              header("After"),
              description(subjects.map { node =>
                val parents = graph.afterIdx(graph.idToIdx(node.id)).map(graph.nodes)
                parents.map(p => nodeCard(p)(display.inlineBlock))
              })
            ),
          )
        )
      },
    )
  }
}
