package wust.css

import scalacss.DevDefaults._
import scalacss.internal.ValueT.{ Len, TypedAttrBase, TypedAttrT1, ZeroLit }
import scalacss.internal.{ Attr, CanIUse, Transform }
import wust.sdk.Colors

import scala.concurrent.duration._

trait BreadCrumbs extends StyleSheet.Standalone {
  import dsl._

  val lineHeightSemanticUI = 19.999 px

  ".breadcrumbs" - (
    padding(2 px, 2 px), // some padding is needed to display the box-shadow

    Styles.flex,
    alignItems.center,
    overflow.hidden,

    &(".cycle-indicator") - (
      verticalAlign.middle,
      margin(1.px),
      width(0.8.em)
    ),

    &(".divider") - (
      marginLeft(3 px),
      marginRight(3 px),
      color(c"rgba(255, 255, 255, 0.78)"),
      fontSize(18 px),
    ),

    &(".nodecard") - (
      padding(1 px, 3 px),
      &(".nodecard-content") - (
        padding(0 px, 2 px)
      ),

    ),

    &(".breadcrumb") - (
      minWidth(2 em).important, // to leave at least the icon when shrinking, important to overwrite min-width:0 of Styles-flex

      // max-height: 1 line (can happen when using eg raw HTML in markdown)
      lineHeight(lineHeightSemanticUI),
      maxHeight(lineHeightSemanticUI),
      overflow.hidden,
    ),

    &(".breadcrumb," +
    ".breadcrumb *:not(.emoji-outer):not(.emoji-sizer):not(.emoji-inner)") - (
      fontSize(13 px),
    ),

    // first/last breadcrumb should not have any margin.
    // this way e.g. the cycle shape is closer to the cycle
    &(".breadcrumb:first-of-type") - (
      marginLeft(0 px),
    ),
    &(".breadcrumb:last-of-type") - (
      marginRight(0 px),
    ),
  )
}
