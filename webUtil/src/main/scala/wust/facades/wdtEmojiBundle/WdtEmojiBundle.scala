package wust.facades.wdtEmojiBundle

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom.raw.HTMLElement

// https://ned.im/wdt-emoji-bundle/

@js.native
@JSGlobal
object wdtEmojiBundle extends js.Object {
  def init(selector: String): Unit = js.native
  def defaults:Options = js.native
  def changeType(tpe: String):Unit = js.native
  def close():Unit = js.native
}

trait Options extends js.Object {
  var emojiType:String
  var emojiSheets:EmojiSheets
}

trait EmojiSheets extends js.Object {
  var twitter:String
  var apple:String
  var pickerColors:js.Array[String]
}
