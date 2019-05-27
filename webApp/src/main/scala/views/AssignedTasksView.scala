package wust.webApp.views

import outwatch.dom.VNode
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.ids.{EpochMilli, NodeId}
import wust.webApp.state.{FocusState, GlobalState, TraverseState}
import wust.webApp.outwatchHelpers._
import wust.util.collection._
import wust.webApp
import wust.webApp.Ownable
import wust.webApp.dragdrop.DragItem
import wust.webApp.views.AssignedTasksData.AssignedTask

import scala.scalajs.js

object AssignedTasksView  {

  private def datePlusDays(now: EpochMilli, days: Int): EpochMilli = {
    val date = new js.Date(now)
    date.setHours(0)
    date.setMinutes(0)
    date.setSeconds(0)
    date.setMilliseconds(0)
    EpochMilli(date.getTime.toLong + days * EpochMilli.day)
  }

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val renderTime = EpochMilli.now
    val bucketNames = Array(
      "Overdue",
      "Today",
      "Tomorrow",
      "Next Week",
    )
    val buckets = Array[EpochMilli](
      renderTime,
      datePlusDays(renderTime, 1),
      datePlusDays(renderTime, 2),
      datePlusDays(renderTime, 7)
    )

    val assignedTasks = Rx {
      AssignedTasksData.assignedTasks(state.graph(), state.userId(), buckets)
    }
    val assignedTasksDue = Rx { assignedTasks().dueTasks }
    val assignedTasksOther = Rx { assignedTasks().tasks }

    div(
      keyed,
      width := "100%",
      Styles.flex,
      flexDirection.column,
      padding := "20px",
      overflow.auto,

      Rx {
        var foundSomething = false
        val rendering = assignedTasksDue().mapWithIndex { (idx, dueTasks) =>
          VDomModifier.ifTrue(dueTasks.nonEmpty) {
            val bucketName = bucketNames(idx)
            val coloringHeader = if (idx == 0) VDomModifier(cls := "red", color.red) else VDomModifier.empty
            foundSomething = true
            UI.segment(
              VDomModifier(coloringHeader, bucketName),
              dueTasks.map(renderTask(state, focusState, _)),
              segmentClass = "basic", segmentsClass = "basic"
            )
          }
        }
        if (foundSomething) VDomModifier(rendering)
        else h3(textAlign.center, padding := "10px", color.gray, "Nothing Due.")
      },

      Rx {
        val tasks = assignedTasksOther()
        VDomModifier.ifTrue(tasks.nonEmpty)(UI.segment(
          "Todo",
          assignedTasksOther().map(renderTask(state, focusState, _)),
          segmentClass = "basic", segmentsClass = "basic"
        ))
      },

      div(height := "20px") // padding bottom workaround in flexbox
    )
  }

  private def renderTask(state: GlobalState, focusState: FocusState, task: AssignedTask) = TaskNodeCard.renderThunk(
    state,
    focusState,
    TraverseState(task.parentId),
    task.nodeId,
    showCheckbox = true,
    inOneLine = true
  ).apply(margin := "8px")
}
