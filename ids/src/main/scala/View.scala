package wust.ids

import cats.data.NonEmptyList
import wust.util.macros.SubObjects

import scala.collection.breakOut

sealed trait View {
  def viewKey: String
  def isContent: Boolean = true
}
object View {
  sealed trait Visible extends View
  case class Table(roles: List[NodeRole]) extends Visible {
    def viewKey = s"table${roles.map(_.toString.toLowerCase).mkString(":", ":", "")}"
    // override def toString = s"Table(${roles.mkString(",")})"
    override def toString = "Table"
  }
  case object Thread extends Visible {
    def viewKey = "thread"
  }
  case object Chat extends Visible {
    def viewKey = "chat"
  }
  case object Files extends Visible {
    def viewKey = "files"
  }
  case object Kanban extends Visible {
    def viewKey = "kanban"
  }
  case object List extends Visible {
    def viewKey = "list"
  }
  case object Graph extends Visible {
    def viewKey = "graph"
  }
  case object Dashboard extends Visible {
    def viewKey = "dashboard"
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
  case object Content extends Visible {
    def viewKey = "content"
    override def isContent = true
  }
  case object Empty extends Visible {
    def viewKey = "empty"
    override def isContent = true
  }
  case class Tiled(operator: ViewOperator, views: NonEmptyList[Visible]) extends Visible {
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

  def list: List[View] = macro SubObjects.list[View]
  def contentList: List[View] = list.filter(_.isContent)

  val map: Map[String, List[String] => Option[View]] = {

    val staticMap: Map[String, List[String] => Option[View]] = list.map(v => v.viewKey -> ((_: List[String]) => Some(v)))(breakOut)
    val parameterMap: Map[String, List[String] => Option[View]] = Map(
      "table" -> { params => Some(Table(params.flatMap(s => NodeRole.fromString(s))(breakOut))) }
      //TODO viewops for TiledView should be done here too. currently hardcoded in UrlParsing
    )

    staticMap ++ parameterMap
  }
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
