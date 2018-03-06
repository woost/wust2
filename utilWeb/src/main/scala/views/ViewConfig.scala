package wust.utilWeb.views

import outwatch.dom.VNode
import wust.utilWeb.GlobalState
import wust.graph._
import wust.ids._
import io.treev.tag._

import scala.util.Try

case class ViewConfig(view: View, page: Page)
object ViewConfig {
  val default = ViewConfig(View.default, Page.default)
  def fromHash(hash: Option[String]): ViewConfig = hash.collect {
    case Path(path) => pathToViewConfig(path)
  }.getOrElse(default)

  def toHash(config: ViewConfig): String = viewConfigToPath(config).toString

  private def viewConfigToPath(config: ViewConfig) = {
    val name = config.view.key
    val page = Option(config.page) collect {
      case Page.Union(ids) => "page" -> PathOption.StringList.toString(ids.toSeq)
    }
    val options = Seq(page).flatten.toMap
    Path(name, options)
  }

  private def pathToViewConfig(path: Path) = {
    val page = View.fromString(path.name)
    val selection = path.options.get("page").map(PathOption.StringList.parse) match {
      case Some(ids) => Page.Union(ids.map(PostId(_)).toSet)
      case None      => Page.default
    }
    ViewConfig(page, selection)
  }
}
