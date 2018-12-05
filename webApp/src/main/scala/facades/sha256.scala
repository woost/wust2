package sha256

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("js-sha256", JSImport.Namespace)
object Sha256 extends js.Object {
  def sha256(msg: String): String = js.native
  def sha224(msg: String): String = js.native
}
