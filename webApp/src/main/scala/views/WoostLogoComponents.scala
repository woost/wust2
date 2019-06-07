package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import webUtil.outwatchHelpers._
import wust.sdk.Colors

object WoostLogoComponents {
  private val woostPathCurve = "m51.843 221.96c81.204 0-6.6913-63.86 18.402 13.37 25.093 77.23 58.666-26.098-7.029 21.633-65.695 47.73 42.949 47.73-22.746 0-65.695-47.731-32.122 55.597-7.029-21.633 25.093-77.23-62.802-13.37 18.402-13.37z"
  val woostIcon = {
    import svg._
    svg.thunkStatic(uniqueKey)(VDomModifier(
      cls := "svg-inline--fa fa-w-14",
      viewBox := "0 0 10 10",
      g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
        path(d := woostPathCurve, fill := "currentColor")
      )
    ))
  }


  val woostLoadingAnimation: VNode = {
    val lineColor = Colors.woost
    div(
      {
        import svg._
        svg(
          width := "100px", height := "100px", viewBox := "0 0 10 10",
          g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
            path(cls := "woost-loading-animation-logo", d := woostPathCurve, fill := "none", stroke := lineColor, strokeLineCap := "round", strokeWidth := "3.5865", pathLength := "100")
            )
          )
      },
      p("LOADING", marginTop := "16px", dsl.color := "rgba(31, 42, 51, 0.6)", textAlign.center, letterSpacing := "0.05em", fontWeight := 700, fontSize := "15px")
    )
  }

  def woostLoadingAnimationWithFadeIn = woostLoadingAnimation(cls := "animated-fadein")

}
