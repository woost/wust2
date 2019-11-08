package wust.css

import scalacss.DevDefaults._
import scalacss.internal.ValueT.{ Len, TypedAttrBase, TypedAttrT1, ZeroLit }
import scalacss.internal.{ Attr, CanIUse, Transform }
import wust.sdk.Colors

import scala.concurrent.duration._

trait TaskList extends StyleSheet.Standalone {
  import dsl._

  val listViewLeftMargin = 4.px
  val taskPaddingPx = 8
  val taskPadding = taskPaddingPx.px
  val tagMarginPx = 2
  val tagMargin = tagMarginPx.px
  val taskPaddingCompactPx = 4
  val taskPaddingCompact = taskPaddingCompactPx.px


  ".tasklist" - (
    paddingTop(1 px), // space for nodecard shadow
    minHeight(20 px).important, // enough vertical space to drop tasks, important overwrites Styles.flex minheight

    Styles.flex,
    flexDirection.column, // make task margin work correctly


    &(".nodecard") - (
      margin(2 px, listViewLeftMargin),

      &(".nodecard-content") - (
        padding(taskPadding, taskPadding, (taskPaddingPx - tagMarginPx).px, taskPadding),// we substract tagMargin to achieve a consistent height of node-cards with and without tags in the same line
      ),

      &(".nodecard-content > .markdown") - (
        marginBottom(tagMargin), // to achieve a consistent height of node-cards with and without tags
      )
    ),

    &(".nodecard > .checkbox") - (
      marginTop((taskPaddingPx + 1) px),
      marginLeft((taskPaddingPx + 1) px),
    ),
  )

  ".tasklist.compact" - (
    &(".nodecard") - (
      &(".nodecard-content") - (
        padding(taskPaddingCompact, taskPaddingCompact, (taskPaddingCompactPx - tagMarginPx).px, taskPaddingCompact),// we substract tagMargin to achieve a consistent height of node-cards with and without tags
      ),
    ),

    &(".nodecard > .checkbox") - (
      marginTop((taskPaddingCompactPx + 1) px),
      marginLeft((taskPaddingCompactPx + 1) px),
    ),
  )

  ".tasklist-header" - (
    fontSize.large,
    marginBottom(0 px),
    marginLeft(listViewLeftMargin),
  )

  ".listviewaddsectiontext" - (
    color(white),
    opacity(0.5),
    fontSize.medium,
    fontWeight.normal,
    cursor.pointer,
  )

  ".listviewaddsectiontext:hover" - (
    opacity(1),
  )
}
