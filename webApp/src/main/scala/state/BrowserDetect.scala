package wust.webApp

import org.scalajs.dom

object BrowserDetect {
  private val md = new mobiledetect.MobileDetect(dom.window.navigator.userAgent)
  final val isMobile = md.mobile() != null
  final val isPhone = md.phone() != null
  final val isTablet = md.tablet() != null
}
