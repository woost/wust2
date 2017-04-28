package wust.frontend.views

import org.scalajs.dom._
import rx._
import wust.ids._
import wust.graph._

import scala.collection.breakOut
import scala.util.{Failure, Success, Try}
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._
import scalatags.rx.all._

sealed trait ViewPage
object ViewPage {
  case object Graph extends ViewPage
  case object Tree extends ViewPage
  case object User extends ViewPage

  def default = Graph
}

case class ViewConfig(page: ViewPage, selection: GraphSelection, invite: Option[String])
object ViewConfig {
  def fromHash(hash: Option[String]): ViewConfig = hash.collect {
    case Path(path) => pathToViewConfig(path)
  }.getOrElse(ViewConfig(ViewPage.default, GraphSelection.default, None))

  def toHash(config: ViewConfig): String = viewConfigToPath(config).toString

  private def viewConfigToPath(config: ViewConfig) = {
    val name = config.page.toString.toLowerCase
    val selection = Option(config.selection) collect {
      case GraphSelection.Union(ids) => "select" -> PathOption.IdList.toString(ids.map(_.id).toSeq)
    }
    //do not store invite key in url
    val options = Seq(selection).flatten.toMap
    Path(name, options)
  }

  private def pathToViewConfig(path: Path) = {
    val page = path.name match {
      case "graph" => ViewPage.Graph
      case "tree" => ViewPage.Tree
      case "user" => ViewPage.User
      case _ => ViewPage.default
    }
    val selection = path.options.get("select").map(PathOption.IdList.parse) match {
      case Some(ids) => GraphSelection.Union(ids.map(PostId.apply _).toSet)
      case None => GraphSelection.default
    }
    val invite = path.options.get("invite")

    ViewConfig(page, selection, invite)
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
