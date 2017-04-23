package wust.frontend.views

import java.net.URI

import org.scalajs.dom._
import rx._
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
}

object Path {
  //TODO: parsing crashes on: "http://localhost:12345/workbench/index.html#graph?"
  def unapply(str: String): Option[ViewConfig] = {
    println("unapply! " + str)
    Try(URI.create(str)) match {
      case Success(uri) => parsePage.lift(uri.getPath).map { page =>
        val map = Option(uri.getQuery).map(queryToMap _).getOrElse(Map.empty)
        ViewConfig(page, mapToSelection(map))
      }
      case Failure(_) => None
    }
  }

  def apply(config: ViewConfig): String = {
    val path = config.page.toString.toLowerCase
    val selection = selectionToMap(config.selection)
    val query = mapToQuery(selection)
    if (query.isEmpty) path
    else path + "?" + query
  }

  private val parsePage: PartialFunction[String, ViewPage] = {
    case "graph" => ViewPage.Graph
    case "tree" => ViewPage.Tree
    case "user" => ViewPage.User
  }

  private def queryToMap(query: String): Map[String, String] =
    query.split("&").map { parts =>
      val Array(key, value) = parts.split("=")
      key -> value
    }.toMap

  private def mapToQuery(query: Map[String, Any]): String =
    query.map { case (k, v) => s"$k=$v" }.mkString("&")

  private def mapToSelection(map: Map[String, String]): GraphSelection =
    map.get("select").flatMap { ids =>
      Try(ids.split(",").map(id => PostId(id.toLong))(breakOut): Set[PostId]).toOption
    }.map(GraphSelection.Union.apply) getOrElse GraphSelection.Root

  private val selectionToMap: GraphSelection => Map[String, String] = {
    case GraphSelection.Root => Map.empty
    case GraphSelection.Union(ids) => Map("select" -> ids.map(_.id).mkString(","))
  }
}

case class ViewConfig(page: ViewPage, selection: GraphSelection)
object ViewConfig {
  val fromHash: Option[String] => ViewConfig = {
    case Some(Path(config)) => config
    case _ => ViewConfig(ViewPage.Graph, GraphSelection.Root)
  }

  def toHash: ViewConfig => String = Path.apply _
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
