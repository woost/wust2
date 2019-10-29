package wust.webApp

import cats.effect.IO
import colorado.HCL
import org.scalajs.dom
import org.scalajs.dom.{console, document}
import outwatch.dom._
import rx._
import wust.api.ApiEvent
import wust.facades.defaultPassiveEvents.DefaultPassiveEvents
import wust.facades.dompurify.DOMPurify
import wust.facades.emojijs.EmojiConvertor
import wust.facades.fomanticui.SearchResults
import wust.facades.highlightjs.Highlight
import wust.facades.intersectionObserver.IntersectionObserver
import wust.facades.jquery.JQuery
import wust.facades.marked.{Marked, MarkedOptions, Renderer}
import wust.facades.wdtEmojiBundle._
import wust.graph.Node
import wust.webApp.dragdrop.SortableEvents
import wust.webApp.state.{GlobalState, GlobalStateFactory}
import wust.webApp.views.{GenericSidebar, MainView, Modal}
import wust.webUtil.{Elements, JSDefined}
import wust.webUtil.outwatchHelpers._

import scala.scalajs.js.JSON
import scala.scalajs.{LinkingInfo, js}
import wust.facades.segment.Segment

object Main {

  def main(args: Array[String]): Unit = {
    Logging.setup()

    setupDom()

    outwatch.woost.OutwatchSetting.init()

    GlobalStateFactory.init()
    Segment.trackEvent("Version", js.Dynamic.literal(version = WoostConfig.value.versionString))

    DevOnly { enableEventLogging() }
    SortableEvents.init()

    // render main content
    import GlobalState.ctx
    val app = for {
      _ <- OutWatch.renderReplace[IO]("#container", MainView.apply)
      // render single modal instance for the whole page that can be configured via GlobalState.uiModalConfig
      _ <- OutWatch.renderReplace[IO]("#modal-placeholder", Modal.modal(GlobalState.uiModalConfig, GlobalState.uiModalClose))
      // render single sidebar instance for the whole page that can be configured via GlobalState.uiSidebarConfig
      _ <- OutWatch.renderReplace[IO]("#sidebar-placeholder", GenericSidebar.sidebar(GlobalState.uiSidebarConfig, GlobalState.uiSidebarClose, targetSelector = Some(".main-viewrender")))
    } yield ()

    app.unsafeRunSync()
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
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
            views.Components.nodeCardAsOneLineText( node, projectWithIcon = true)(Ctx.Owner.Unsafe).prepend(
              cursor.pointer,
              Styles.flex,
              alignItems.center
            )
          )
        }
      ).render.outerHTML
    }: js.Function1[SearchResults, String]
  }

  private def setupDom(): Unit = {
    setupDefaultPassiveEvents()
    setupIntersectionObserverPolyfill()
    setupDomPurify()
    setupMarked()
    setupEmojis()
    setupEmojiPicker()
    setupFomanticUISearch()
    setupChartJS()

    if (LinkingInfo.developmentMode) {
      setupRuntimeScalaCSSInjection()
       // setupSnabbdomDebugging()
    }
  }

  private def setupChartJS(): Unit = {
    typings.chartDotJs.chartDotJsRequire
  }

  private def enableEventLogging() = {
    val boxBgColor = "#666" // HCL(baseHue, 50, 63).toHex
    val boxStyle =
      s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold"
    val color = HCL(0, 0, 93).toHex // HCL(baseHue, 20, 93).toHex
    Client.observable.event.foreach { events =>
      events.foreach {
        case ApiEvent.NewGraphChanges(user, change) =>
          console.log(s"%c ➙ from User:${ user.name } %c ${ change.toPrettyString(GlobalState.graph.now) }", boxStyle, s"background: $color")
        case other                                  => console.log(s"%c ➙ %c ${ other }", boxStyle, s"background: $color")
      }
    }
  }

  private def setupDomPurify(): Unit = {
    // make all links target blank with safe rel props
    // see: https://github.com/cure53/DOMPurify/blob/master/demos/hooks-target-blank-demo.html
    DOMPurify.addHook("afterSanitizeAttributes", { node =>
        if (js.Object.hasProperty(node, "target")) {
            // set all elements owning target to target=_blank
            node.setAttribute("target","_blank");
            // prevent https://www.owasp.org/index.php/Reverse_Tabnabbing
            node.setAttribute("rel", s"${Elements.safeRelForTargetBlank} nofollow");
            // If link is in nodecard, stopPropagation prevents the nodecard click (e.g. rightsidebar)
            node.setAttribute("onclick", "event.stopPropagation()")
        }

        if (!node.hasAttribute("target") && (node.hasAttribute("xlink:href") || node.hasAttribute("href"))) {
            // set non-HTML/MathML links to xlink:show=new
            node.setAttribute("xlink:show", "new");
        }

        node
    })
  }

  private def setupMarked():Unit = {
    Marked.setOptions(new MarkedOptions {
      renderer = new Renderer()
      gfm = true
      breaks = true // If true, add <br> on a single line break (copies GitHub). Requires gfm be true.
      highlight = ((code: String, lang: js.UndefOr[String]) => { // Only gets called for code blocks
        lang match {
          case JSDefined(lang) if Highlight.getLanguage(lang).isDefined => Highlight.highlight(lang, code).value
          case _ => Highlight.highlightAuto(code).value
        }
      }): js.Function2[String, js.UndefOr[String], String]

      // We sanitize marked output using dompurify in Elements.markdownString
    })
  }

  private def setupEmojis():Unit = {
    // setup emoji converter
    EmojiConvertor.img_sets.twitter.sheet = WoostConfig.value.urls.emojiSheet
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

  private def setupEmojiPicker():Unit = {
    wdtEmojiBundle.defaults.emojiType = "twitter"
    wdtEmojiBundle.defaults.emojiSheets.twitter = WoostConfig.value.urls.emojiPickerSheet

    // reposition hack, because picker only opens to the bottom (https://github.com/needim/wdt-emoji-bundle/blob/master/wdt-emoji-bundle.js#L230)
    val oldOpen = wdtEmojiBundle.asInstanceOf[js.Dynamic].openPicker.asInstanceOf[js.Function1[js.Any, js.Any]]
    wdtEmojiBundle.asInstanceOf[js.Dynamic].openPicker = { (self, ev) =>
      val element = document.querySelector(".wdt-emoji-popup")
      if (element != null) {
        element.asInstanceOf[dom.html.Element].style.marginTop = "0px"
      }
      oldOpen.call(self, ev)
      if (element != null && element.classList.contains("open")) {
        val rect = element.getBoundingClientRect();
        val inViewPort =
            rect.top >= 0 &&
            rect.left >= 0 &&
            rect.bottom <= (dom.window.innerHeight) &&
            rect.right <= (dom.window.innerWidth)
        if (!inViewPort) {
          element.asInstanceOf[dom.html.Element].style.marginTop = "-387px"
        }
      }
    }: js.ThisFunction1[js.Object, js.Any, js.Any]
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

  private def setupDefaultPassiveEvents():Unit = {
    // initialize default-passive-events for smoother scrolling
    DefaultPassiveEvents
  }

  private def setupIntersectionObserverPolyfill():Unit = {
    // initialize intersection-observer polyfill for older browsers like safari < 12.1
    IntersectionObserver
  }
}
