package wust.webApp.views

import wust.facades.wdtEmojiBundle._
import fontAwesome._
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, Elements, Ownable, UI }
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.util._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.SharedViewElements._

object InputRow {

  def apply(
    state: GlobalState,
    submitAction: String => Unit,
    fileUploadHandler: Option[Var[Option[AWS.UploadableFile]]] = None,
    blurAction: Option[String => Unit] = None,
    scrollHandler: Option[ScrollBottomHandler] = None,
    triggerFocus: Observable[Unit] = Observable.empty,
    autoFocus: Boolean = false,
    placeholder: Placeholder = Placeholder.empty,
    preFillByShareApi: Boolean = false,
    submitOnEnter: Boolean = !BrowserDetect.isMobile,
    submitIcon: VDomModifier = freeRegular.faPaperPlane,
    showSubmitIcon: Boolean = BrowserDetect.isMobile,
    textAreaModifiers: VDomModifier = VDomModifier.empty,
    allowEmptyString: Boolean = false,
    enforceUserName: Boolean = false,
    showMarkdownHelp: Boolean = false,
    enableEmojiPicker: Boolean = false,
  )(implicit ctx: Ctx.Owner): VNode = {
    val initialValue = if (preFillByShareApi) Rx {
      state.urlConfig().shareOptions.fold("") { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }.toObservable.dropWhile(_.isEmpty)
    else Observable.empty // drop starting sequence of empty values. only interested once share api defined.

    val autoResizer = new TextAreaAutoResizer

    val heightOptions = VDomModifier(
      rows := 1,
      resize := "none",
      minHeight := "42px",
      autoResizer.modifiers
    )

    var currentTextArea: dom.html.TextArea = null
    def handleInput(str: String): Unit = if (allowEmptyString || str.trim.nonEmpty || fileUploadHandler.exists(_.now.isDefined)) {
      def handle() = {
        submitAction(str)
        if (preFillByShareApi && state.urlConfig.now.shareOptions.isDefined) {
          state.urlConfig.update(_.copy(shareOptions = None))
        }
        if (BrowserDetect.isMobile) currentTextArea.focus() // re-gain focus on mobile. Focus gets lost and closes the on-screen keyboard after pressing the button.
      }
      if (enforceUserName && !state.askedForUnregisteredUserName.now) {
        state.askedForUnregisteredUserName() = true
        state.user.now match {
          case user: AuthUser.Implicit if user.name.isEmpty =>
            val sink = state.eventProcessor.changes.redirectMapMaybe[String] { str =>
              val userNode = user.toNode
              userNode.data.updateName(str).map(data => GraphChanges.addNode(userNode.copy(data = data)))
            }
            state.uiModalConfig.onNext(Ownable(implicit ctx => newNamePromptModalConfig(state, sink, "Give yourself a name, so others can recognize you.", placeholder = Placeholder(Components.implicitUserName), onClose = () => { handle(); true })))
          case _ => handle()
        }
      } else {
        handle()
      }
    }

    val initialValueAndSubmitOptions = {
      if (submitOnEnter) {
        valueWithEnterWithInitial(initialValue) foreach handleInput _
      } else {
        valueWithCtrlEnterWithInitial(initialValue) foreach handleInput _
      }
    }

    val placeholderString = if (BrowserDetect.isMobile || state.screenSize.now == ScreenSize.Small) placeholder.short else placeholder.long

    val immediatelyFocus = {
      autoFocus.ifTrue(
        onDomMount.asHtml --> inNextAnimationFrame(_.focus())
      )
    }

    val pageScrollFixForMobileKeyboard = BrowserDetect.isMobile.ifTrue(VDomModifier(
      scrollHandler.map { scrollHandler =>
        VDomModifier(
          onFocus foreach {
            // when mobile keyboard opens, it may scroll up.
            // so we scroll down again.
            if (scrollHandler.isScrolledToBottomNow) {
              window.setTimeout(() => scrollHandler.scrollToBottomInAnimationFrame(), 500)
              // again for slower phones...
              window.setTimeout(() => scrollHandler.scrollToBottomInAnimationFrame(), 2000)
              ()
            }
          },
          eventProp("touchstart") foreach {
            // if field is already focused, but keyboard is closed:
            // we do not know if the keyboard is opened right now,
            // but we can detect if it was opened: by screen-height changes
            if (scrollHandler.isScrolledToBottomNow) {
              val screenHeight = window.screen.availHeight
              window.setTimeout({ () =>
                val keyboardWasOpened = screenHeight > window.screen.availHeight
                if (keyboardWasOpened) scrollHandler.scrollToBottomInAnimationFrame()
              }, 500)
              // and again for slower phones...
              window.setTimeout({ () =>
                val keyboardWasOpened = screenHeight > window.screen.availHeight
                if (keyboardWasOpened) scrollHandler.scrollToBottomInAnimationFrame()
              }, 2000)
              ()
            }
          }
        )
      }
    ))

    def submitButton = div( // clickable box around circular button
      padding := "3px",
      button(
        margin := "0px",
        cls := "ui circular icon button",
        submitIcon,
        fontSize := "1.1rem",
        backgroundColor := "#545454",
        color := "white",
      ),
      onClick.stopPropagation foreach {
        val str = currentTextArea.value
        handleInput(str)
        currentTextArea.value = ""
        autoResizer.trigger()
      },
    )

    val activateEmojiPicker = VDomModifier.ifTrue(enableEmojiPicker && !BrowserDetect.isMobile)(
      snabbdom.VNodeProxy.repairDomBeforePatch, // the emoji-picker modifies the dom
      onDomMount.foreach {
        wdtEmojiBundle.init(".inputrow.field.enabled-emoji-picker")
      },
      cls := "enabled-emoji-picker",
      cls := "wdt-emoji-open-on-colon"
    )

    val markdownHelp = VDomModifier.ifTrue(showMarkdownHelp)(
      position.relative,
      a(
        freeSolid.faFont,
        color := "#a0a8ab",
        position.absolute,
        right := (if(enableEmojiPicker) "38px" else "12px"),
        top := "11px",
        fontSize := "16px",
        float.right,
        Elements.safeTargetBlank,
        UI.tooltip("left center") := "You can use Markdown to format your text. Click for help.",
        href := "https://commonmark.org/help"
      )
    )

    div(
      emitter(triggerFocus).foreach { currentTextArea.focus() },
      Styles.flex,

      alignItems.center,
      form(
        VDomModifier.ifTrue(showSubmitIcon || fileUploadHandler.isDefined)(marginRight := "0"), // icons itself have marginLeft
        width := "100%",
        cls := "ui form",

        textArea(
          onDomUpdate.foreach(autoResizer.trigger()),
          maxHeight := "400px",
          cls := "field",
          cls := "inputrow",
          initialValueAndSubmitOptions,
          heightOptions,
          dsl.placeholder := placeholderString,

          immediatelyFocus,
          blurAction.map(onBlur.value foreach _),
          pageScrollFixForMobileKeyboard,
          onDomMount foreach { e => currentTextArea = e.asInstanceOf[dom.html.TextArea] },
          activateEmojiPicker,
          textAreaModifiers,
        ),
        markdownHelp,
      ),
      fileUploadHandler.map(UploadComponents.uploadField(state, _).apply(Styles.flexStatic, width := "unset")), // unsetting width:100% from commonedithandler
      VDomModifier.ifTrue(showSubmitIcon)(submitButton.apply(Styles.flexStatic))
    )
  }
}
