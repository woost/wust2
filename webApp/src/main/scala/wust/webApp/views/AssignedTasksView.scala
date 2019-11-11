package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.graph.{ Edge, GraphChanges, Node }
import wust.ids.{ ChildId, EpochMilli, ParentId, UserId }
import wust.util.collection._
import wust.webApp.state.{ FocusState, GlobalState, Placeholder, TraverseState }
import wust.webApp.views.AssignedTasksData.AssignedTask
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.UI
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

import scala.scalajs.js

object AssignedTasksView {

  private def datePlusDays(now: EpochMilli, days: Int): EpochMilli = {
    val date = new js.Date(now)
    date.setHours(0)
    date.setMinutes(0)
    date.setSeconds(0)
    date.setMilliseconds(0)
    EpochMilli(date.getTime.toLong + days * EpochMilli.day)
  }

  def apply(focusState: FocusState, deepSearch: Boolean = false, selectedUserId: Var[Option[UserId]] = Var(Some(GlobalState.userId.now)))(implicit ctx: Ctx.Owner): VNode = {
    val renderTime = EpochMilli.now
    val bucketNames = Array(
      "Overdue",
      "Today",
      "Tomorrow",
      "Within a Week",
      "Within a Month",
    )
    val buckets = Array[EpochMilli](
      renderTime,
      datePlusDays(renderTime, 1),
      datePlusDays(renderTime, 2),
      datePlusDays(renderTime, 7),
      datePlusDays(renderTime, 30)
    )

    val selectableUsers = Rx {
      val graph = GlobalState.graph()
      graph.members(focusState.focusedId)
    }

    val assignedTasks = Rx {
      AssignedTasksData.assignedTasks(GlobalState.graph(), focusState.focusedId, selectedUserId(), buckets, deepSearch)
    }
    val assignedTasksDue = Rx { assignedTasks().dueTasks }
    val assignedTasksOther = Rx { assignedTasks().tasks }

    def addNewTask(sub: InputRow.Submission): Unit = {
      val newTask = Node.MarkdownTask(sub.text)
      val changes = GraphChanges(
        addNodes = Array(newTask),
        addEdges = selectedUserId.now.fold (
          Array[Edge](
            Edge.Child(ParentId(focusState.focusedId), ChildId(newTask.id))
          )
        ) (uid =>
            Array[Edge](
              Edge.Child(ParentId(focusState.focusedId), ChildId(newTask.id)),
              Edge.Assigned(newTask.id, uid)
            )),
      )

      GlobalState.submitChanges(changes merge sub.changes(newTask.id))
    }

    div(
      uniqueKeyed(focusState.focusedId.toStringFast), // needed for thunks below to be unique in nodeid
      width := "100%",
      Styles.flex,
      flexDirection.column,
      padding := "20px",

      div(
        Styles.flex,
        alignItems.center,

        chooseUser(selectableUsers, selectedUserId).apply(margin := "10px", Styles.flexStatic),
        InputRow(
          Some(focusState),
          addNewTask,
          placeholder = Placeholder("Add Task"),
          showSubmitIcon = false,
          submitOnEnter = true,
        ).apply(flexGrow := 1),
      ),

      Rx {
        var foundSomething = false
        val rendering = assignedTasksDue().mapWithIndex { (idx, dueTasks) =>
          VDomModifier.ifTrue(dueTasks.nonEmpty) {
            val bucketName = bucketNames(idx)
            val coloringHeader = if (idx == 0) VDomModifier(cls := "red", color.red) else cls := "grey"
            foundSomething = true
            VDomModifier(
              h3(coloringHeader, bucketName, cls := "tasklist-header", fontSize.small),
              div(cls := "tasklist", dueTasks.sortBy(_.dueDate).map(renderTask(focusState, _))),
            )
          }
        }
        if (foundSomething) VDomModifier(rendering)
        else div(textAlign.center, padding := "20px", opacity := 0.4, "Nothing due")
      },

      Rx {
        val tasks = assignedTasksOther()
        VDomModifier.ifTrue(tasks.nonEmpty)(
          h2("Assigned tasks", fontSize.large, cls := "tasklist-header", marginBottom := "15px"),
          div(cls := "tasklist", assignedTasksOther().map(renderTask(focusState, _))),
        )
      },

      div(height := "20px") // padding bottom workaround in flexbox
    )
  }

  private def renderTask(focusState: FocusState, task: AssignedTask) = TaskNodeCard.renderThunk(

    focusState,
    TraverseState(task.parentId),
    task.nodeId,
    showCheckbox = true,
    inOneLine = true
  )

  private def chooseUser(users: Rx[Seq[Node.User]], selectedUserId: Var[Option[UserId]])(implicit ctx: Ctx.Owner): VNode = {
    val close = SinkSourceHandler.publish[Unit]
    val avatarSize = "20px"
    val allUsersDiv = div(height := avatarSize)
    div(
      div(
        Styles.flex,
        Rx {
          val userIdOpt: Option[UserId] = selectedUserId()
          userIdOpt match {
            case Some(userId) =>
              users().find(_.id == userId).map { user =>
                Avatar.user(user, size = avatarSize, enableDrag = false)
              }: VDomModifier
            case _ => allUsersDiv("All", width := avatarSize)
          }
        },
        i(cls := "dropdown icon"),
      ),
      UI.dropdownMenu(
        VDomModifier(
          padding := "10px",
          users.map(_.map { user =>
            div(
              cls := "item",
              Components.renderUser(user, enableDrag = false).apply(
                backgroundColor := "transparent", // overwrite white background
              ),
              onClickDefault.foreach {
                close.onNext(())
                selectedUserId() = Some(user.id)
              }
            )
          }),
          div(
            cls := "item",
            allUsersDiv(
              "All Due Dates",
            ),
            onClickDefault.foreach {
              close.onNext(())
              selectedUserId() = None
            }
          )
        ),
        close = close,
        dropdownModifier = cls := "top left"
      )
    )
  }
}
