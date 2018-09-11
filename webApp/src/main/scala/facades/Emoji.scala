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

//self.img_sets = {
//'apple' : {'path' : '/emoji-data/img-apple-64/', 'sheet' : '/emoji-data/sheet_apple_64.png', 'sheet_size' : 64, 'mask' : 1},
//'google' : {'path' : '/emoji-data/img-google-64/', 'sheet' : '/emoji-data/sheet_google_64.png', 'sheet_size' : 64, 'mask' : 2},
//'twitter' : {'path' : '/emoji-data/img-twitter-64/', 'sheet' : '/emoji-data/sheet_twitter_64.png', 'sheet_size' : 64, 'mask' : 4},
//'emojione' : {'path' : '/emoji-data/img-emojione-64/', 'sheet' : '/emoji-data/sheet_emojione_64.png', 'sheet_size' : 64, 'mask' : 8},
//'facebook' : {'path' : '/emoji-data/img-facebook-64/', 'sheet' : '/emoji-data/sheet_facebook_64.png', 'sheet_size' : 64, 'mask' : 16},
//'messenger' : {'path' : '/emoji-data/img-messenger-64/', 'sheet' : '/emoji-data/sheet_messenger_64.png', 'sheet_size' : 64, 'mask' : 32},
//};

class ImgSets extends js.Object {
  val apple: ImgSet = new ImgSet {
    var path = "/emoji-data/img-apple-64/"
    var sheet = "/emoji-data/sheet_apple_64.png"
    var sheet_size = 64
    var mask = 1
  }
  val google: ImgSet = new ImgSet {
    var path = "/emoji-data/img-google-64/"
    var sheet = "/emoji-data/sheet_google_64.png"
    var sheet_size = 64
    var mask = 2
  }
}

trait ImgSet extends js.Object {
  var path: String
  var sheet: String
  var sheet_size: Int
  var mask: Int
}
