package wust.webApp.views

import acyclic.file
import outwatch.dom.VNode
import rx.Ctx
import wust.ids.View
import wust.webApp.state.{FocusState, GlobalState}

trait ViewRenderLike {
  def apply(focusState: FocusState, view: View)(implicit ctx: Ctx.Owner): VNode
  def apply(focusState: Option[FocusState], view: View)(implicit ctx: Ctx.Owner): VNode
}
