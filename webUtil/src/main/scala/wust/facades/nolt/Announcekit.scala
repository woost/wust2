package wust.facades.announcekit

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal
object announcekit extends js.Object {
  def push(options: AnnouncekitOptions): Unit = js.native
}

trait AnnouncekitOptions extends js.Object {
  var widget: js.UndefOr[String] = js.undefined
  var version: js.UndefOr[Int] = js.undefined
  var selector: js.UndefOr[String] = js.undefined
  var embed: js.UndefOr[Boolean] = js.undefined
  var name: js.UndefOr[String] = js.undefined
  var data: js.UndefOr[AnnouncekitDataOptions] = js.undefined
  var badge: js.UndefOr[AnnouncekitBadgeOptions] = js.undefined
}

trait AnnouncekitDataOptions extends js.Object {
  var user_id: js.UndefOr[String] = js.undefined
  var user_email: js.UndefOr[String] = js.undefined
  var user_name: js.UndefOr[String] = js.undefined
}

trait AnnouncekitBadgeOptions extends js.Object {
  var style: js.UndefOr[js.Dynamic] = js.undefined
}
