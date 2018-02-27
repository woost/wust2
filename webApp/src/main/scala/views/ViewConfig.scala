package wust.webApp.views

import outwatch.dom.VNode
import wust.webApp.GlobalState
import wust.webApp.views.graphview.GraphView
import wust.graph._
import wust.ids._
import io.treev.tag._

import scala.util.Try

case class ViewConfig(view: View, page: Page, groupIdOpt: Option[GroupId], invite: Option[String], lockToGroup: Boolean)
object ViewConfig {
  val default = ViewConfig(View.default, Page.default, None, None, lockToGroup = false)
  def fromHash(hash: Option[String]): ViewConfig = hash.collect {
    case Path(path) => pathToViewConfig(path)
  }.getOrElse(default)

  def toHash(config: ViewConfig): String = viewConfigToPath(config).toString

  private def viewConfigToPath(config: ViewConfig) = {
    val name = config.view.key
    val selection = Option(config.page) collect {
      case Page.Union(ids) => "select" -> PathOption.StringList.toString(ids.toSeq)
    }
    val group = config.groupIdOpt.map(groupId => "group" -> (groupId: GroupId.Raw).toString)
    //invite is not listed here, because we don't need to see it after joining the group
    val lockToGroup = if (config.lockToGroup) Some("locktogroup" -> "true") else None
    val options = Seq(selection, group, lockToGroup).flatten.toMap
    Path(name, options)
  }

  private def pathToViewConfig(path: Path) = {
    val page = View.fromString(path.name)
    val selection = path.options.get("select").map(PathOption.StringList.parse) match {
      case Some(ids) => Page.Union(ids.map(PostId(_)).toSet)
      case None      => Page.default
    }
    val invite = path.options.get("invite")
    val groupId = path.options.get("group").flatMap(str => Try(GroupId(str.toLong)).toOption)
    val lockToGroup = path.options.get("locktogroup").exists(PathOption.Flag.parse)

    ViewConfig(page, selection, groupId, invite, lockToGroup)
  }
}
