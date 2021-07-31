package wust.facades.crisp

import scala.scalajs.js
import scala.scalajs.js.annotation._

//TODO: this is a workaround for scalajs: https://github.com/scala-js/scala-js/issues/3737
object JSNames {
  final val Crisp = "$crisp"
}
@js.native
@JSGlobal(JSNames.Crisp)
object crisp extends js.Object {
  def push(options: js.Array[js.Any]): Unit = js.native
  def is: js.UndefOr[js.Function1[String,Boolean]] = js.native
}

