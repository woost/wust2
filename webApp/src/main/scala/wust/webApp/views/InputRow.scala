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
import wust.ids._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.SharedViewElements._
import monix.reactive.Observer

import scala.collection.breakOut
import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import monix.reactive.subjects.PublishSubject

object InputRow {

  case class Submission(text: String, changes: NodeId => GraphChanges)

  def apply(
    state: GlobalState,
    focusState: Option[FocusState],
    submitAction: Submission => Unit,
    fileUploadHandler: Option[Var[Option[AWS.UploadableFile]]] = None,
    blurAction: Option[String => Unit] = None,
    scrollHandler: Option[ScrollBottomHandler] = None,
    triggerFocus: Observable[Unit] = Observable.empty,
    autoFocus: Boolean = false,
    placeholder: Placeholder = Placeholder.empty,
    preFillByShareApi: Boolean = false,
    submitOnEnter: Boolean = !BrowserDetect.isMobile,
    triggerSubmit: Observable[Unit] = Observable.empty,
    submitIcon: VDomModifier = freeRegular.faPaperPlane,
    showSubmitIcon: Boolean = BrowserDetect.isMobile,
    textAreaModifiers: VDomModifier = VDomModifier.empty,
    allowEmptyString: Boolean = false,
    enforceUserName: Boolean = false,
    showMarkdownHelp: Boolean = false,
    enableEmojiPicker: Boolean = false,
    enableMentions: Boolean = true,
  )(implicit ctx: Ctx.Owner): VNode = {
    val initialValue = if (preFillByShareApi) Rx {
      state.urlConfig().shareOptions.fold("") { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }.toObservable.dropWhile(_.isEmpty)
    else Observable.empty // drop starting sequence of empty values. only interested once share api defined.

    val autoResizer = new TextAreaAutoResizer
    val collectedMentions = new mutable.HashMap[String, Node]

    val heightOptions = VDomModifier(
      rows := 1,
      resize := "none",
      minHeight := "42px",
      autoResizer.modifiers
    )

    def nodeToSelectString(node: Node): String = {
      val lines = node.str.linesIterator
      val linesHead = if (lines.hasNext) StringOps.trimToMaxLength(lines.next.replace("\\", "\\\\").replace(" ", "\\ "), 100) else ""
      linesHead
    }

    var currentTextArea: dom.html.TextArea = null
    def handleInput(str: String): Unit = if (allowEmptyString || str.trim.nonEmpty || fileUploadHandler.exists(_.now.isDefined)) {
      def handle() = {
        val actualMentions = {
          val mentionsRegex = raw"@(?:[^ \\]|\\\\)*(?:\\ [^ ]*)*".r
          val stringMentions = mentionsRegex.findAllIn(str).map(_.drop(1)).to[List]
          stringMentions.flatMap(str => collectedMentions.get(str).map(str -> _))
        }
        def extraChanges(nodeId: NodeId): GraphChanges = {
          GraphChanges(
            addEdges = actualMentions.map { case (mentionName, mentionedNode) =>
              Edge.Mention(nodeId, EdgeData.Mention(mentionName), mentionedNode.id)
            }(breakOut)
          )
        }
        collectedMentions.clear()
        submitAction(Submission(str, extraChanges))
        if (preFillByShareApi && state.urlConfig.now.shareOptions.isDefined) {
          state.urlConfig.update(_.copy(shareOptions = None))
        }
        if (BrowserDetect.isMobile) currentTextArea.focus() // re-gain focus on mobile. Focus gets lost and closes the on-screen keyboard after pressing the button.
      }
      if (enforceUserName && !state.askedForUnregisteredUserName.now) {
        state.askedForUnregisteredUserName() = true
        state.user.now match {
          case user: AuthUser.Implicit if user.name.isEmpty =>
            val sink = state.eventProcessor.changes.redirectMapMaybe[Submission] { sub =>
              val userNode = user.toNode
              userNode.data.updateName(sub.text).map(data => GraphChanges.addNode(userNode.copy(data = data)) merge sub.changes(userNode.id))
            }
            state.uiModalConfig.onNext(Ownable(implicit ctx => newNamePromptModalConfig(state, sink, "Give yourself a name, so others can recognize you.", placeholder = Placeholder(Components.implicitUserName), onClose = () => { handle(); true }, enableMentions = false)))
          case _ => handle()
        }
      } else {
        handle()
      }
    }

    val emojiPicker = if (enableEmojiPicker && !BrowserDetect.isMobile) {
      Some(VDomModifier(
        snabbdom.VNodeProxy.repairDomBeforePatch, // the emoji-picker modifies the dom
        onDomMount.foreach {
          //TODO: only init for this element? not do whole initialization?
          wdtEmojiBundle.init(".inputrow.field.enabled-emoji-picker")
        },
        cls := "enabled-emoji-picker",
        cls := "wdt-emoji-open-on-colon"
      ))
    } else None

    val mentionsTribute = if (enableMentions) {
      import wust.facades.tribute._
      val tribute = new Tribute(new TributeCollection[Node] {
        trigger = "@"
        lookup = { (node, text) =>
          node.str
        }: js.Function2[Node, String, String]
        selectTemplate = { item =>
          item.fold("@") { item =>
            "@" + nodeToSelectString(item.original)
          }
        }: js.Function1[js.UndefOr[TributeItem[Node]], String]
        menuItemTemplate = { item =>
          Components.nodeCard(item.original, projectWithIcon = true, maxLength = Some(200)).render.outerHTML
        }: js.Function1[TributeItem[Node], String]
        noMatchTemplate = { () =>
          i("Not Found").render.outerHTML
        }: js.Function0[String]
        spaceSelectsMatch = true
        values = { (text, cb) =>
          val graph = state.graph.now
          cb(
            focusState.flatMap(f => graph.nodesById(f.focusedId).collect { case node: Node.Content => node.copy(data = NodeData.Markdown("all")) }).toJSArray ++
              graph.nodes.collect { case item if EmojiReplacer.emojiAtBeginningRegex.replaceFirstIn(item.str, "").trim.toLowerCase.startsWith(text.toLowerCase) => item }.take(50)
          )
        }: js.Function2[String, js.Function1[js.Array[Node], Unit], Unit]
        searchOpts = new TributeSearchOpts {
          pre = ""
          post = ""
        }
      })

      Some(tribute)
    } else None

    val filterSubmitEvent = () => mentionsTribute.forall(tribute => !tribute.isActive) //&& emojiPicker.forall(_ => dom.document.querySelectorAll(".wdt-emoji-picker-open").length == 0)

    val initialValueAndSubmitOptions = {
      // ignore submit events if mentions or emoji picker is open
      if (submitOnEnter) {
        valueWithEnterWithInitial(initialValue, filterEvent = filterSubmitEvent) foreach handleInput _
      } else {
        valueWithCtrlEnterWithInitial(initialValue, filterEvent = filterSubmitEvent) foreach handleInput _
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

    def submit():Unit = {
      handleInput(currentTextArea.value)
      currentTextArea.value = ""
      autoResizer.trigger()
    }

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
      onClick
        .filter(_ => filterSubmitEvent())
        .stopPropagation foreach { submit() },
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
      emitter(triggerSubmit).foreach { submit() },
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
          emojiPicker,
          mentionsTribute.map { tribute =>
            VDomModifier(
              tribute,
              wust.facades.tribute.Tribute.replacedEvent[Node].foreach { e =>
                e.detail.item.foreach { item =>
                  val completedStr = nodeToSelectString(item.original)
                  collectedMentions(completedStr) = item.original
                }
              }
            )
          },
          textAreaModifiers,
        ),
        markdownHelp,
      ),
      fileUploadHandler.map(UploadComponents.uploadField(state, _).apply(Styles.flexStatic, width := "unset")), // unsetting width:100% from commonedithandler
      VDomModifier.ifTrue(showSubmitIcon)(submitButton.apply(Styles.flexStatic)),
      onClick.stopPropagation --> Observer.empty, // prevents globalClick trigger (which e.g. closes emojiPicker - it doesn't even open it in the first place)
    )
  }
}
