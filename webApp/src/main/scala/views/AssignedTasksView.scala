package wust.webApp.views

import monix.reactive.subjects.PublishSubject
import outwatch.dom.VNode
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph.{Edge, GraphChanges, Node}
import wust.ids.{ChildId, EpochMilli, NodeId, ParentId, UserId}
import wust.webApp.state.{FocusState, GlobalState, Placeholder, TraverseState}
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
      "Next Month",
    )
    val buckets = Array[EpochMilli](
      renderTime,
      datePlusDays(renderTime, 1),
      datePlusDays(renderTime, 2),
      datePlusDays(renderTime, 7),
      datePlusDays(renderTime, 30)
    )

    val selectedUserId = Var(state.userId.now)

    val selectableUsers = Rx {
      val graph = state.graph()
      graph.members(focusState.focusedId)
    }

    val assignedTasks = Rx {
      AssignedTasksData.assignedTasks(state.graph(), selectedUserId(), buckets)
    }
    val assignedTasksDue = Rx { assignedTasks().dueTasks }
    val assignedTasksOther = Rx { assignedTasks().tasks }

    def addNewTask(str: String): Unit = {
      val newTask = Node.MarkdownTask(str)
      val changes = GraphChanges(
        addNodes = Array(newTask),
        addEdges = Array(
          Edge.Child(ParentId(focusState.focusedId), ChildId(newTask.id)),
          Edge.Assigned(newTask.id, selectedUserId.now),
        )
      )

      state.eventProcessor.changes.onNext(changes)
    }

    div(
      uniqueKeyed(focusState.focusedId.toStringFast), // needed for thunks below to be unique in nodeid
      width := "100%",
      Styles.flex,
      flexDirection.column,
      padding := "20px",
      overflow.auto,

      div(
        Styles.flex,
        alignItems.center,

        chooseUser(selectableUsers, selectedUserId).apply(Styles.flexStatic),

        InputRow(
          state,
          addNewTask,
          placeholder = Placeholder("Add Task"),
        ).apply(flexGrow := 1),
      ),

      Rx {
        var foundSomething = false
        val rendering = assignedTasksDue().mapWithIndex { (idx, dueTasks) =>
          VDomModifier.ifTrue(dueTasks.nonEmpty) {
            val bucketName = bucketNames(idx)
            val coloringHeader = if (idx == 0) VDomModifier(cls := "red", color.red) else cls := "grey"
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
          VDomModifier("Todo", cls := "grey"),
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

  private def chooseUser(users: Rx[Seq[Node.User]], selectedUserId: Var[UserId])(implicit ctx: Ctx.Owner): VNode = {
    div(
      Rx {
        Avatar.user(selectedUserId()).apply(height := "20px")
      },
      i(cls := "dropdown icon"),
      UI.dropdownMenu(
        VDomModifier(
          padding := "10px",
          div(cls := "item", display.none), //TODO ui dropdown needs at least one element
          users.map(_.map { user =>
            Components.renderUser(user).apply(cursor.pointer, onClick.stopPropagation(user.id) --> selectedUserId)
          }),
        ),
        close = selectedUserId.toTailObservable.map(_ => ()), dropdownModifier = cls := "top left"
      )
    )
  }
}
