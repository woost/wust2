package wust.webApp

import emojijs.EmojiConvertor
import highlight.Highlight
import marked.{Marked, MarkedOptions}
import monix.reactive.Observable
import org.scalajs.dom.document
import outwatch.dom._
import rx._
import wust.webApp.jsdom.ServiceWorker
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalStateFactory
import wust.webApp.views.MainView

import scala.scalajs.js.JSON
import scala.scalajs.{LinkingInfo, js}

object Main {

  def main(args: Array[String]): Unit = {
    Logging.setup()

    setupDom()

    // register the serviceworker and get an update observable when serviceworker updates are available.
    val swUpdateIsAvailable = if (!LinkingInfo.developmentMode) ServiceWorker.register() else Observable.empty

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
    val state = GlobalStateFactory.create(swUpdateIsAvailable)

    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
  }

  private def setupDom(): Unit = {
    // initialize default-passive-events for smoother scrolling
    defaultPassiveEvents.DefaultPassiveEvents

    // Add polyfill for setImmediate
    // https://developer.mozilla.org/en-US/docs/Web/API/Window/setImmediate
    // Api explanation: https://jphpsf.github.io/setImmediate-shim-demo
    // this will be automatically picked up by monix and used instead of
    // setTimeout( ... , 0)
    // This reduces latency for the async scheduler
    js.Dynamic.global.setImmediate = immediate.immediate


    // setup markdown parser options
    Marked.setOptions(new MarkedOptions {
      gfm = true
      highlight = ((code: String, lang: js.UndefOr[String]) => { // Only gets called for code blocks
        lang.toOption match {
          case Some(l) => "<div class = \"hljs\">" + Highlight.highlight(l, code).value + "</div>"
          case _ => "<div class = \"hljs\">" + Highlight.highlightAuto(code).value + "</div>"
        }
      }): js.Function2[String, js.UndefOr[String], String]
      sanitize = true // this sanitizes all html input
      //TODO provide a sane sanitizer that whitelists some commonly used html features
      // sanitizer = new SanitizeState().getSanitizer(): js.Function1[String, String]
    })


    // setup emoji converter
    EmojiConvertor.img_sets.apple.sheet = "/emoji-datasource/sheet_apple_32.png"
    EmojiConvertor.img_sets.apple.sheet_size = 32
    EmojiConvertor.img_set = "apple"
    EmojiConvertor.use_sheet = true
    EmojiConvertor.init_env()
    EmojiConvertor.include_title = true
    EmojiConvertor.text_mode = false
    EmojiConvertor.colons_mode = false
    EmojiConvertor.allow_native = false
    EmojiConvertor.wrap_native = true
    EmojiConvertor.avoid_ms_emoji = true
    EmojiConvertor.replace_mode = "img"


    // dev defaults
    DevOnly {

      // inject styles tags at runtime. in production a css file is generated and included.
      val styleTag = document.createElement("style")
      document.head.appendChild(styleTag)
      styleTag.innerHTML = wust.css.StyleRendering.renderAll

      // debug snabbdom patching in outwatch
      // helpers.OutwatchTracing.patch.zipWithIndex.foreach { case (proxy, index) =>
      //   org.scalajs.dom.console.log(s"Snabbdom patch ($index)!", JSON.parse(JSON.stringify(proxy)), proxy)
      // }
    }
  }
}
