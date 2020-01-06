package wust.css

import scalacss.DevDefaults._
import scalacss.internal.ValueT.{ Len, TypedAttrBase, TypedAttrT1, ZeroLit }
import scalacss.internal.{ Attr, CanIUse, Transform }
import wust.sdk.Colors

import scala.concurrent.duration._

trait NotesView extends StyleSheet.Standalone {
  import dsl._

  ".notesview" - (
    Styles.flex,
    justifyContent.center,
    &(".notesview-container") - (
      padding(20 px),
      maxWidth(980 px), // like github readme
      width(100 %%),
      &(".notesview-note") - (
        // readability like github readme:
        padding(40 px, 25 px),
        fontSize(16 px),
      ),
    )
  )

  ".screensize-small" - (
    &(".notesview") - (
      &(".notesview-container") - (
        &(".notesview-note") - (
          padding(25 px, 5 px, 5 px, 5 px),
        )
      )
    )
  )

  ".note" - (
    &(".markdown") - (
      Styles.wordWrap,
    )
  )

}
