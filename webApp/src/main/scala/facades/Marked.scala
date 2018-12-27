package marked

import highlight._

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("marked", JSImport.Default)
object Marked extends js.Object {
  def apply(src: String): String = js.native
  def apply(src: String, callback: js.Function): String = js.native
  def apply(src: String, options: MarkedOptions = ???, callback: js.Function = ???): String =
    js.native
  def lexer(src: String, options: MarkedOptions = ???): js.Array[js.Any] = js.native
  def parse(src: String, callback: js.Function): String = js.native
  def parse(src: String, options: MarkedOptions = ???, callback: js.Function = ???): String =
    js.native
  def parser(src: js.Array[js.Any], options: MarkedOptions = ???): String = js.native
  def setOptions(options: MarkedOptions): Unit = js.native
}

@js.native
@JSImport("marked", "Renderer")
class Renderer() extends js.Object {
  var link:js.ThisFunction3[Renderer,String,String,String,String] = js.native
}

trait MarkedOptions extends js.Object {
  var renderer: js.UndefOr[Renderer] = js.undefined
  var gfm: js.UndefOr[Boolean] = js.undefined
  var tables: js.UndefOr[Boolean] = js.undefined
  var breaks: js.UndefOr[Boolean] = js.undefined
  var pedantic: js.UndefOr[Boolean] = js.undefined
  var sanitize: js.UndefOr[Boolean] = js.undefined
  var smartLists: js.UndefOr[Boolean] = js.undefined
  var silent: js.UndefOr[Boolean] = js.undefined
  var langPrefix: js.UndefOr[Boolean] = js.undefined
  var smartypants: js.UndefOr[Boolean] = js.undefined
  var highlight: js.UndefOr[js.Function2[String, js.UndefOr[String], String]] = js.undefined
  var sanitizer: js.UndefOr[js.Function1[String, String]] = js.undefined
}
