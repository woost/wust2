package wust.facades.juration

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("juration", JSImport.Default)
object Juration extends js.Object {
  def stringify(value: Double, options: JurationStringifyOptions = ???): String = js.native
  def parse(value: String, options: JurationParseOptions = ???): Double = js.native
}

trait JurationStringifyOptions extends js.Object {
  var format: js.UndefOr[String] = js.undefined // small|micro|long
  var units: js.UndefOr[Int] = js.undefined
}
trait JurationParseOptions extends js.Object {
  var defaultUnit: js.UndefOr[String] = js.undefined
}
