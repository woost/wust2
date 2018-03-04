package wust.pwaApp

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("offline-plugin/runtime", JSImport.Default)
object OfflinePlugin extends js.Object {
  def install(config: OfflinePluginConfig = ???): Unit = js.native
  def applyUpdate(): Unit = js.native
  def update(): Unit = js.native
}

trait OfflinePluginConfig extends js.Object {
  def onUpdating(): Unit
  def onUpdateReady(): Unit
  def onUpdated(): Unit
  def onUpdateFailed(): Unit
}
