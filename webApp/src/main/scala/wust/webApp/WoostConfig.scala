package wust.webApp

import org.scalajs.dom.window

import scala.scalajs.js

@js.native
trait WoostConfig extends js.Object {
  def versionString: String = js.native
  def urls: WoostUrls = js.native
  def audience: String = js.native
}

trait WoostUrls extends js.Object {
  def emojiSheet: String
  def emojiPickerSheet: String
  def halfCircle: String
  def wunderlistIcon: String
  def trelloIcon: String
  def meistertaskIcon: String
  def serviceworker: String
}

object WoostConfig {
  val value: WoostConfig = window.asInstanceOf[js.Dynamic].woostConfig.asInstanceOf[WoostConfig]
  val audience: WoostAudience = value.audience match {
    case "dev" => WoostAudience.Dev
    case "staging" => WoostAudience.Staging
    case _ => WoostAudience.App // app
  }
}

sealed trait WoostAudience
object WoostAudience {
  case object Dev extends WoostAudience
  case object Staging extends WoostAudience
  case object App extends WoostAudience
}
