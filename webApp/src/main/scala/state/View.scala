package wust.webApp.state

import cats.data.NonEmptyList
import wust.util.macros.SubObjects

import scala.collection.breakOut

sealed trait View {
  def viewKey: String
  def isContent: Boolean
}
object View {
  case object Magic extends View {
    def viewKey = "magic"
    def isContent = true
  }
  case object Conversation extends View {
    def viewKey = "conversation"
    def isContent = true
  }
  case object Thread extends View {
    def viewKey = "thread"
    def isContent = true
  }
  case object Chat extends View {
    def viewKey = "chat"
    def isContent = true
  }
  case object Kanban extends View {
    def viewKey = "kanban"
    def isContent = true
  }
  case object ListV extends View {
    def viewKey = "list"
    def isContent = true
  }
  case object Graph extends View {
    def viewKey = "graph"
    def isContent = true
  }
  case object Login extends View {
    def viewKey = "login"
    def isContent = false
  }
  case object Signup extends View {
    def viewKey = "signup"
    def isContent = false
  }
  case object NewChannel extends View {
    def viewKey = "newchannel"
    def isContent = false
  }
  case object UserSettings extends View {
    def viewKey = "usersettings"
    def isContent = false
  }
  case class Error(msg: String) extends View {
    def viewKey = "error"
    def isContent = false
  }
  case class Tiled(operator: ViewOperator, views: NonEmptyList[View]) extends View {
    def viewKey = views.map(_.viewKey).toList.mkString(operator.separator)
    def isContent = views.exists(_.isContent)
  }

  def list: List[View] = macro SubObjects.list[View]
  def contentList: List[View] = list.filter(_.isContent)

  val map: Map[String, View] = list.map(v => v.viewKey -> v)(breakOut)

  def default: View = Conversation
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
