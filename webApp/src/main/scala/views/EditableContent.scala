package wust.webApp.views

import fontAwesome.freeSolid
import monix.eval.Task
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import outwatch.Sink
import outwatch.dom.helpers.{AttributeBuilder, EmitterBuilder}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp._
import wust.css.Styles
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Elements._

import scala.concurrent.duration._
import scala.scalajs.js

object EditableContent {

  sealed trait ErrorMode
  object ErrorMode {
    case object Cancel extends ErrorMode
    case object ShowToast extends ErrorMode
    case object ShowInline extends ErrorMode
    case object Ignore extends ErrorMode
  }

  sealed trait SubmitMode
  object SubmitMode {
    // emitter submit mode has no magic and just emits a new value (input or error) whenever the mentioned emitters trigger.
    // should be used in forms where you have an action like enter or a specific submit button.
    // then you set your desired emitter, and in the end use the current value in your submit logic
    case class Emitter(builder: List[EmitterBuilder[dom.Event, VDomModifier]]) extends SubmitMode
    def OnChange = Emitter(dsl.onChange :: Nil)
    def OnInput = Emitter(dsl.onInput :: Nil)
    def OnEnter = Emitter(Elements.onEnter :: Nil)
    def Off = Emitter(Nil)

    // explicit submit mode is special in that it is built for edit fields without any submit button.
    // we only emit a new value (input or error) when onBlur or onEnter were triggered. If the value has
    // not changed, it will emit cancel. It will addditionally emit cancel when escape is pressed.
    // furthermore a small x-button is embedded into the ui.
    case object Explicit extends SubmitMode
  }

  case class Config(
    innerModifier: VDomModifier = VDomModifier.empty,
    outerModifier: VDomModifier = VDomModifier.empty,
    submitMode: SubmitMode = SubmitMode.Explicit,
    errorMode: ErrorMode = ErrorMode.ShowInline,
    selectTextOnFocus: Boolean = true,
  )
  object Config {
    def default = Config()
    def cancelOnError = Config(
      errorMode = ErrorMode.Cancel
    )
    def off = Config(
      submitMode = SubmitMode.Off,
      errorMode = ErrorMode.Ignore
    )
  }

  //TODO elegant wrap in form to check reportvalidity on inputfield constraints?
  @inline def inputField[T: EditInputParser: ValueStringifier](implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = inputField[T](Config.default)
  def inputField[T: EditInputParser: ValueStringifier](config: Config)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]]

    VDomModifier(
      emitter(currentVar) --> action,
      inputFieldRxInteraction(currentVar, config)
    )
  }

  def inputField[T: EditInputParser: ValueStringifier](current: T, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]](EditInteraction.Input(current))

    VDomModifier(
      emitter(currentVar.drop(1)) --> action,
      inputFieldRxInteraction(currentVar, config)
    )
  }

  @inline def inputFieldRx[T: EditInputParser: ValueStringifier](current: Var[Option[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = inputFieldRxInteraction[T](zoomOutToEditInteraction(current), config)
  def inputFieldRxInteraction[T: EditInputParser: ValueStringifier](current: Handler[EditInteraction[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = {
    val modifier = EditInputParser[T].modifier
    commonEditStructure(current, config)(
      input(
        modifier.mod,
        commonEditMods[T, dom.html.Input](current, config, EditInputParser[T].parse(_), modifier.valueSetter, modifier.fixedSubmitEvent),
      ),
      modifier.outerMod,
    )
  }

  @inline def inputInline[T: EditStringParser: ValueStringifier](implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = inputInline[T](Config.default)
  def inputInline[T: EditStringParser: ValueStringifier](config: Config)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier{ action =>
    val currentVar = Handler.unsafe[EditInteraction[T]]

    VDomModifier(
      emitter(currentVar) --> action,
      inputInlineRxInteraction[T](currentVar, config)
    )
  }

  def inputInline[T: EditStringParser: ValueStringifier](current: T, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]](EditInteraction.Input(current))

    VDomModifier(
      emitter(currentVar.drop(1)) --> action,
      inputInlineRxInteraction[T](currentVar, config)
    )
  }

  @inline def inputInlineRx[T: EditStringParser: ValueStringifier](current: Var[Option[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = inputInlineRxInteraction[T](zoomOutToEditInteraction(current), config)
  def inputInlineRxInteraction[T: EditStringParser: ValueStringifier](current: Handler[EditInteraction[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = {
    commonEditStructure(current, config)(
      div(
        outline := "none", // hides contenteditable outline
        contentEditable := true,
        backgroundColor := "#FFF",
        color := "#000",
        cursor.auto,

        commonEditMods[T, dom.html.Element](current, config, EditStringParser.parseElement[T], _.fold[String](identity, identity))
      )
    )
  }

  def inputFieldOrRender[T: EditInputParser: ValueStringifier](current: T, editMode: Var[Boolean], renderFn: T => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = editOrRender[T](current, editMode, renderFn, inputFieldRxInteraction(_, config))

  def inputInlineOrRender[T: EditStringParser: ValueStringifier](current: T, editMode: Var[Boolean], renderFn: T => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = editOrRender[T](current, editMode, renderFn, inputInlineRxInteraction(_, config))

  def ofNode(state: GlobalState, node: Node, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[Node], VDomModifier] = EmitterBuilder.ofModifier[EditInteraction[Node]] { action =>
    implicit val context = EditContext(state)

    EditStringParser.forNode(node).map { implicit parser =>
      inputInline[Node](node, config) --> action
    } orElse EditInputParser.forNode(node).map { implicit parser =>
      inputField[Node](node, config) --> action
    }
  }

  def ofNodeOrRender(state: GlobalState, node: Node, editMode: Var[Boolean], renderFn: Node => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[Node], VDomModifier] = EmitterBuilder.ofModifier[EditInteraction[Node]] { action =>
    implicit val context = EditContext(state)

    EditStringParser.forNode(node).map { implicit parser =>
      inputInlineOrRender[Node](node, editMode, renderFn, config) --> action
    } orElse EditInputParser.forNode(node).map { implicit parser =>
      inputFieldOrRender[Node](node, editMode, renderFn, config) --> action
    }
  }

  def select[T: EditStringParser: ValueStringifier](header: String, activeElement: Var[Option[T]], elements: Seq[(String, T)])(implicit ctx: Ctx.Owner): VNode = {
    dsl.select(
      option(
        value := "", header,
        selected <-- activeElement.map(_.isEmpty),
        disabled,
      ),
      elements.map { case (title, element) =>
        option(value := ValueStringifier[T].stringify(element), title, selected <-- activeElement.map(_ contains element)),
      },
      onInput.transform(_.mapEval(e => EditStringParser.parseSelect(e.currentTarget.asInstanceOf[dom.html.Select]))).editValueOption --> activeElement,
    )
  }

  private def closeButton(current: Observer[EditInteraction[Nothing]]) = i(
    cls := "close icon link",
    styleAttr := "cursor: pointer !important", // overwrite style from semantic ui with important
    onClick.stopPropagation(EditInteraction.Cancel) --> current
  )

  private def commonEditStructure[T](current: Handler[EditInteraction[T]], config: Config)(modifiers: VDomModifier*) = {
    div(
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      div(
        width := "100%",
        Styles.flex,
        modifiers,
        Some(config.submitMode).collect {
          case SubmitMode.Explicit => closeButton(current)
        }
      ),

      config.outerModifier,
      showErrorsOutside(current, config.errorMode)
    )
  }

  private def commonEditMods[T: ValueStringifier, Elem <: dom.html.Element](current: Handler[EditInteraction[T]], config: Config, rawParse: Elem => Task[EditInteraction[T]], valueSetter: AttributeBuilder[Either[String, String], VDomModifier], fixedSubmitEvent: Option[EmitterBuilder[dom.Event, VDomModifier]] = None) = {
    var elem: dom.html.Element = null
    var lastValue: Option[T] = None
    val parse: Elem => Task[EditInteraction[T]] = { elem =>
      rawParse(elem).map {
        case e@EditInteraction.Input(v) => config.submitMode match {
          case SubmitMode.Explicit => if (lastValue contains v) EditInteraction.Cancel else e
          case _ => e
        }
        case e@EditInteraction.Error(error) => config.errorMode match {
          case ErrorMode.Cancel => EditInteraction.Cancel
          case _ => e
        }
        case e => e
      }
    }

    def shouldFocus = {
      dom.document.activeElement.tagName.toLowerCase != "input"
    }
    VDomModifier(
      onDomMount.asHtml.foreach { elem = _ },
      keyed, // when updates come in, don't disturb current editing session
      current.map {
        case EditInteraction.Input(value) =>
          lastValue = Some(value)
          valueSetter := Right(ValueStringifier[T].stringify(value))
        case EditInteraction.Cancel =>
          valueSetter := Left(lastValue.fold("")(ValueStringifier[T].stringify))
        case EditInteraction.Error(_) =>
          VDomModifier.empty
      },

      cls := "enable-text-selection", // fix for macos safari (contenteditable should already be selectable, but safari seems to have troube with interpreting `:not(input):not(textarea):not([contenteditable=true])`)
      whiteSpace.preWrap, // preserve white space in Markdown code

      onClick.stopPropagation --> Observer.empty, // prevent e.g. selecting node, but only when editing

      onDomMount.asHtml --> inNextAnimationFrame { elem => if (shouldFocus) elem.focus() },
      onDomUpdate.asHtml --> inNextAnimationFrame { elem => if (shouldFocus) elem.focus() },
      VDomModifier.ifTrue(config.selectTextOnFocus)(
        onFocus foreach { e => dom.document.execCommand("selectAll", false, null) }, // select text on focus
      ),

      config.submitMode match {
        case SubmitMode.Explicit => VDomModifier(
          fixedSubmitEvent.getOrElse(onBlur.transform(_.delayOnNext(200 millis))).transform(_.mapEval { e => // we delay the blur event, because otherwise in chrome it will trigger Before the onEscape, and we want onEscape to trigger frist.
            parse(e.target.asInstanceOf[Elem])
          }) --> current,
          BrowserDetect.isMobile.ifFalse[VDomModifier](VDomModifier(
            VDomModifier.ifTrue(fixedSubmitEvent.isEmpty)(onEnter.transform(_.mapEval(e => parse(e.target.asInstanceOf[Elem]))) --> current),
            onGlobalEscape(EditInteraction.Cancel) --> current
          )),
        )
        case SubmitMode.Emitter(builders) => VDomModifier(
          fixedSubmitEvent.fold(builders)(_ :: Nil).map(_.transform(_.mapEval(e => parse(e.target.asInstanceOf[Elem]))) --> current)
        )
      },

      config.innerModifier,

      showErrorsInside(current)
    )
  }

  private def editOrRender[T: EditInputParser: ValueStringifier](current: T, editMode: Var[Boolean], renderFn: T => VDomModifier, inputFn: Handler[EditInteraction[T]] => VDomModifier)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]](EditInteraction.Input(current))

    val editRender = inputFn(currentVar)
    VDomModifier(
      emitter(currentVar.drop(1)).map(handleEditInteractionInOrRender(editMode)) --> action,

      p( // has different line-height than div and is used for text by markdown
        Rx {
          if(editMode()) VDomModifier(editRender, keyed)
          else VDomModifier(currentVar.collect { case EditInteraction.Input(current) => renderFn(current) }.prepend(renderFn(current)), keyed)
        },
      )
    )
  }

  private def handleEditInteractionInOrRender[T](editMode: Var[Boolean]): EditInteraction[T] => EditInteraction[T] = {
    case e@EditInteraction.Cancel =>
      editMode() = false
      e
    case e@EditInteraction.Input(t) =>
      editMode() = false
      e
    case e =>
      e
  }

  private def showErrorsOutside[T](interaction: Observable[EditInteraction[T]], errorMode: ErrorMode): VDomModifier = errorMode match {
    case ErrorMode.ShowInline => interaction.map {
      case EditInteraction.Error(error) => div(
        cls := "ui pointing red basic mini label",
        error
      )
      case _ => VDomModifier.empty
    }
    case ErrorMode.ShowToast => emitter(interaction).foreach(_ match {
      case EditInteraction.Error(error) => UI.toast(error, level = UI.ToastLevel.Warning)
      case _ => ()
    })
    case _ =>  VDomModifier.empty
  }

  private def showErrorsInside[T](interaction: Observable[EditInteraction[T]]): VDomModifier = interaction.map {
    case EditInteraction.Error(error) => VDomModifier(
      boxShadow := s"0 0 1px 1px #e0b4b4",
      borderColor := "#e0b4b4",
      backgroundColor := "#fff6f6",
      color := "#9f3a38",
    )
    case _ =>
      VDomModifier.empty
  }

  private def zoomOutToEditInteraction[T](current: Var[Option[T]])(implicit ctx: Ctx.Owner): Handler[EditInteraction[T]] = {
    var lastValue: EditInteraction[T] = EditInteraction.fromOption(current.now)
    val handler = Handler.unsafe[EditInteraction[T]](lastValue)
    current.triggerLater { v =>
      val newValue =  EditInteraction.fromOption(v)
      if (newValue != lastValue) {
        lastValue = newValue
        handler.onNext(lastValue)
      }
    }

    handler.mapObserver { e =>
      if (e != lastValue) {
        lastValue = e
        current() = lastValue.toOption
      }
      e
    }
  }
}
