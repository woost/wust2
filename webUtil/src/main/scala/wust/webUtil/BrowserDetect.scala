package wust.webUtil

import wust.facades.mobileDetect.MobileDetect
import org.scalajs.dom

object BrowserDetect {
  private val md = new MobileDetect(dom.window.navigator.userAgent)
  final val isMobile = md.mobile() != null
  final val isPhone = md.phone() != null
  final val isTablet = md.tablet() != null
}
