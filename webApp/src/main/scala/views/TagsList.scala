package wust.webApp.views

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.webApp._
import wust.graph.Graph
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Components._
import wust.util._

object TagsList  {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val sidebarVisible = Var[Boolean](false)

    def tagsList(graph: Graph) = VDomModifier(
      overflow.auto,
      height := "100%",
      width := "150px",
      Styles.flex,
      flexDirection.columnReverse,
      {
        val sortedContainments = graph.containments.toSeq.sortBy { containment =>
          val authorships = graph.authorshipsByNodeId(containment.sourceId)
          authorships.sortBy(_.data.timestamp: Long).last.data.timestamp: Long
        }

        sortedContainments.map(_.targetId).reverse.distinct.map { id =>
          nodeTag(state, graph.nodesById(id)).apply(margin := "2px")
        }
      }
    )

    val overlay = VDomModifier(
      position := "absolute", //TODO: better?
      bottom := "0px",
      right := "0px"
    )

    def tagsListToggleControl(graph: Graph, additional: VDomModifier = VDomModifier.empty) =
      graph.containments.nonEmpty.ifTrue[VDomModifier](div(
        freeSolid.faHashtag,
        onClick.map(_ => !sidebarVisible.now) --> sidebarVisible,
        cursor.pointer,
        paddingRight := "5px",
        additional
      ))

    div(
      backgroundColor := "rgba(255,255,255,0.4)",
      padding := "4px",

      Rx {
        val graph = state.graphContent()
        state.screenSize() match {
          case ScreenSize.Large => tagsList(graph)
          case _ =>
            if (sidebarVisible()) VDomModifier(
              Styles.flex,
              flexDirection.row,
              alignItems.flexEnd,
              overflow.auto,
              tagsListToggleControl(graph),
              div(tagsList(graph)),
              overlay
            ) else tagsListToggleControl(graph, overlay)
        }
      }
    )
  }
}
