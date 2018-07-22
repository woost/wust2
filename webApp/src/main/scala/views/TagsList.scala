package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._

object TagsList  {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    div(
      state.screenSize.map {
        case ScreenSize.Desktop => VDomModifier(
          width := "150px",
          Styles.flex,
          flexDirection.columnReverse,
          Rx {
            val graph = state.graphContent()

            val sortedContainments = graph.containments.toSeq.sortBy { containment =>
              val authorships = graph.authorshipsByNodeId(containment.sourceId)
              authorships.sortBy(_.data.timestamp: Long).last.data.timestamp: Long
            }

            sortedContainments.map(_.targetId).reverse.distinct.map { id =>
              nodeTag(state, graph.nodesById(id)).apply(margin := "2px")
            }
          }
        )
        case ScreenSize.Mobile => VDomModifier.empty
      }
    )
  }
}
