package wust.webApp.views

import cats.data.NonEmptyList
import colorado.{Color, HCL, LAB, RGB}
import rx.{Ctx, Rx}
import wust.graph.{Node, Page}
import wust.sdk.NodeColor
import wust.sdk.NodeColor._
import wust.util._

import scala.collection.breakOut

object PageStyle {
  object Color {
    //TODO: ensure that these are calculated at compile time
    val baseBgLight = RGB("#e2f8f2").hcl
    val baseBg = RGB("#F3EFCC").hcl
    val baseBgDark = RGB("#4D394B").hcl
    val baseBgDarkHighlight = RGB("#9D929B").hcl
    val border = RGB("#95CCDF").hcl
  }

  def apply(view: Rx[View], page: Rx[Page])(implicit ctx: Ctx.Owner) = {

    def applyPageHue(base: HCL): Rx[String] = Rx {
      val pageHueOpt = NodeColor.pageHue(page()).filter(_ => view().isContent)
      pageHueOpt.fold[Color](LAB(base.l, 0, 0))(hue => HCL(hue, base.c, base.l)).toHex
    }

    new PageStyle(
      accentLineColor = applyPageHue(Color.border),
      bgColor = applyPageHue(Color.baseBg),
      bgLightColor = applyPageHue(Color.baseBgLight),
      darkBgColor = applyPageHue(Color.baseBgDark),
      darkBgColorHighlight = applyPageHue(Color.baseBgDarkHighlight)
    )
  }
}

case class PageStyle(
    accentLineColor: Rx[String],
    bgColor: Rx[String],
    bgLightColor: Rx[String],
    darkBgColor: Rx[String],
    darkBgColorHighlight: Rx[String]
)
