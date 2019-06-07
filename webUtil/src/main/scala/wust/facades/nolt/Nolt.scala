package wust.facades.nolt

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal
object nolt extends js.Object {
  def apply(action:String, noltData:NoltData): Unit = js.native
}

trait NoltData extends js.Object {
  var url:String
  var selector:String
}
