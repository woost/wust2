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

  private def applyView(state: GlobalState)(ctx: Ctx.Owner)(view: View) = {
    view.apply(state)(ctx)(height := "100%", width := "100%")
  }

  //TODO: inline styles from viewgrid* css classes. better support in scala-dom-types for viewgrid?
  //TODO: outwach: Observable[Seq[VDomModifier]] should work, otherwise cannot share code proberly...muliple div.
  //TOOD: outwatch: make constructor for CompositeModifier public, otherwise need implicit
  override final def apply(state: GlobalState)(implicit ctx: Ctx.Owner) =
     operator match {
      case ViewOperator.Row => div(
        cls := "viewgridRow",
        views.map(applyView(state)(ctx)).toList)
      case ViewOperator.Column => div(
        cls := "viewgridColumn",
        views.map(applyView(state)(ctx)).toList)
      case ViewOperator.Auto => div(
        cls := "viewgridAuto",
        views.map(applyView(state)(ctx)).toList)
      case ViewOperator.Optional => div(
        cls := "viewgridAuto",
        events.window.onResize
          .map(_ => ()).startWith(Seq(()))
          .map { _ =>
            //TODO: min-width corresponds media query in style.css
            if (dom.window.matchMedia("only screen and (min-width : 992px)").matches)
              views.map(applyView(state)(ctx)).toList
            else
              applyView(state)(ctx)(views.head) :: Nil
          }
      )
    }
}
