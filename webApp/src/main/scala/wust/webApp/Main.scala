package wust.webApp

import colorado.HCL
import wust.facades.defaultPassiveEvents.DefaultPassiveEvents
import wust.facades.emojijs.EmojiConvertor
import wust.facades.fomanticui.SearchResults
import wust.facades.highlightjs.Highlight
import wust.facades.immediate.immediate
import wust.facades.jquery.JQuery
import wust.facades.marked.{Marked, MarkedOptions, Renderer}
import monix.reactive.Observable
import org.scalajs.dom.{console, document}
import outwatch.dom._
import rx._
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._
import wust.api.ApiEvent
import wust.graph.Node
import wust.ids._
import wust.webApp.jsdom.ServiceWorker
import wust.webApp.state.{GlobalState, GlobalStateFactory}
import wust.webApp.views.{GenericSidebar, MainView, Modal}

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

    DevOnly { enableEventLogging(state) }

    // render main content
    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
    // render single modal instance for the whole page that can be configured via state.uiModalConfig
    OutWatch.renderReplace("#modal-placeholder", Modal.modal(state.uiModalConfig, state.uiModalClose)).unsafeRunSync()
    // render single sidebar instance for the whole page that can be configured via state.uiSidebarConfig
    OutWatch.renderReplace("#sidebar-placeholder", GenericSidebar.sidebar(state.uiSidebarConfig, state.uiSidebarClose, targetSelector = Some(".main-viewrender"))).unsafeRunSync()
  }

  private def setupFomanticUISearch(): Unit = {
    import dsl._
    import wust.css.Styles

    JQuery.asInstanceOf[js.Dynamic].`$`.fn.search.settings.templates.node = { results =>
      div(
        results.results.map { result =>
          val node = result.data.asInstanceOf[Node]
          div(
            cls := "result", //we need this class for semantic ui to work,
            div(cls := "title", display.none, result.title), // needed for semantic ui to map the html element back to the SearchSourceEntry
            padding := "4px",
            views.Components.nodeCardAsOneLineText(node).prepend(
              cursor.pointer,
              Styles.flex,
              alignItems.center,
              VDomModifier.ifTrue(node.role == NodeRole.Project || node.isInstanceOf[Node.User])(
                views.Components.nodeAvatar(node, size = 12).apply(
                  Styles.flexStatic,
                  marginRight := "4px",
                )
              )
            )
          )
        }
      ).render.outerHTML
    }: js.Function1[SearchResults, String]
  }

  private def setupDom(): Unit = {
    setupDefaultPassiveEvents()
    setupSetImmediatePolyfill()
    setupMarked()
    setupEmojis()
    setupFomanticUISearch()

    if (LinkingInfo.developmentMode) {
      setupRuntimeScalaCSSInjection()
       // setupSnabbdomDebugging()
    }
  }

  private def enableEventLogging(state:GlobalState)= {
    val boxBgColor = "#666" // HCL(baseHue, 50, 63).toHex
    val boxStyle =
      s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold"
    val color = HCL(0, 0, 93).toHex // HCL(baseHue, 20, 93).toHex
    Client.observable.event.foreach { events =>
      events.foreach {
        case ApiEvent.NewGraphChanges(user, change) =>
          console.log(s"%c ➙ from User:${ user.name } %c ${ change.toPrettyString(state.graph.now) }", boxStyle, s"background: $color")
        case other                                  => console.log(s"%c ➙ %c ${ other }", boxStyle, s"background: $color")
      }
    }
  }

  private def setupMarked():Unit = {
    // to open links in new windows:
    // https://github.com/markedjs/marked/issues/655#issuecomment-383226346
    val newRenderer = new Renderer()
    val linkRenderer = newRenderer.link
    newRenderer.link = {(renderer, href, title, text) => 
      val html = linkRenderer(renderer, href, title, text)
      html.replaceFirst("^<a ", s"""<a target="_blank" rel="${Elements.safeRelForTargetBlank} nofollow" """)
    }

    Marked.setOptions(new MarkedOptions {
      renderer = newRenderer
      gfm = true
      breaks = true // If true, add <br> on a single line break (copies GitHub). Requires gfm be true.
      highlight = ((code: String, lang: js.UndefOr[String]) => { // Only gets called for code blocks
        lang.toOption match {
          case Some(l) if Highlight.getLanguage(l).isDefined => "<div class = \"hljs\">" + Highlight.highlight(l, code).value + "</div>"
          case _ => "<div class = \"hljs\">" + Highlight.highlightAuto(code).value + "</div>"
        }
      }): js.Function2[String, js.UndefOr[String], String]
      sanitize = true // this sanitizes all html input
      //TODO provide a sane sanitizer that whitelists some commonly used html features
      // sanitizer = new SanitizeState().getSanitizer(): js.Function1[String, String]
    })
  }

  private def setupEmojis():Unit = {
    // setup emoji converter
    EmojiConvertor.img_sets.twitter.sheet = "/emoji-datasource/sheet_twitter_64.png"
    EmojiConvertor.img_sets.twitter.sheet_size = 64
    EmojiConvertor.img_set = "twitter"
    EmojiConvertor.use_sheet = true
    EmojiConvertor.init_env()
    EmojiConvertor.include_title = true
    EmojiConvertor.text_mode = false
    EmojiConvertor.colons_mode = false
    EmojiConvertor.allow_native = false
    EmojiConvertor.wrap_native = true
    EmojiConvertor.avoid_ms_emoji = true
    EmojiConvertor.replace_mode = "img"
  }

  private def setupRuntimeScalaCSSInjection():Unit = {
    // inject styles tags at runtime. in production a css file is generated and included.
    val styleTag = document.createElement("style")
    document.head.appendChild(styleTag)
    styleTag.innerHTML = wust.css.StyleRendering.renderAll
  }

  private def setupSnabbdomDebugging():Unit = {
    // debug snabbdom patching in outwatch
    helpers.OutwatchTracing.patch.zipWithIndex.foreach { case (proxy, index) =>
      org.scalajs.dom.console.log(s"Snabbdom patch ($index)!", JSON.parse(JSON.stringify(proxy)), proxy)
    }
  }

  private def setupSetImmediatePolyfill():Unit = {
    // Add polyfill for setImmediate
    // https://developer.mozilla.org/en-US/docs/Web/API/Window/setImmediate
    // Api explanation: https://jphpsf.github.io/setImmediate-shim-demo
    // this will be automatically picked up by monix and used instead of
    // setTimeout( ... , 0)
    // This reduces latency for the async scheduler
    js.Dynamic.global.setImmediate = immediate
  }

  private def setupDefaultPassiveEvents():Unit = {
    // initialize default-passive-events for smoother scrolling
    DefaultPassiveEvents
  }
}
