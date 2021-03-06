package wust.webApp.views

import cats.data.NonEmptyList
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import rx._
import wust.ids.ViewOperator
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webUtil.outwatchHelpers._

object TiledView {
  //TODO: inline styles from viewgrid* css classes. better support in scala-dom-types for viewgrid?
  def apply(operator: ViewOperator, views: NonEmptyList[VNode])(implicit ctx: Ctx.Owner) = {
    val appliedViews = views.map(_(height := "100%", width := "100%"))

    operator match {
      case ViewOperator.Row    => div(cls := "viewgridRow", appliedViews.toList)
      case ViewOperator.Column => div(cls := "viewgridColumn", appliedViews.toList)
      case ViewOperator.Auto   => div(cls := "viewgridAuto", appliedViews.toList)
      case ViewOperator.Optional =>
        div(
          cls := "viewgridAuto",
          GlobalState.screenSize.map {
            case ScreenSize.Large => appliedViews.toList
            case _  => appliedViews.head :: Nil
          }
        )
    }
  }
}
