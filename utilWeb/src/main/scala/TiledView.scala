package wust.utilWeb.views

import outwatch.dom._
import outwatch.dom.dsl._
import wust.utilWeb._
import rx._

class TiledView(val views: List[View]) extends View {
  override val key = views.map(_.key).mkString(TiledView.separator.toString)
  override val displayName = views.map(_.displayName).mkString(TiledView.separator.toString)

  override final def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    id := "viewgrid",
    views.map(_.apply(state))
  )
}
object TiledView {
  val separator:Char = '|' // needs to be a char, because else strings will be interpreted as regexes
}
