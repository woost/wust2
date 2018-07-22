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

    val sidebarWidth = "150px"
    val tagSidebar = VDomModifier(
      width := sidebarWidth,
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


    val sidebarToggleControl = div(
      freeSolid.faHashtag,
      onClick.map(_ => !sidebarVisible.now) --> sidebarVisible,
      cursor.pointer,
      position := "absolute", //TODO: better?
      bottom := "50px",
      paddingRight := "5px"
    )

    div(
      state.screenSize.map {
        case ScreenSize.Desktop => tagSidebar
        case ScreenSize.Mobile => VDomModifier(Rx {
          if (sidebarVisible()) VDomModifier(
            tagSidebar,
            sidebarToggleControl(right := sidebarWidth)
          )
          else sidebarToggleControl(right := "0px")
        })
      }
    )
  }
}
