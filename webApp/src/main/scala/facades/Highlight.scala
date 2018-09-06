package highlight

import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("highlight.js", JSImport.Default)
object Highlight extends js.Object {
  def highlight(name: String, value: String, ignore_illegals: js.UndefOr[Boolean] = js.undefined, continuation: js.UndefOr[js.Object] = js.undefined): HighlightResult = js.native
  def highlightAuto(value: String, languageSubset: js.UndefOr[js.Array[String]] = js.undefined): AutoHighlightResult = js.native
//  def highlightBlock(block: HTMLElement): HTMLElement = js.native ???
  def configure(options: HighlightOptions): String = js.native
}

@js.native
trait HighlightOptions extends js.Object {
  var tabReplace: js.UndefOr[String] = js.native
  var useBr: js.UndefOr[Boolean] = js.native
  var classPrefix: js.UndefOr[String] = js.native
  var languages: js.UndefOr[js.Array[String]] = js.native
}


class AutoHighlightResult(val language: String, val relevance: Int, val value: String, val second_best: js.Object) extends js.Object
class HighlightResult(val language: String, val relevance: Int, val value: String, val top: js.Object) extends js.Object
