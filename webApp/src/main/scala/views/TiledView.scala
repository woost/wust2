package wust.webApp.views

import cats.data.NonEmptyList
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import rx._

class TiledView(val operator: ViewOperator, views: NonEmptyList[View]) extends View {
  override val key = views.map(_.key).toList.mkString(operator.separator.toString)
  override val displayName = views.map(_.displayName).toList.mkString(operator.separator.toString)

  override final def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    cls := (operator match {
      case ViewOperator.Row => "viewgridRow"
      case ViewOperator.Column => "viewgridColumn"
      case ViewOperator.Auto => "viewgridAuto"
    }),
    views.map(_.apply(state)).toList
  )
}
