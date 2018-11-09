package mobiledetect

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("mobile-detect", JSImport.Namespace)
class MobileDetect(userAgent: String) extends js.Object {
  def mobile(): String = js.native
  def phone(): String = js.native
  def tablet(): String = js.native
  def userAgent(): String = js.native
  def os(): String = js.native
  def is(s: String): Boolean = js.native
  def version(s: String): Double = js.native
  def versionStr(s: String): String = js.native
  def `match`(s: String): Boolean = js.native
}
