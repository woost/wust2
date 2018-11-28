package woostConfig

import scala.scalajs.js
import org.scalajs.dom.window

@js.native
trait WoostConfig extends js.Object {
  def versionString: String = js.native
}

object WoostConfig {
  val value: WoostConfig = window.asInstanceOf[js.Dynamic].woostConfig.asInstanceOf[WoostConfig]
}
