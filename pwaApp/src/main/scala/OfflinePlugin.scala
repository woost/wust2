package wust.pwaApp

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("offline-plugin/runtime", JSImport.Default)
object OfflinePlugin extends js.Object {
  def install(): Unit = js.native
}
