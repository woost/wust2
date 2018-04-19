package wust.webApp.views

import outwatch.dom.VNode
import wust.webApp.GlobalState
import wust.graph._
import wust.ids._
import io.treev.tag._

import scala.util.Try

case class ViewConfig(view: View, page: Page)
object ViewConfig {
  val default = ViewConfig(View.default, Page.empty)
  def fromHash(hash: Option[String]): ViewConfig = hash.collect {
    case Path(path) => pathToViewConfig(path)
  }.getOrElse(default)

  def toHash(config: ViewConfig): String = viewConfigToPath(config).toString

  private def viewConfigToPath(config: ViewConfig) = {
    val name = config.view.key
    val page = Option(config.page) collect {
      case Page(ids, _) => "page" -> PathOption.StringList.toString(ids.toSeq)
    }
    val options = Seq(page).flatten.toMap
    Path(name, options)
  }

  private def pathToViewConfig(path: Path) = {
    val page = View.fromString(path.name)
    val selection = path.options.get("page").map(PathOption.StringList.parse) match {
      case Some(ids) => Page(ids.map(PostId(_)))
      case None      => Page.empty
    }
    ViewConfig(page, selection)
  }
}
