package wust.sdk

import colorado._

object Colors {
  @inline final def woost = "#6636b7"

  @inline final def unread = "#1F81F3"
  @inline final def unreadBorder = "#66c0ff"
  @inline final def contentBg = "#EDEDED"

  @inline final def sidebarBg = "#fff"
  @inline final def pageHeaderControl = "rgba(255, 255, 255, 0.75)"

  @inline final def dragHighlight = "rgba(55, 66, 74, 1)"

  @inline final def nodecardBg = "#FEFEFE"
}

object BaseColors {
  val pageBg = RGB("#9A82F9").hcl
  val pageBgLight = RGB("#c6fbd9").hcl

  val tag = RGB("#fa8088").hcl
  val accent = tag
  val eulerBg = tag
}

