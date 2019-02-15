package wust.webApp.views

import outwatch.dom.{VNode, dsl}
import rx._
import wust.ids.{NodeId, View}
import wust.webApp.state.GlobalState
import wust.webApp.views.graphview.GraphView

object ViewRender {
  def apply(view: View.Visible, state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    apply(view, state, state.page.map(_.parentId))
  }
  def apply(view: View.Visible, state: GlobalState, parentId: Rx[Option[NodeId]])(implicit ctx: Ctx.Owner): VNode = view match {
    case View.Files            => FilesView(state, parentId)
    case View.Thread           => ThreadView(state, parentId)
    case View.Chat             => ChatView(state, parentId)
    case View.Kanban           => KanbanView(state, parentId)
    case View.List             => ListView(state, parentId)
    case View.Graph            => GraphView(state)
    case View.Welcome          => WelcomeView(state)
    case View.Login            => AuthView.login(state)
    case View.Signup           => AuthView.signup(state)
    case View.Dashboard        => DashboardView(state, parentId)
    case View.UserSettings     => UserSettingsView(state)
    case View.Empty            => dsl.div
    case View.Tiled(op, views) => TiledView(op, views.map(ViewRender(_, state, parentId)), state)
  }
}
