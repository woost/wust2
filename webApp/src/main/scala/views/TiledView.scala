package wust.webApp.views

import cats.data.NonEmptyList
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import rx._
import org.scalajs.dom

class TiledView(val operator: ViewOperator, views: NonEmptyList[View]) extends View {
  override val key = views.map(_.key).toList.mkString(operator.separator.toString)
  override val displayName = views.map(_.displayName).toList.mkString(operator.separator.toString)
  override def innerViews: Seq[View] = views.toList.flatMap(_.innerViews)

  override def toString = s"TiledView($operator, ${views.map(_.toString)})"

  override final def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
     operator match {
      case ViewOperator.Row => div(
        cls := "viewgridRow",
        views.map(_.apply(state)).toList)
      case ViewOperator.Column => div(
        cls := "viewgridColumn",
        views.map(_.apply(state)).toList)
      case ViewOperator.Auto => div(
        cls := "viewgridAuto",
        views.map(_.apply(state)).toList)
      case ViewOperator.Optional => div(
        cls := "viewgridAuto",
        events.window.onResize
          .map(_ => ())
          .startWith(Seq(()))
          .map { _ =>
            //TODO: min-width corresponds media query in style.css
            if (dom.window.matchMedia("only screen and (min-width : 992px)").matches)
              views.map(_.apply(state)).toList
            else
              views.head.apply(state) :: Nil
          }
      )
    })
}
