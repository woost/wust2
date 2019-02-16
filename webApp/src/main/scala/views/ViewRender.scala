package wust.webApp.views

import outwatch.dom.{VNode, dsl}
import rx._
import wust.webApp.state.FocusState
import wust.ids.{NodeId, View}
import wust.webApp.state.GlobalState
import wust.webApp.views.graphview.GraphView

object ViewRender {
  def apply(state: GlobalState, focusState: FocusState, view: View.Visible)(implicit ctx: Ctx.Owner): VNode = view match {
    case View.Chat             => ChatView(state, focusState)
    case View.Thread           => ThreadView(state, focusState)
    case View.List             => ListView(state, focusState)
    case View.Kanban           => KanbanView(state, focusState)
    case View.Graph            => GraphView(state, focusState)
    case View.Dashboard        => DashboardView(state, focusState)
    case View.Welcome          => WelcomeView(state)
    case View.Files            => FilesView(state, focusState)
    case View.Login            => AuthView.login(state)
    case View.Signup           => AuthView.signup(state)
    case View.UserSettings     => UserSettingsView(state)
    case View.Empty            => dsl.div
    case View.Tiled(op, views) => TiledView(op, views.map(ViewRender(state, focusState, _)), state)
  }
}
