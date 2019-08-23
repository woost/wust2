package wust.facades.emojijs

import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.util.{Try, Success, Failure}

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
  private def replace_emoticons_with_colons(emoticon: String): String = js.native
  private def replace_emoticons(emoticon: String): String = js.native
  private def replace_colons(colonString: String): String = js.native
  private def replace_unified(unifiedString: String): String = js.native
}
object EmojiConvertor extends EmojiConvertor {
  implicit class SafeEmoji(val emoji: EmojiConvertor)  extends AnyVal {
    def replace_colons_safe(s: String): String = Try(emoji.replace_colons(s)) match {
      case Success(s) => s
      case Failure(err) =>
        scribe.warn("Error in emoji in replace_colons", err)
        s
    }
    def replace_unified_safe(s: String): String = Try(emoji.replace_unified(s)) match {
      case Success(s) => s
      case Failure(err) =>
        scribe.warn("Error in emoji in replace_unified", err)
        s
    }
    def replace_emoticons_safe(s: String): String = Try(emoji.replace_emoticons(s)) match {
      case Success(s) => s
      case Failure(err) =>
        scribe.warn("Error in emoji in replace_emoticons", err)
        s
    }
    def replace_emoticons_with_colons_safe(s: String): String = Try(emoji.replace_emoticons_with_colons(s)) match {
      case Success(s) => s
      case Failure(err) =>
        scribe.warn("Error in emoji in replace_emoticons_with_colons", err)
        s
    }
  }
}

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
