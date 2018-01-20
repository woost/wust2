package wust.frontend

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("marked", JSImport.Default)
object marked extends js.Object {
  def apply(src: String): String = js.native
  def apply(src: String, callback: js.Function): String = js.native
  def apply(src: String, options: MarkedOptions = ???, callback: js.Function = ???): String = js.native
  def lexer(src: String, options: MarkedOptions = ???): js.Array[js.Any] = js.native
  def parse(src: String, callback: js.Function): String = js.native
  def parse(src: String, options: MarkedOptions = ???, callback: js.Function = ???): String = js.native
  def parser(src: js.Array[js.Any], options: MarkedOptions = ???): String = js.native
  def setOptions(options: MarkedOptions): Unit = js.native
}

@js.native
trait MarkedOptions extends js.Object {
  var renderer: Object = js.native
  var gfm: Boolean = js.native
  var tables: Boolean = js.native
  var breaks: Boolean = js.native
  var pedantic: Boolean = js.native
  var sanitize: Boolean = js.native
  var smartLists: Boolean = js.native
  var silent: Boolean = js.native
  def highlight(code: String, lang: String, callback: js.Function = ???): String = js.native
  var langPrefix: String = js.native
  var smartypants: Boolean = js.native
}

object MarkedOptions {
  def apply(
             renderer: js.UndefOr[js.Object] = js.undefined,
             gfm: js.UndefOr[Boolean] = js.undefined,
             tables: js.UndefOr[Boolean] = js.undefined,
             breaks: js.UndefOr[Boolean] = js.undefined,
             pedantic: js.UndefOr[Boolean] = js.undefined,
             sanitize: js.UndefOr[Boolean] = js.undefined,
             smartLists: js.UndefOr[Boolean] = js.undefined,
             silent: js.UndefOr[Boolean] = js.undefined,
             highlight: js.UndefOr[js.Function3[String, String,String,js.Function]] = js.undefined,
             langPrefix: js.UndefOr[String] = js.undefined,
             smartypants: js.UndefOr[Boolean] = js.undefined
             ): MarkedOptions = {
    js.Dynamic.literal(
      renderer = renderer,
      gfm = gfm,
      tables = tables,
      breaks = breaks,
      pedantic = pedantic,
      sanitize = sanitize,
      smartLists = smartLists,
      silent = silent,
      highlight = highlight,
      langPrefix = langPrefix,
      smartypants = smartypants
    ).asInstanceOf[MarkedOptions]
  }
}
