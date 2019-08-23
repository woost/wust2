package wust.sdk

import colorado._

object Colors {
  @inline final def woost = "#6636b7"

  @inline final def unread = "#1F81F3"
  @inline final def unreadBorder = "#66c0ff"
  @inline final def contentBg = "rgba(103, 128, 159, 0.02)"

  @inline final def sidebarBg = "#fff"
  @inline final def fgColor = "rgba(0, 0, 0, 0.87)"
  @inline final def pageHeaderControl = "rgba(255, 255, 255, 0.75)"

  @inline final def dragHighlight = "rgba(55, 66, 74, 1)"
  @inline final def linkColor = "#6495ED"

  @inline final def nodecardBg = "#FEFEFE"
}

object BaseColors {
  val pageBg = RGB("#9A82F9").hcl
  val pageBgLight = RGB("#f9efd7").hcl

  val tag = RGB("#fa8088").hcl
  val accent = RGB("#72cb9e").hcl
  val eulerBg = tag
}

