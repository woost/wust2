package sanitizer

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("marked-sanitizer-github", JSImport.Default)
class SanitizeState() extends js.Object {
  def getSanitizer(): js.Function1[String, String] = js.native
  def reset(): Unit = js.native
  def isBroken(): Boolean = js.native
  def isInUse(): Boolean = js.native
}
