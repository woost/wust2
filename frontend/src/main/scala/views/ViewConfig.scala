package wust.frontend.views

import wust.graph._
import wust.ids._

import scala.util.Try
import scalaz.Tag

sealed trait View
object View {
  case object Graph extends View
  case object List extends View
  case object Article extends View
  case object Code extends View
  case object Chat extends View
  case object Board extends View
  case object MyBoard extends View
  case object Test extends View

  def default = Graph

  def toString(page: View) = page.toString.toLowerCase
  val fromString: String => View = {
    case "myboard"   => View.MyBoard
    case "graph"   => View.Graph
    case "list"    => View.List
    case "article" => View.Article
    case "code"    => View.Code
    case "chat"    => View.Chat
    case "board"    => View.Board
    case "test"    => View.Test
    case _         => View.default
  }
}

case class ViewConfig(view: View, page: Page, groupIdOpt: Option[GroupId], invite: Option[String], lockToGroup: Boolean)
object ViewConfig {
  val default = ViewConfig(View.default, Page.default, None, None, false)
  def fromHash(hash: Option[String]): ViewConfig = hash.collect {
    case Path(path) => pathToViewConfig(path)
  }.getOrElse(default)

  def toHash(config: ViewConfig): String = viewConfigToPath(config).toString

  private def viewConfigToPath(config: ViewConfig) = {
    val name = View.toString(config.view)
    val selection = Option(config.page) collect {
      case Page.Union(ids) => "select" -> PathOption.StringList.toString(ids.map(Tag.unwrap _).toSeq)
    }
    val group = config.groupIdOpt.map(groupId => "group" -> Tag.unwrap(groupId).toString)
    //invite is not listed here, because we don't need to see it after joining the group
    val lockToGroup = if (config.lockToGroup) Some("locktogroup" -> "true") else None
    val options = Seq(selection, group, lockToGroup).flatten.toMap
    Path(name, options)
  }

  private def pathToViewConfig(path: Path) = {
    val page = View.fromString(path.name)
    val selection = path.options.get("select").map(PathOption.StringList.parse) match {
      case Some(ids) => Page.Union(ids.map(PostId _).toSet)
      case None      => Page.default
    }
    val invite = path.options.get("invite")
    val groupId = path.options.get("group").flatMap(str => Try(GroupId(str.toLong)).toOption)
    val lockToGroup = path.options.get("locktogroup").map(PathOption.Flag.parse).getOrElse(false)

    ViewConfig(page, selection, groupId, invite, lockToGroup)
  }
}
