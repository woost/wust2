package wust.webApp.views

import cats.data.NonEmptyList
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize, ViewOperator}

object TiledView {
  //TODO: inline styles from viewgrid* css classes. better support in scala-dom-types for viewgrid?
  def apply(operator: ViewOperator, views: NonEmptyList[VNode], state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val appliedViews = views.map(_(height := "100%", width := "100%"))

    operator match {
      case ViewOperator.Row    => div(cls := "viewgridRow", appliedViews.toList)
      case ViewOperator.Column => div(cls := "viewgridColumn", appliedViews.toList)
      case ViewOperator.Auto   => div(cls := "viewgridAuto", appliedViews.toList)
      case ViewOperator.Optional =>
        div(
          cls := "viewgridAuto",
          state.screenSize.map {
            case ScreenSize.Large => appliedViews.toList
            case _  => appliedViews.head :: Nil
          }
        )
    }
  }
}
