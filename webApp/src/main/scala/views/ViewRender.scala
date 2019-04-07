package wust.webApp.views

import outwatch.dom.{VNode, dsl}
import rx._
import wust.webApp.state.FocusState
import wust.ids.{NodeId, View}
import wust.webApp.state.GlobalState
import wust.webApp.views.graphview.GraphView

object ViewRender {

  def apply(state: GlobalState, focusState: FocusState, view: View.Visible)(implicit ctx: Ctx.Owner): VNode = apply(state, Some(focusState), view)
  def apply(state: GlobalState, focusState: Option[FocusState], view: View.Visible)(implicit ctx: Ctx.Owner): VNode = {
    def emptyView = dsl.div
    def withoutFocusState: PartialFunction[View.Visible, VNode] = {
      case View.Login            => AuthView.login(state)
      case View.Signup           => AuthView.signup(state)
      case View.UserSettings     => UserSettingsView(state)
      case View.Welcome          => WelcomeView(state)
      case View.Avatar           => AvatarView(state)
      case View.Tiled(op, views) => TiledView(op, views.map(ViewRender(state, focusState, _)), state)
      case View.Empty            => emptyView
    }
    def withFocusState(focusState: FocusState): PartialFunction[View.Visible, VNode] = withoutFocusState orElse {
      case View.Chat             => ChatView(state, focusState)
      case View.Thread           => ThreadView(state, focusState)
      case View.Table(roles)     => TableView(state, focusState, roles)
      case View.List             => ListView(state, focusState)
      case View.Kanban           => KanbanView(state, focusState)
      case View.Graph            => GraphView(state, focusState)
      case View.Dashboard        => DashboardView(state, focusState)
      case View.Files            => FilesView(state, focusState)
      case View.Content          => NotesView(state, focusState)
      case View.Gantt            => GanttView(state, focusState)
      case View.Topological      => TopologicalView(state, focusState)
    }

    val renderView: PartialFunction[View.Visible, VNode] = focusState.fold(withoutFocusState)(withFocusState)

    renderView.applyOrElse[View.Visible, VNode](view, _ => emptyView)
  }
}
