package woostConfig

import org.scalajs.dom.window

import scala.scalajs.js

@js.native
trait WoostConfig extends js.Object {
  def versionString: String = js.native
}

object WoostConfig {
  val value: WoostConfig = window.asInstanceOf[js.Dynamic].woostConfig.asInstanceOf[WoostConfig]
}
