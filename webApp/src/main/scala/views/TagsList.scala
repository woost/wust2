package wust.webApp.views

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._

object TagsList  {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val sidebarVisible = Var[Boolean](false)

    val tagSidebar = VDomModifier(
      overflow.auto,
      height := "100%",
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

    val overlay = VDomModifier(
      position := "absolute", //TODO: better?
      bottom := "100px",
      right := "0px"
    )

    val sidebarToggleControl = div(
      freeSolid.faHashtag,
      onClick.map(_ => !sidebarVisible.now) --> sidebarVisible,
      cursor.pointer,
      paddingRight := "5px"
    )

    div(
      state.screenSize.map[VDomModifier] {
        case ScreenSize.Desktop => tagSidebar
        case ScreenSize.Mobile => Rx {
          if (sidebarVisible())
          VDomModifier(
            Styles.flex,
            flexDirection.row,
            alignItems.flexEnd,
            overflow.auto,
            height := "60%",
            sidebarToggleControl,
            div(tagSidebar),
            overlay
          )
          else sidebarToggleControl(overlay)
        }
      }
    )
  }
}
