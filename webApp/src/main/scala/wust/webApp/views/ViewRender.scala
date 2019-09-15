package wust.webApp.views

import acyclic.file
import outwatch.dom.{VNode, dsl}
import rx._
import wust.ids.View
import wust.webApp.state.{FocusState, GlobalState}
import wust.webApp.views.graphview.GraphView

object ViewRender extends ViewRenderLike {
  def apply(focusState: FocusState, view: View)(implicit ctx: Ctx.Owner): VNode = apply( Some(focusState), view)
  def apply(focusState: Option[FocusState], view: View)(implicit ctx: Ctx.Owner): VNode = {
    @inline def emptyView = dsl.div
    def withoutFocusState: PartialFunction[View, VNode] = {
      case View.Login            => AuthView.login
      case View.Signup           => AuthView.signup
      case View.UserSettings     => UserSettingsView.apply
      case View.Welcome          => WelcomeView.apply
      case View.Avatar           => AvatarView.apply
      case View.Tiled(op, views) => TiledView(op, views.map(ViewRender( focusState, _)))
      case View.Empty            => emptyView
    }
    def withFocusState(focusState: FocusState): PartialFunction[View, VNode] = withoutFocusState orElse {
      case View.Chat          => ChatView(focusState)
      case View.Thread        => ThreadView(focusState)
      case View.Table(roles)  => TableView(focusState, roles, ViewRender)
      case View.List          => ListView(focusState)
      case v: View.Kanban        => KanbanView(focusState, ViewRender, v)
      case View.Graph         => GraphView(focusState)
      case View.Dashboard     => DashboardView(focusState)
      case View.Files         => FilesView(focusState)
      case View.Content       => NotesView(focusState)
      case View.Gantt         => GanttView(focusState)
      case View.Topological   => TopologicalView(focusState)
      case View.Notifications => NotificationView(focusState)
      case View.ActivityStream => ActivityStream(focusState)
    }

    val renderView: PartialFunction[View, VNode] = focusState.fold(withoutFocusState)(withFocusState)

    renderView.applyOrElse[View, VNode](view, _ => emptyView)
  }
}
