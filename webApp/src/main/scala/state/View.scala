package wust.webApp.state

import cats.data.NonEmptyList
import wust.util.macros.SubObjects

import scala.collection.breakOut

sealed trait View {
  def viewKey: String
  def isContent: Boolean = true
}
object View {
  case object Detail extends View {
    def viewKey = "detail"
  }
  case object Magic extends View {
    def viewKey = "magic"
  }
  case object Split extends View {
    def viewKey = "split"
  }
  case object Conversation extends View {
    def viewKey = "conversation"
  }
  case object Tasks extends View {
    def viewKey = "tasks"
  }
  case object Thread extends View {
    def viewKey = "thread"
  }
  case object Chat extends View {
    def viewKey = "chat"
  }
  case object Files extends View {
    def viewKey = "files"
  }
  case object Kanban extends View {
    def viewKey = "kanban"
  }
  case object List extends View {
    def viewKey = "list"
  }
  case object Property extends View {
    def viewKey = "property"
  }
  case object Graph extends View {
    def viewKey = "graph"
  }
  case object Dashboard extends View {
    def viewKey = "dashboard"
  }
  case object Login extends View {
    def viewKey = "login"
    override def isContent = false
  }
  case object Signup extends View {
    def viewKey = "signup"
    override def isContent = false
  }
  case object Welcome extends View {
    def viewKey = "welcome"
    override def isContent = false
  }
  case object UserSettings extends View {
    def viewKey = "usersettings"
    override def isContent = false
  }
  case class Tiled(operator: ViewOperator, views: NonEmptyList[View]) extends View {
    def viewKey = views.map(_.viewKey).toList.mkString(operator.separator)
    override def isContent = views.exists(_.isContent)
  }

  def list: List[View] = macro SubObjects.list[View]
  def contentList: List[View] = list.filter(_.isContent)

  val map: Map[String, View] = list.map(v => v.viewKey -> v)(breakOut)
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
