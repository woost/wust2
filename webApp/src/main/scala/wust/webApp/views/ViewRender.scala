package wust.webApp.views

// import acyclic.file
import outwatch.dom.{VNode, dsl}
import rx._
import wust.ids.View
import wust.webApp.state.FocusState
import wust.webApp.views.graphview.GraphView

object ViewRender extends ViewRenderLike {
  def apply(focusState: FocusState, view: View.Visible)(implicit ctx: Ctx.Owner): VNode = apply( Some(focusState), view)
  def apply(focusState: Option[FocusState], view: View.Visible)(implicit ctx: Ctx.Owner): VNode = {
    @inline def emptyView = dsl.div
    def withoutFocusState: PartialFunction[View.Visible, VNode] = {
      case View.Login            => AuthView.login
      case View.Signup           => AuthView.signup
      case View.Payment          => PaymentView.render
      case View.UserSettings     => UserSettingsView.apply
      case View.Welcome          => WelcomeView.apply
      case View.Avatar           => AvatarView.apply
      case View.Admin            => AdminView.apply
      case View.Tiled(op, views) => TiledView(op, views.map(ViewRender(focusState, _)))
      case View.Empty            => emptyView
    }
    def withFocusState(focusState: FocusState): PartialFunction[View.Visible, VNode] = withoutFocusState orElse {
      case View.Chat          => ChatView(focusState)
      case View.Thread        => ThreadView(focusState)
      case View.Table(roles)  => TableView(focusState, roles, ViewRender)
      case View.List          => ListView(focusState, autoFocusInsert = true, showNestedInputFields = false)
      case View.ListWithChat  => ListWithChatView(focusState)
      case View.Kanban        => KanbanView(focusState, ViewRender)
      case View.Graph         => GraphView(focusState)
      case View.Dashboard     => DashboardView(focusState)
      case View.Form          => FormView(focusState)
      case View.Files         => FilesView(focusState)
      case View.Content       => NotesView(focusState)
      case View.Gantt         => GanttView(focusState)
      case View.Topological   => TopologicalView(focusState)
      case View.Notifications => NotificationView(focusState)
      case View.ActivityStream => ActivityStream(focusState)
    }

    val renderView: PartialFunction[View.Visible, VNode] = focusState.fold(withoutFocusState)(withFocusState)

    renderView.applyOrElse[View.Visible, VNode](view, _ => emptyView)
  }
}
