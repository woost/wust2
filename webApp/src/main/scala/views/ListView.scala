package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import wust.util.ArraySet
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object ListView {
  import SharedViewElements._

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      overflow.auto,

      Rx {
        withLoadingAnimation(state) {
          val page = state.page()

          val graph = {
            val g = state.graph()
            val transitivePageChildren = page.parentIds.flatMap(g.notDeletedDescendants)
            g.filterIds(page.parentIdSet ++ transitivePageChildren.toSet ++ transitivePageChildren.flatMap(id => g.authors(id).map(_.id)))
          }

          val pageParentArraySet = graph.createArraySet(page.parentIdSet)

          val allTasks:ArraySet = graph.subset { nodeIdx =>
            val node = graph.nodes(nodeIdx)

            @inline def isContent = node.isInstanceOf[Node.Content]
            @inline def isTask = node.role.isInstanceOf[NodeRole.Task.type]
            @inline def noPage = pageParentArraySet.containsNot(nodeIdx)

            isContent && isTask && noPage
          }

          val (todoTasks, doneTasks) = allTasks.partition { nodeIdx =>

            true
          }

          VDomModifier(
            div(todoTasks.map(nodeIdx =>
              nodeCard(graph.nodes(nodeIdx)).apply(margin := "2px").prepend(
                Styles.flex,
                alignItems.flexStart,
                div(
                  cls := "ui checkbox fitted",
                  marginTop := "5px",
                  marginLeft := "5px",
                  marginRight := "3px",
                  input(
                    tpe := "checkbox",
                    onChange.checked foreach { checked =>
                    }
                  ),
                  label()
                )
)
            ))
          )
        }
      },
    )
  }

}
