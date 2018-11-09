package wust.webApp.views

import outwatch.dom.VNode
import rx._
import wust.webApp.state.{GlobalState, View}
import wust.webApp.views.graphview.GraphView

object ViewRender {
  def apply(view: View, state: GlobalState)(implicit ctx: Ctx.Owner): VNode = view match {
    case View.Magic           => MagicView(state)
    case View.Thread           => ThreadView(state)
    case View.Workflow         => workflow.WorkflowView(state)
    case View.Chat             => ChatView(state)
    case View.Kanban           => KanbanView(state)
    case View.Graph            => GraphView(state)
    case View.Login            => AuthView.login(state)
    case View.Signup           => AuthView.signup(state)
    case View.NewChannel       => NewChannelView(state)
    case View.UserSettings     => UserSettingsView(state)
    case View.Error(msg)       => ErrorView(msg, state)
    case View.Tiled(op, views) => TiledView(op, views.map(ViewRender(_, state)), state)
  }
}
