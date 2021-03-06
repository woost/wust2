package wust.ids

import cats.data.NonEmptyList
import wust.util.collection.BasicMap
import wust.util.macros.SubObjects

import scala.collection.breakOut

// BE AWARE: Whenever you rename/change/delete a View.Visible from here, you have to write a DB MIGRATION
// to update the json views in the node table.

sealed trait View {
  def viewKey: String
  def isContent: Boolean = true
}
object View {
  sealed trait Visible extends View
  final case class Table(roles: List[NodeRole]) extends Visible {
    def viewKey = s"table${roles.map(_.toString.toLowerCase).mkString(":", ":", "")}"
    // override def toString = s"Table(${roles.mkString(",")})"
    override def toString = "Table with Task Details"
  }
  case object Thread extends Visible {
    override def toString = "Chat with Threads"
    def viewKey = "thread"
  }
  case object Chat extends Visible {
    def viewKey = "chat"
  }
  case object Files extends Visible {
    def viewKey = "files"
  }
  case object Form extends Visible {
    def viewKey = "form"
  }
  case object Kanban extends Visible {
    override def toString = "Kanban Board"
    def viewKey = "kanban"
  }
  case object List extends Visible {
    override def toString = "Checklist"
    def viewKey = "list"
  }
  case object ListWithChat extends Visible {
    override def toString = "Checklist with Chat (side by side)"
    def viewKey = "listwithchat"
  }
  case object Graph extends Visible {
    override def toString = "Tag Diagram for Tasks"
    def viewKey = "graph"
  }
  case object Dashboard extends Visible {
    def viewKey = "dashboard"
  }
  case object Payment extends Visible {
    def viewKey = "plans"
    override def isContent = false
  }
  case object Login extends Visible {
    def viewKey = "login"
    override def isContent = false
  }
  case object Signup extends Visible {
    def viewKey = "signup"
    override def isContent = false
  }
  case object Welcome extends Visible {
    def viewKey = "welcome"
    override def isContent = false
  }
  case object Avatar extends Visible {
    def viewKey = "avatar"
    override def isContent = false
  }
  case object UserSettings extends Visible {
    def viewKey = "usersettings"
    override def isContent = false
  }
  //TODO: rename to Notes
  case object Content extends Visible {
    override def toString = "Notes"
    def viewKey = "notes"
    override def isContent = true
  }
  case object Gantt extends Visible {
    override def toString = "Gantt-Chart"
    def viewKey = "gantt"
    override def isContent = true
  }
  case object Calendar extends Visible {
    override def toString = "Calendar"
    def viewKey = "calendar"
    override def isContent = true
  }
  case object Topological extends Visible {
    override def toString = "Topological Sort"
    def viewKey = "topological"
    override def isContent = true
  }
  case object Notifications extends Visible {
    def viewKey = "notifications"
    override def isContent = true
  }
  case object ActivityStream extends Visible {
    def viewKey = "activity"
    override def isContent = true
  }
  case object Empty extends Visible {
    def viewKey = "empty"
    override def isContent = true
  }
  final case class Tiled(operator: ViewOperator, views: NonEmptyList[Visible]) extends Visible {
    def viewKey = views.map(_.viewKey).toList.mkString(operator.separator)
    override def isContent = views.exists(_.isContent)
  }

  sealed trait Heuristic extends View
  case object Conversation extends Heuristic {
    def viewKey = "conversation"
  }
  case object Tasks extends Heuristic {
    def viewKey = "tasks"
  }
  case object Admin extends Visible {
    def viewKey = "admin"
    override def isContent = false
  }

  val list: Array[View] = SubObjects.all[View]
  val contentList: Array[View] = list.filter(_.isContent)

  val selectableList: Array[View.Visible] = Array(
    View.List,
    View.Kanban,
    View.Chat,
    View.Content,
    View.ListWithChat,
    View.Table(NodeRole.Task :: Nil),
    // View.Graph,
    View.Thread,
    View.Dashboard,
    View.Files,
    View.Form,
    View.Calendar,
  // View.Gantt,
  // View.Topological,
  )

  val selectableNewProjectList: Array[View.Visible] = Array(
    // View.Dashboard,
    View.List,
    View.Kanban,
    View.Chat,
    View.Content,
    View.ListWithChat,
    // View.Table(NodeRole.Task :: Nil),
    // View.Graph,
    // View.Thread,
    // View.Files,
    // View.Form,
    // View.Calendar,
  // View.Gantt,
  // View.Topological,
  )


  val map: BasicMap[String, List[String] => Option[View]] = {
    val map = BasicMap.ofString[List[String] => Option[View]]()
    list.foreach { v =>
      map += v.viewKey -> ((_: List[String]) => Some(v))
    }

    map += "table" -> { params => Some(Table(params.flatMap(s => NodeRole.fromString(s))(breakOut))) }
    //TODO viewops for TiledView should be done here too. currently hardcoded in UrlParsing

    map
  }

  def forNodeRole(nodeRole: NodeRole): Option[View] = Some(nodeRole) collect {
    case NodeRole.Project => View.Dashboard
    case NodeRole.Message => View.Conversation
    case NodeRole.Task    => View.Tasks
    case NodeRole.Note    => View.Content
  }
}

sealed trait ViewOperator {
  val separator: String
}
object ViewOperator {
  case object Row extends ViewOperator { override val separator = "+" }
  case object Column extends ViewOperator { override val separator = "/" }
  case object Auto extends ViewOperator { override val separator = "," }
  case object Optional extends ViewOperator { override val separator = "?" }

  val fromString: PartialFunction[String, ViewOperator] = {
    case Row.separator      => Row
    case "|"                => Row // old representation
    case Column.separator   => Column
    case Auto.separator     => Auto
    case Optional.separator => Optional
  }
}
