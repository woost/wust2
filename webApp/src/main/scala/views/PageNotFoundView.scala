package wust.webApp.views

import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.css.Styles
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState

object PageNotFoundView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      justifyContent.spaceAround,
      flexDirection.column,
      alignItems.center,
      h1(
        cls := "pagenotfound",
        padding := "20px",
        marginBottom := "10%",
        "We're sorry. The page you're looking for does not exist or you don't have sufficient permissions."
      )
    )
  }
}
