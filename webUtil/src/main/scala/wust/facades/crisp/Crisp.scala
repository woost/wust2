package wust.facades.crisp

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal("$crisp")
object crisp extends js.Object {
  def push(options: js.Array[js.Any]): Unit = js.native
  def is: js.UndefOr[js.Function1[String,Boolean]] = js.native
}

