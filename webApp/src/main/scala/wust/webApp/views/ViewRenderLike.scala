package wust.webApp.views

import acyclic.file
import outwatch.dom.VNode
import rx.Ctx
import wust.ids.View
import wust.webApp.state.{FocusState, GlobalState}

trait ViewRenderLike {
  def apply(state: GlobalState, focusState: FocusState, view: View.Visible)(implicit ctx: Ctx.Owner): VNode
  def apply(state: GlobalState, focusState: Option[FocusState], view: View.Visible)(implicit ctx: Ctx.Owner): VNode
}