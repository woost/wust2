package wust.webApp.views

import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import colibri._
import rx._
import wust.css.Styles
import wust.graph.{Edge, GraphChanges, Node}
import wust.ids.{ChildId, GlobalNodeSettings, ParentId, UserId}
import wust.util.collection._
import wust.webApp.state.{FocusState, GlobalState, Placeholder, TraverseState}
import wust.webApp.views.AssignedTasksData.AssignedTask
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.UI
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

object AssignedTasksView {

  def apply(assignedTasks: Rx[AssignedTasksData.AssignedTasks], focusState: FocusState, selectedUserId: Var[Option[UserId]])(implicit ctx: Ctx.Owner): HtmlVNode = {
    val node = Rx {
      val g = GlobalState.rawGraph()
      g.nodesById(focusState.focusedId)
    }
    val globalNodeSettings = node.map(_.flatMap(_.settings).fold(GlobalNodeSettings.default)(_.globalOrDefault))

    val selectableUsers = Rx {
      val graph = GlobalState.graph()
      graph.members(focusState.focusedId)
    }

    val buckets = DueDate.Bucket.values
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
        Rx {
          InputRow(
            Some(focusState),
            addNewTask,
            placeholder = Placeholder(s"Add ${ globalNodeSettings().itemName }"),
            showSubmitIcon = false,
            submitOnEnter = true,
          ).apply(flexGrow := 1)
        },
      ),

      Rx {
        var foundSomething = false
        val rendering = assignedTasksDue().mapWithIndex { (idx, dueTasks) =>
          VDomModifier.ifTrue(dueTasks.nonEmpty) {
            val bucketName = buckets(idx).name
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
          //TODO: Is there a phrasing where we can use ${globalNodeSettings().itemName} (which is singular)? "Your Assignments"?
          h2(s"Assigned Tasks", fontSize.large, cls := "tasklist-header", marginBottom := "15px"),
          div(cls := "tasklist", assignedTasksOther().map(renderTask(focusState, _))),
        )
      },

      div(height := "20px") // padding bottom workaround in flexbox
    )
  }

  def apply(focusState: FocusState, deepSearch: Boolean = false, selectedUserId: Var[Option[UserId]] = Var(Some(GlobalState.userId.now)))(implicit ctx: Ctx.Owner): VNode = {
    val assignedTasks = Rx {
      AssignedTasksData.assignedTasks(GlobalState.graph(), focusState.focusedId, selectedUserId(), deepSearch)
    }

    apply(assignedTasks, focusState, selectedUserId)
  }

  private def renderTask(focusState: FocusState, task: AssignedTask) = TaskNodeCard.renderThunk(

    focusState,
    TraverseState(task.parentId),
    task.nodeId,
    showCheckbox = true,
    inOneLine = true
  )

  private def chooseUser(users: Rx[Seq[Node.User]], selectedUserId: Var[Option[UserId]])(implicit ctx: Ctx.Owner): VNode = {
    val close = Subject.publish[Unit]
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
