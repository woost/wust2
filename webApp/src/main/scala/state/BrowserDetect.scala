package wust.webApp

import org.scalajs.dom

object BrowserDetect {
  private val md = new mobiledetect.MobileDetect(dom.window.navigator.userAgent)
  val isMobile = md.mobile() != null
  val isPhone = md.phone() != null
  val isTablet = md.tablet() != null
}
