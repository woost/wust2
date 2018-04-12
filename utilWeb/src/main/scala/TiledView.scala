package wust.utilWeb.views

import outwatch.dom._
import outwatch.dom.dsl._
import wust.utilWeb._
import rx._

trait TiledView extends View {
  protected val views: List[View]

  override final def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    id := "viewgrid",
    views.map(_.apply(state))
  )
}
