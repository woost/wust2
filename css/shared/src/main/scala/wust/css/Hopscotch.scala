package wust.css

import scalacss.DevDefaults._
import scalacss.internal.ValueT.{ Len, TypedAttrBase, TypedAttrT1, ZeroLit }
import scalacss.internal.{ Attr, CanIUse, Transform }
import wust.sdk.Colors

import scala.concurrent.duration._

trait Hopscotch extends StyleSheet.Standalone {
  import dsl._

  ".hopscotch-bubble" - (
    &(".hopscotch-nav-button.next") - (
      // make buttons look flat
      (backgroundImage := "none").important,
      (border.none).important,
      (textShadow := "none").important,
    ),
    &(".hopscotch-bubble-number") - (
      lineHeight(29 px).important, // centering 1/x step numbers
      fontSize(16 px).important,
    ),
    zIndex(ZIndex.tutorialOverlay).important
  )
}
