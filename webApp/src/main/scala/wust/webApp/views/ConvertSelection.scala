package wust.webApp.views

import fontAwesome.IconLookup
import outwatch.dom._
import outwatch.dom.dsl._
import wust.facades.googleanalytics.Analytics
import wust.graph.{GraphChanges, Node}
import wust.ids.NodeRole
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._

final case class ConvertSelection(
  role: NodeRole,
  icon: IconLookup,
  description: String,
)
object ConvertSelection {

  def menuItem(state: GlobalState, node: Node.Content): VNode = {
    a(
      cls := "item",
      Elements.icon(Icons.convertItem),
      span("Convert"),

      Components.horizontalMenu(
        ConvertSelection.all.map { convert =>
          Components.MenuItem(
            title = Elements.icon(convert.icon),
            description = VDomModifier(
              fontSize.xSmall,
              convert.role.toString,
            ),
            active = node.role == convert.role,
            clickAction = { () =>
              state.eventProcessor.changes.onNext(GraphChanges.addNode(node.copy(role = convert.role)))
              Analytics.sendEvent("pageheader", "changerole", convert.role.toString)
            }
          )
        }
      )
    )
  }

  val all =
    ConvertSelection(
      role = NodeRole.Message,
      icon = Icons.conversation,
      description = "Message of a workspace or chat.",
    ) ::
      ConvertSelection(
        role = NodeRole.Task,
        icon = Icons.task,
        description = "Task item of a list or kanban.",
      ) ::
      ConvertSelection(
        role = NodeRole.Project,
        icon = Icons.project,
        description = "A Project. It can contain conversations and tasks.",
      ) ::
      ConvertSelection(
        role = NodeRole.Note,
        icon = Icons.note,
        description = "Note for notes and documentation.",
      ) ::
      // ConvertSelection(
      //   role = NodeRole.Stage,
      //   icon = Icons.stage,
      //   description = "Stage, a period in a structure / progress, e.g. column in a kanban",
      // ) ::
      Nil
}
