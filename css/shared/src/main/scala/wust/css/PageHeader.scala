package wust.css

import scalacss.DevDefaults._
import scalacss.internal.ValueT.{ Len, TypedAttrBase, TypedAttrT1, ZeroLit }
import scalacss.internal.{ Attr, CanIUse, Transform }
import wust.sdk.Colors

import scala.concurrent.duration._

trait PageHeader extends StyleSheet.Standalone {
  import dsl._

  ".pageheader" - (
    color.white,
    padding(0 px, 5 px),
  )

  ".pageheader-channeltitle" - (
    fontSize(20 px),
    minWidth(30 px), // min-width and height help to edit if channel name is empty
    lineHeight(1.4285 em), // semantic ui default line height
    marginBottom(0 px), // remove margin when title is in <p> (rendered my markdown)
    Styles.flex, // for notification count
    color.white.important // overwriting .nodecard.node color
  )

  ".pageheader-channeltitle.nodecard" - (
    paddingTop(0 px),
    paddingBottom(0 px),
    (boxShadow := "none").important,
  )
  ".pageheader-channeltitle.nodecard.project" - (
    // backgroundcolor set in code dynamically to node color
    color.white,
  )
  ".pageheader-channeltitle.nodecard .nodecard-content" - (
    padding(2 px),
  )

  ".pageheader" - (
    &(".breadcrumb") - (
      // pageheader has a colored background. No shadow needed.
      (boxShadow := s"none").important, // overwrite nodecard shadow
      maxWidth(10 em),
    ),

    &(".breadcrumb.nodecard.project") - (
      paddingLeft(0.5.em),
    )
  )

}
