package emojijs

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("emoji-js", JSImport.Default)
class EmojiConvertor() extends js.Object {
  var img_set: String = js.native
  var img_sets: ImgSets = js.native
  var use_css_imgs: Boolean = js.native
  var colons_mode: Boolean = js.native
  var text_mode: Boolean = js.native
  var include_title: Boolean = js.native
  var include_text: Boolean = js.native
  var allow_native: Boolean = js.native
  var wrap_native: Boolean = js.native
  var use_sheet: Boolean = js.native
  var avoid_ms_emoji: Boolean = js.native
  var allow_caps: Boolean = js.native
  var img_suffix: String = js.native
  var replace_mode: js.UndefOr[String] = js.native

  def init_env(): Unit = js.native
  def replace_emoticons_with_colons(emoticon: String): String = js.native
  def replace_emoticons(emoticon: String): String = js.native
  def replace_colons(colonString: String): String = js.native
  def replace_unified(unifiedString: String): String = js.native
}
object EmojiConvertor extends EmojiConvertor

trait ImgSets extends js.Object {
  def apple: ImgSet
  def google: ImgSet
  def twitter: ImgSet
}

trait ImgSet extends js.Object {
  var path: String
  var sheet: String
  var sheet_size: Int
  var mask: Int
}
