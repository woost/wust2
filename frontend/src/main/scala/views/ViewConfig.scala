package wust.frontend.views

import wust.graph._
import wust.ids._

import scala.util.Try
import scalaz.Tag

sealed trait ViewPage
object ViewPage {
  case object Graph extends ViewPage
  case object List extends ViewPage
  case object Article extends ViewPage
  case object Code extends ViewPage
  case object Chat extends ViewPage
  case object Board extends ViewPage
  case object MyBoard extends ViewPage
  case object Test extends ViewPage

  def default = Graph

  def toString(page: ViewPage) = page.toString.toLowerCase
  val fromString: String => ViewPage = {
    case "myboard"   => ViewPage.MyBoard
    case "graph"   => ViewPage.Graph
    case "list"    => ViewPage.List
    case "article" => ViewPage.Article
    case "code"    => ViewPage.Code
    case "chat"    => ViewPage.Chat
    case "board"    => ViewPage.Board
    case "test"    => ViewPage.Test
    case _         => ViewPage.default
  }
}

case class ViewConfig(page: ViewPage, selection: GraphSelection, groupIdOpt: Option[GroupId], invite: Option[String], lockToGroup: Boolean)
object ViewConfig {
  val default = ViewConfig(ViewPage.default, GraphSelection.default, None, None, false)
  def fromHash(hash: Option[String]): ViewConfig = hash.collect {
    case Path(path) => pathToViewConfig(path)
  }.getOrElse(default)

  def toHash(config: ViewConfig): String = viewConfigToPath(config).toString

  private def viewConfigToPath(config: ViewConfig) = {
    val name = ViewPage.toString(config.page)
    val selection = Option(config.selection) collect {
      case GraphSelection.Union(ids) => "select" -> PathOption.StringList.toString(ids.map(Tag.unwrap _).toSeq)
    }
    val group = config.groupIdOpt.map(groupId => "group" -> Tag.unwrap(groupId).toString)
    //invite is not listed here, because we don't need to see it after joining the group
    val lockToGroup = if (config.lockToGroup) Some("locktogroup" -> "true") else None
    val options = Seq(selection, group, lockToGroup).flatten.toMap
    Path(name, options)
  }

  private def pathToViewConfig(path: Path) = {
    val page = ViewPage.fromString(path.name)
    val selection = path.options.get("select").map(PathOption.StringList.parse) match {
      case Some(ids) => GraphSelection.Union(ids.map(PostId _).toSet)
      case None      => GraphSelection.default
    }
    val invite = path.options.get("invite")
    val groupId = path.options.get("group").flatMap(str => Try(GroupId(str.toLong)).toOption)
    val lockToGroup = path.options.get("locktogroup").map(PathOption.Flag.parse).getOrElse(false)

    ViewConfig(page, selection, groupId, invite, lockToGroup)
  }
}
