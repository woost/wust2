package wust.ids

import cats.data.NonEmptyList
import wust.util.collection._
import wust.util.macros.SubObjects

import scala.collection.breakOut

// BE AWARE: Whenever you rename/change/delete a View from here, you have to write a DB MIGRATION
// to update the json views in the node table.

case class ViewConfig(
  view: View.Custom,
)

sealed trait View extends Product with Serializable {
  def viewKey: String //TODO remove viewkey except for view.system.
  def isContent: Boolean = true
}
object View {

  sealed trait Custom extends View

  sealed trait System extends View

  //TOOD move roles out of view.
  final case class Table(roles: List[NodeRole]) extends Custom {
    def viewKey = s"table${roles.map(_.toString.toLowerCase).mkString(":", ":", "")}"
    // override def toString = s"Table(${roles.mkString(",")})"
    override def toString = "Table with Task Details"
  }
  case object Thread extends Custom {
    override def toString = "Chat with Threads"
    def viewKey = "thread"
  }
  case object Chat extends Custom {
    def viewKey = "chat"
  }
  case object Files extends Custom {
    def viewKey = "files"
  }
  case class Kanban(groupKey: PropertyKey, contentRole: NodeRole) extends Custom {
    override def toString = "Kanban Board"
    def viewKey = "kanban"
  }
  object Kanban {
    def default = Kanban(PropertyKey.stage, NodeRole.Task)
  }
  case object List extends Custom {
    override def toString = "Checklist"
    def viewKey = "list"
  }
  case object Graph extends Custom {
    override def toString = "Tag Diagram for Tasks"
    def viewKey = "graph"
  }
  case object Dashboard extends Custom {
    def viewKey = "dashboard"
  }
  //TODO: rename to Notes
  case object Content extends Custom {
    override def toString = "Notes"
    def viewKey = "notes"
  }
  case object Gantt extends Custom {
    override def toString = "Gantt-Chart"
    def viewKey = "gantt"
  }
  case object Topological extends Custom {
    override def toString = "Topological Sort"
    def viewKey = "topological"
  }
  final case class Tiled(operator: ViewOperator, views: NonEmptyList[Custom]) extends Custom {
    def viewKey = views.map(_.viewKey).toList.mkString(operator.separator)
    override def isContent = views.exists(_.isContent)
  }
  case object Login extends System {
    def viewKey = "login"
    override def isContent = false
  }
  case object Signup extends System {
    def viewKey = "signup"
    override def isContent = false
  }
  case object Welcome extends System {
    def viewKey = "welcome"
    override def isContent = false
  }
  case object Avatar extends System {
    def viewKey = "avatar"
    override def isContent = false
  }
  case object UserSettings extends System {
    def viewKey = "usersettings"
    override def isContent = false
  }
  case object Notifications extends System {
    def viewKey = "notifications"
  }
  case object ActivityStream extends System {
    def viewKey = "activity"
  }
  case object Empty extends System {
    def viewKey = "empty"
  }

  val list: Array[View.System] = SubObjects.all[View.System]
  def map: collection.Map[String, View.System] = list.toList.by(_.viewKey)

  // val selectableList: Array[View] = Array(
  //   View.Dashboard,
  //   View.List,
  //   View.Kanban,
  //   View.Table(NodeRole.Task :: Nil),
  //   View.Graph,
  //   View.Chat,
  //   View.Thread,
  //   View.Content,
  //   View.Files,
  // // View.Gantt,
  // // View.Topological,
  // )

  def forNodeRole(nodeRole: NodeRole): Option[View] = //Some(nodeRole) collect {
    // case NodeRole.Project => View.Dashboard
    // case NodeRole.Message => View.Conversation
    // case NodeRole.Task    => View.Tasks
    // case NodeRole.Note    => View.Content
  // }
  ???
}

sealed trait ViewOperator {
  val separator: String
}
object ViewOperator {
  case object Row extends ViewOperator { override val separator = "|" }
  case object Column extends ViewOperator { override val separator = "/" }
  case object Auto extends ViewOperator { override val separator = "," }
  case object Optional extends ViewOperator { override val separator = "?" }

  val fromString: PartialFunction[String, ViewOperator] = {
    case Row.separator      => Row
    case Column.separator   => Column
    case Auto.separator     => Auto
    case Optional.separator => Optional
  }
}
