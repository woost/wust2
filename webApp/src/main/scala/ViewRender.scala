package wust.webApp

import outwatch.dom.VNode
import cats.Eval

import collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import rx._
import wust.webApp.views._
import wust.webApp.views.graphview.GraphView
import wust.graph.Page

object ViewRender {
  def apply(view: View, state: GlobalState)(implicit ctx: Ctx.Owner): VNode = view match {
    case View.Chat => ChatView(state)
    case View.Kanban => KanbanView(state)
    case View.Graph => GraphView(state)
    case View.Login => LoginView(state)
    case View.Signup => SignupView(state)
    case View.NewChannel => NewChannelView(state)
    case View.UserSettings => UserSettingsView(state)
    case View.Error(msg) => ErrorView(msg, state)
    case View.Tiled(op, views) => TiledView(op, views.map(ViewRender(_, state)), state)
  }
}
