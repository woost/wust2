package wust.webApp.views

import wust.webApp.outwatchHelpers._
import cats.data.NonEmptyList
import cats.Eval
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import rx._

class TiledView(val operator: ViewOperator, views: NonEmptyList[View]) extends View {
  override val key = views.map(_.key).toList.mkString(operator.separator.toString)
  override val displayName = views.map(_.displayName).toList.mkString(operator.separator.toString)
  override def innerViews: Seq[View] = views.toList.flatMap(_.innerViews)

  override def toString = s"TiledView($operator, ${views.map(_.toString)})"

  //TODO: inline styles from viewgrid* css classes. better support in scala-dom-types for viewgrid?
  //TODO: outwach: Observable[Seq[VDomModifier]] should work, otherwise cannot share code proberly...muliple div.
  override final def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val appliedViews = views.map { view =>
      Eval.later(view.apply(state)(ctx)(height := "100%", width := "100%"))
    }.toList

    operator match {
      case ViewOperator.Row    => div(cls := "viewgridRow", appliedViews.map(_.value))
      case ViewOperator.Column => div(cls := "viewgridColumn", appliedViews.map(_.value))
      case ViewOperator.Auto   => div(cls := "viewgridAuto", appliedViews.map(_.value))
      case ViewOperator.Optional =>
        div(
          cls := "viewgridAuto",
          state.screenSize.map {
            case ScreenSize.Desktop => appliedViews.map(_.value)
            case ScreenSize.Mobile  => appliedViews.head.value :: Nil
          }
        )
    }
  }
}
