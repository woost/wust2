package wust.webApp.views

import acyclic.skipped // file is allowed in dependency cycle
import outwatch.dom.VNode

import collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import rx._
import wust.webApp.views.graphview.GraphView
import wust.graph.Page

object ViewList {
  val list: List[View] =
    ChatView ::
      KanbanView ::
      GraphView ::
      LoginView ::
      SignupView ::
      NewGroupView ::
      UserSettingsView ::
      // AvatarView ::
      Nil

  val contentList: List[View] = list.filter(_.isContent)

  val viewMap: Map[String, View] = list.map(v => v.viewKey -> v)(breakOut)
  def default = ChatView // new TiledView(ViewOperator.Optional, contentList)

  val defaultViewConfig = ViewConfig(default, Page.empty, None, None)
}
