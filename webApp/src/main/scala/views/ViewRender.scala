package wust.webApp.views

import outwatch.dom.VNode
import rx._
import views.SplitView
import wust.webApp.state.{GlobalState, View}
import wust.webApp.views.graphview.GraphView

object ViewRender {
  def apply(view: View, state: GlobalState)(implicit ctx: Ctx.Owner): VNode = view match {
    case View.Magic            => MagicView(state)
    case View.Conversation     => ConversationView(state)
    case View.Split            => SplitView(state)
    case View.Tasks            => TasksView(state)
    case View.Thread           => ThreadView(state)
    case View.Chat             => ChatView(state)
    case View.Kanban           => KanbanView(state)
    case View.List             => ListView(state)
    case View.Property         => PropertyView(state)
    case View.Graph            => GraphView(state)
    case View.Login            => AuthView.login(state)
    case View.Signup           => AuthView.signup(state)
    case View.Welcome          => NewChannelView(state)
    case View.UserSettings     => UserSettingsView(state)
    case View.Tiled(op, views) => TiledView(op, views.map(ViewRender(_, state)), state)
  }
}
