package wust.webApp.views

import cats.data.NonEmptyList
import colorado.{Color, HCL, LAB, RGB}
import rx.{Ctx, Rx}
import wust.graph.{Node, Page}
import wust.sdk.NodeColor._

import scala.collection.breakOut

object PageStyle {
  object Color {
    //TODO: ensure that these are calculated at compile time
    val baseBgLight = RGB("#f2fdfb").hcl
    val baseBg = RGB("#F3EFCC").hcl
    val baseBgDark = RGB("#4D394B").hcl
    val baseBgDarkHighlight = RGB("#9D929B").hcl
    val border = RGB("#95CCDF").hcl
  }

  def apply(view: Rx[View], page: Rx[Page])(implicit ctx: Ctx.Owner) = {
    val pageColor: Rx[Option[LAB]] = Rx {
      view() match {
        case view if view.isContent =>
          NonEmptyList
            .fromList(page().parentIds.map(baseColor)(breakOut): List[Color])
            .map(mixColors)
        case _ => None
      }
    }

    val pageHue: Rx[Option[Double]] = Rx { pageColor().map(_.hcl.h) }
    def withBaseHueDefaultGray(base: HCL): Rx[String] = Rx {
      val pageHue = pageColor().map(_.hcl.h)
      pageHue.fold(LAB(base.l, 0, 0): Color)(hue => HCL(hue, base.c, base.l)).toHex
    }

    new PageStyle(
      accentLineColor = withBaseHueDefaultGray(Color.border),
      bgColor = withBaseHueDefaultGray(Color.baseBg),
      bgLightColor = withBaseHueDefaultGray(Color.baseBgLight),
      darkBgColor = withBaseHueDefaultGray(Color.baseBgDark),
      darkBgColorHighlight = withBaseHueDefaultGray(Color.baseBgDarkHighlight)
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
