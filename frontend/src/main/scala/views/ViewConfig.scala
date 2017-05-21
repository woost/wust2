package wust.frontend.views

import org.scalajs.dom._
import rx._
import wust.graph._
import wust.ids._
import scala.util.Try

import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._
import scalatags.rx.all._

sealed trait ViewPage
object ViewPage {
  case object Graph extends ViewPage
  case object Tree extends ViewPage
  case object Chat extends ViewPage

  def default = Graph

  def toString(page: ViewPage) = page.toString.toLowerCase
  val fromString: String => ViewPage = {
    case "graph" => ViewPage.Graph
    case "tree"  => ViewPage.Tree
    case "chat"  => ViewPage.Chat
    case _       => ViewPage.default
  }
}

case class ViewConfig(page: ViewPage, selection: GraphSelection, groupIdOpt: Option[GroupId], invite: Option[String])
object ViewConfig {
  def fromHash(hash: Option[String]): ViewConfig = hash.collect {
    case Path(path) => pathToViewConfig(path)
  }.getOrElse(ViewConfig(ViewPage.default, GraphSelection.default, None, None))

  def toHash(config: ViewConfig): String = viewConfigToPath(config).toString

  private def viewConfigToPath(config: ViewConfig) = {
    val name = ViewPage.toString(config.page)
    val selection = Option(config.selection) collect {
      case GraphSelection.Union(ids) => "select" -> PathOption.IdList.toString(ids.map(_.id).toSeq)
    }
    val group = config.groupIdOpt.map(groupId => "group" -> groupId.id.toString)
    //do not store invite key in url
    val options = Seq(selection, group).flatten.toMap
    Path(name, options)
  }

  private def pathToViewConfig(path: Path) = {
    val page = ViewPage.fromString(path.name)
    val selection = path.options.get("select").map(PathOption.IdList.parse) match {
      case Some(ids) => GraphSelection.Union(ids.map(PostId.apply _).toSet)
      case None      => GraphSelection.default
    }
    val invite = path.options.get("invite")
    val groupId = path.options.get("group").flatMap(str => Try(GroupId(str.toLong)).toOption)

    ViewConfig(page, selection, groupId, invite)
  }
}

class ViewPageRouter(page: Rx[ViewPage])(implicit ctx: Ctx.Owner) {
  def toggleDisplay(f: ViewPage => Boolean): Rx[String] =
    page.map(m => if (f(m)) "block" else "none")

  def showOn(pages: ViewPage*)(elem: TypedTag[Element]) =
    showIf(pages.toSet)(elem)

  def showIf(predicate: ViewPage => Boolean)(elem: TypedTag[Element]) =
    elem(display := toggleDisplay(predicate))

  def map(mappings: Seq[(ViewPage, TypedTag[Element])]) =
    div(mappings.map { case (page, elem) => showOn(page)(elem) }: _*)
}
