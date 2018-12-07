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

object ListView {
  import SharedViewElements._

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      overflow.auto,
      padding := "10px",

      addListItemInputField(state),

      Rx {
        state.page().parentId.map { pageParentId =>
          val graph = {
            val g = state.graph()
            val transitivePageChildren = g.notDeletedDescendants(pageParentId)
            g.filterIds(Set(pageParentId) ++ transitivePageChildren.toSet ++ transitivePageChildren.flatMap(id => g.authors(id).map(_.id)))
          }

          val pageParentIdx = graph.idToIdx(pageParentId)

          val doneIdx = graph.doneNodeIdx(pageParentIdx).getOrElse(-1)
          val allTasks: ArraySet = graph.subset { nodeIdx =>
            val node = graph.nodes(nodeIdx)

            @inline def isDoneNode = nodeIdx == doneIdx
            @inline def isContent = node.isInstanceOf[Node.Content]
            @inline def isTask = node.role.isInstanceOf[NodeRole.Task.type]
            @inline def noPage = nodeIdx != pageParentIdx

            !isDoneNode && isContent && isTask && noPage
          }


          val (todoTasks, doneTasks) = {
            doneIdx match {
              case -1      => (allTasks, ArraySet.create(graph.nodes.length))
              case doneIdx =>
                allTasks.partition { nodeIdx =>
                  @inline def isDone = graph.parentsIdx.exists(nodeIdx)(_ == doneIdx)

                  !isDone
                }
            }
          }

          VDomModifier(
            div(todoTasks.map { nodeIdx =>
              val node = graph.nodes(nodeIdx)
              nodeCardWithCheckbox(state, node, pageParentId :: Nil).apply(margin := "4px")
            }),

            doneTasks.calculateNonEmpty.ifTrue[VDomModifier](hr(border := "1px solid black", opacity := 0.4, margin := "15px")),

            div(
              opacity := 0.5,
              doneTasks.map { nodeIdx =>
                val node = graph.nodes(nodeIdx)
                nodeCardWithCheckbox(state, node, pageParentId :: Nil).apply(margin := "4px")
              })
          )
        }

      },
    )
  }


  private def addListItemInputField(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    def submitAction(str: String) = {
      val change = GraphChanges.addMarkdownTask(str)
      state.eventProcessor.enriched.changes.onNext(change)
    }

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add."

    val inputFieldFocusTrigger = PublishSubject[Unit]

    if(!BrowserDetect.isMobile) {
      state.page.triggerLater {
        inputFieldFocusTrigger.onNext(Unit) // re-gain focus on page-change
        ()
      }
    }

    inputRow(state, submitAction,
      preFillByShareApi = true,
      autoFocus = !BrowserDetect.isMobile,
      triggerFocus = inputFieldFocusTrigger,
      placeHolderMessage = Some(placeHolder),
      submitIcon = freeSolid.faPlus
    )(ctx)(Styles.flexStatic)
  }

}
