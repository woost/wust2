package wust.webApp.views

import monix.eval.Task
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl.{emitter, _}
import outwatch.ext.monix._
import outwatch.reactive._
import outwatch.reactive.handler._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, UI}
import wust.graph._
import wust.webApp.state.GlobalState
import wust.css.{Styles, ZIndex}

import scala.concurrent.duration._
import scala.scalajs.js

object EditableContent {
  private val currentlyEditingSubject = SinkSourceHandler.publish[Boolean]
  def currentlyEditing: SourceStream[Boolean] = currentlyEditingSubject

  sealed trait Position
  object Position {
    case object Top extends Position
    case object Bottom extends Position
    case object Right extends Position
    case object Left extends Position
  }

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
    case object Manual extends SubmitMode

    // automatic submit mode is special in that it is built for edit fields without any submit button.
    // we only emit a new value (input or error) when onBlur or onEnter were triggered. If the value has
    // not changed, it will emit cancel. It will additionally emit cancel when escape is pressed.
    // furthermore a small x-button is embedded into the ui.
    case object Automatic extends SubmitMode
  }

  final case class Config(
    modifier: VDomModifier = VDomModifier.empty,
    inputModifier: VDomModifier = VDomModifier.empty,
    submitMode: SubmitMode = SubmitMode.Automatic,
    emitter: EmitterBuilder[dom.Event, VDomModifier] = EmitterBuilder.empty,
    submitOnEnter: Boolean = !BrowserDetect.isMobile,
    submitOnBlur: Boolean = true,
    errorMode: ErrorMode = ErrorMode.ShowInline,
    selectTextOnFocus: Boolean = true,
    autoFocus: Boolean = true,
    autoresizeTextarea: Boolean = true,
    saveDialogPosition: Position = Position.Top
  )
  object Config {
    def default = Config()
    def cancelOnError = Config(
      errorMode = ErrorMode.Cancel
    )
    def manual = Config(
      submitMode = SubmitMode.Manual
    )
    def off = Config(
      submitMode = SubmitMode.Manual,
      errorMode = ErrorMode.Ignore
    )
  }

  //TODO elegant wrap in form to check reportvalidity on inputfield constraints?

  @inline def editor[T: EditElementParser](implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VNode] = editor[T](Config.default)
  def editor[T: EditElementParser](config: Config)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VNode] = EmitterBuilder.ofNode { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]]
    editorHandler(None, currentVar, config).apply(emitter(currentVar) --> action)
  }
  def editor[T: EditElementParser](current: T, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VNode] = EmitterBuilder.ofNode { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]]
    editorHandler(Some(current), currentVar, config).apply(emitter(currentVar) --> action)
  }
  def editorRx[T: EditElementParser](current: Var[Option[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VNode = {
    val currentVar = zoomOutToEditInteraction(current)
    editorHandler[T](current.now, currentVar, config)
  }
  private def editorHandler[T: EditElementParser](initial: Option[T], current: Handler[EditInteraction[T]], config: Config, handle: EditInteraction[T] => EditInteraction[T] = (x: EditInteraction[T]) => x)(implicit ctx: Ctx.Owner): VNode = {
    commonEditStructure(initial, current, config, handle)(handler => VDomModifier(
      EditElementParser[T].render(EditElementParser.Config(
        inputEmitter = inputEmitter(config),
        inputModifier = inputModifiers(config, handler.edit),
        blurEmitter = blurEmitter(config),
        emitter = emitter(handler.save),
        modifier = basicModifiers(config, handler.edit),
      ), Task.pure(initial), handler.edit)
    ))
  }

  @inline def inlineEditor[T: EditStringParser: ValueStringifier](implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VNode] = inlineEditor[T](Config.default)
  def inlineEditor[T: EditStringParser: ValueStringifier](config: Config)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VNode] = EmitterBuilder.ofNode { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]]
    inlineEditorHandler[T](None, currentVar, config).apply(emitter(currentVar) --> action)
  }
  def inlineEditor[T: EditStringParser: ValueStringifier](current: T, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VNode] = EmitterBuilder.ofNode { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]]
    inlineEditorHandler[T](Some(current), currentVar, config).apply(emitter(currentVar.drop(1)) --> action)
  }
  def inlineEditorRx[T: EditStringParser: ValueStringifier](current: Var[Option[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VNode = {
    val currentVar = zoomOutToEditInteraction(current)
    inlineEditorHandler[T](current.now, currentVar, config)
  }
  private def inlineEditorHandler[T: EditStringParser: ValueStringifier](initial: Option[T], current: Handler[EditInteraction[T]], config: Config, handle: EditInteraction[T] => EditInteraction[T] = (x: EditInteraction[T]) => x)(implicit ctx: Ctx.Owner): VNode = {
    commonEditStructure(initial, current, config, handle)(handler => textArea(
      rows := 1,
      VDomModifier.ifTrue(config.autoresizeTextarea)((new TextAreaAutoResizer).modifiers),
      outline := "none", // hides textarea outline
      border := "0", // hides textarea border
      minWidth := "36px", minHeight := "36px", // minimal edit area
      lineHeight := "1.4285em", // like semantic UI <p>

      basicModifiers(config, handler.edit),
      inputModifiers(config, handler.edit),

      EditHelper.valueParsingModifier[T, dom.html.TextArea](Task.pure(initial), handler.edit, EmitterBuilder.combine(emitter(handler.save), inputEmitter(config), blurEmitter(config)), identity, _.value, e => EditStringParser[T].parse(e.value)),
    ))
  }

  def editorOrRender[T: EditElementParser: ValueStringifier](current: T, editMode: Var[Boolean], renderFn: Ctx.Owner => T => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = editOrRender[T](current, editMode, renderFn, implicit ctx => editorHandler(Some(current), _, config, handle = handleEditInteractionInOrRender[T](editMode)))
  def inlineEditorOrRender[T: EditStringParser: ValueStringifier](current: T, editMode: Var[Boolean], renderFn: Ctx.Owner => T => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = editOrRender[T](current, editMode, renderFn, implicit ctx => inlineEditorHandler(Some(current), _, config, handle = handleEditInteractionInOrRender[T](editMode)))
  def customOrRender[T](current: T, editMode: Var[Boolean], renderFn: Ctx.Owner => T => VDomModifier, inputFn: Ctx.Owner => CommonEditHandler[T] => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = editOrRender[T](current, editMode, renderFn, implicit ctx => commonEditStructure(Some(current), _, config, handle = handleEditInteractionInOrRender[T](editMode))(inputFn(ctx)))

  def custom[T](current: T, inputFn: Ctx.Owner => CommonEditHandler[T] => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier[EditInteraction[T]] { sink =>
    val currentVar = Handler.unsafe[EditInteraction[T]](EditInteraction.Input(current))

    commonEditStructure[T](Some(current), currentVar, config, handle = e => e)(handler =>
      VDomModifier(inputFn(ctx)(handler), managedFunction(() => currentVar.subscribe(sink)))
    )
  }

  def ofNode(node: Node, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[Node], VDomModifier] = EmitterBuilder.ofModifier[EditInteraction[Node]] { action =>
    EditStringParser.forNode(node).map { implicit parser =>
      inlineEditor[Node](node, config) --> action
    } orElse EditElementParser.forNode(node).map { implicit parser =>
      editor[Node](node, config) --> action
    }
  }

  def ofNodeOrRender(node: Node, editMode: Var[Boolean], renderFn: Ctx.Owner => Node => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[Node], VDomModifier] = EmitterBuilder.ofModifier[EditInteraction[Node]] { action =>
    EditStringParser.forNode(node).map { implicit parser =>
      inlineEditorOrRender[Node](node, editMode, renderFn, config) --> action
    } orElse EditElementParser.forNode(node).map { implicit parser =>
      editorOrRender[Node](node, editMode, renderFn, config) --> action
    }
  }

  def selectEmitter[T: EditStringParser: ValueStringifier](header: Option[String], activeElement: Option[T], elements: Seq[(String, T)], unselectableMapping: Map[T, T] = Map.empty[T,T])(implicit ctx: Ctx.Owner): EmitterBuilder[T, VNode] = EmitterBuilder { sink =>
    val variable = Var(activeElement)
    variable.triggerLater(_.foreach(sink.onNext(_)))

    select[T](header, variable, elements, unselectableMapping)
  }
  def select[T: EditStringParser: ValueStringifier](header: Option[String], activeElement: Var[Option[T]], elements: Seq[(String, T)], unselectableMapping: Map[T, T] = Map.empty[T,T])(implicit ctx: Ctx.Owner): VNode = {
    dsl.select(
      padding := "0.2em",
      header.map { header =>
        option(
          value := "", header,
          selected <-- activeElement.map(_.isEmpty),
          disabled,
        )
      },
      elements.map { case (title, element) =>
        option(value := ValueStringifier[T].stringify(element), title, selected <-- activeElement.map(e => e.contains(element) || e.flatMap(unselectableMapping.get).contains(element))),
      },
      onInput
        .map(e => stringFromSelect(e.currentTarget.asInstanceOf[dom.html.Select]))
        .concatMapAsync(str => EditStringParser[T].parse(str)).editValueOption --> activeElement,
    )
  }

  @inline private def stringFromSelect(element: dom.html.Select): String = element.value

  private def cancelButton(current: SinkObserver[EditInteraction[Nothing]]) = dsl.span(
    "Cancel",
    cls := "ui button compact mini",
    padding := "5px",
    margin := "1px",
    flexShrink := 0,
    fontSize.xSmall,
    styleAttr := "cursor: pointer !important", // overwrite style from semantic ui with important
    onClick.stopPropagation.use(EditInteraction.Cancel) --> current
  )
  private def saveButton(current: SinkObserver[Unit]) = dsl.span(
    "Save",
    cls := "ui button primary compact mini",
    padding := "5px",
    margin := "1px",
    flexShrink := 0,
    fontSize.xSmall,
    styleAttr := "cursor: pointer !important", // overwrite style from semantic ui with important
    onClick.stopPropagation.use(()) --> current
  )

  final case class CommonEditHandler[T](edit: Handler[EditInteraction[T]], save: SourceStream[Unit])
  private def commonEditStructure[T](initial: Option[T], current: Handler[EditInteraction[T]], config: Config, handle: EditInteraction[T] => EditInteraction[T])(modifier: CommonEditHandler[T] => VDomModifier) = {
    val handledCurrent = ProHandler(
      current.contramap[EditInteraction[T]](handleEditInteraction[T](initial, config) andThen handle),
      current.filter(uniqueEditInteraction[T](initial)).shareWithLatest
    )

    val saveHandler = Handler.publish.unsafe[Unit]

    dsl.span(
      display.inlineFlex,
      flexDirection.column,
      alignItems.center,
      width := "100%",
      dsl.span(
        display.inlineFlex,
        alignItems.flexStart,
        width := "100%",
        modifier(CommonEditHandler(handledCurrent, saveHandler)),
        config.submitMode match {
          case SubmitMode.Automatic => div(
            position.absolute,
            padding := "2px",
            config.saveDialogPosition match { // hopefully always correct
              case Position.Top => VDomModifier(marginTop := "-30px", right := "4px")
              case Position.Bottom => VDomModifier(bottom := "-30px", right := "4px")
              case Position.Right => VDomModifier(right := "-100px")
              case Position.Left => VDomModifier(left := "-100px")

            },
            backgroundColor := "rgba(255, 255, 255, 0.75)",
            boxShadow := "0px 0px 3px 0px rgba(0, 0, 0, 0.75)",
            borderRadius := "3px",
            zIndex := ZIndex.overlay,
            cancelButton(handledCurrent).apply(marginRight := "6px"),
            saveButton(saveHandler)
          )
          case _ => VDomModifier.empty
        }
      ),

      config.submitMode match {
        case SubmitMode.Automatic => VDomModifier.ifNot(BrowserDetect.isMobile)(onGlobalEscape.use(EditInteraction.Cancel) -->[SinkObserver] handledCurrent)
        case _ => VDomModifier.empty
      },

      showErrorsOutside(handledCurrent, config.errorMode)
    )
  }

  private def editOrRender[T](current: T, editMode: Var[Boolean], renderFn: Ctx.Owner => T => VDomModifier, inputFn: Ctx.Owner => Handler[EditInteraction[T]] => VDomModifier)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val currentVar = Handler.unsafe[EditInteraction[T]]

    VDomModifier(
      emitter(currentVar) --> action,

      Rx {
        //components are keyed, becasue otherwise setting editMode to false does not reliably cancel editRender (happens in table with search-and-select of reference node)
        if(editMode()) VDomModifier(
          keyed,
          inputFn(ctx)(currentVar),
          onDomMount.use(true) --> currentlyEditingSubject,
          onDomUnmount.use(false) --> currentlyEditingSubject,
        )
        else VDomModifier(
          keyed,
          currentVar.collect { case EditInteraction.Input(current) => renderFn(ctx)(current) }.prepend(renderFn(ctx)(current)),
        )
      },
    )
  }

  private def uniqueEditInteraction[T](initial: Option[T]): EditInteraction[T] => Boolean = {
    var lastValue = initial

    {
      case EditInteraction.Input(value) =>
        val alreadyExists = lastValue.contains(value)
        lastValue = Some(value)
        !alreadyExists
      case _ =>
        lastValue = None
        true
    }
  }

  private def handleEditInteraction[T](initial: Option[T], config: Config): EditInteraction[T] => EditInteraction[T] = {
    var lastValue = initial

    (({
      case EditInteraction.Input(value) if config.submitMode == SubmitMode.Automatic && lastValue.contains(value) =>
        EditInteraction.Cancel
      case _: EditInteraction.Error if config.errorMode == ErrorMode.Cancel =>
        EditInteraction.Cancel
      case e@EditInteraction.Input(value) =>
        lastValue = Some(value)
        e
      case e => e
    }):(EditInteraction[T] => EditInteraction[T])) andThen {
      case e@EditInteraction.Cancel =>
        // if the transformation resulted in `Cancel`, clear all text selections
        dom.window.getSelection().removeAllRanges()
        e
      case e => e
    }
  }

  private def handleEditInteractionInOrRender[T](editMode: Var[Boolean]): EditInteraction[T] => EditInteraction[T] = {
    case e@EditInteraction.Cancel =>
      editMode() = false
      e
    case e@EditInteraction.Input(t) =>
      editMode() = false
      e
    case e => e
  }

  private def showErrorsOutside[T](interaction: SourceStream[EditInteraction[T]], errorMode: ErrorMode): VDomModifier = errorMode match {
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

  private def showErrorsInside[T](interaction: SourceStream[EditInteraction[T]]): VDomModifier = interaction.map {
    case EditInteraction.Error(error) => VDomModifier(
      boxShadow := s"0 0 1px 1px #e0b4b4",
      borderColor := "#e0b4b4",
      backgroundColor := "#fff6f6",
      color := "#9f3a38",
    )
    case _ => VDomModifier.empty
  }

  private def zoomOutToEditInteraction[T](current: Var[Option[T]])(implicit ctx: Ctx.Owner): Handler[EditInteraction[T]] = {
    var lastValue: EditInteraction[T] = EditInteraction.fromOption(current.now)
    val handler = Handler.unsafe[EditInteraction[T]]
    current.triggerLater { v =>
      val newValue =  EditInteraction.fromOption(v)
      if (newValue != lastValue) {
        lastValue = newValue
        handler.onNext(lastValue)
      }
    }

    ProHandler(
      handler.contramap[EditInteraction[T]] { e =>
        if (e != lastValue) {
          lastValue = e
          current() = lastValue.toOption
        }
        e
      },
      handler
    )
  }

  //TODO: bad heuristic...
  private def shouldFocusInput= dom.document.activeElement.tagName.toLowerCase != "input"

  private def basicModifiers[T](config: Config, handler: Handler[EditInteraction[T]]) = VDomModifier(
    width := "100%",
    style("user-select") := "text", // fix for macos safari (contenteditable should already be selectable, but safari seems to have troube with interpreting `:not(input):not(textarea):not([contenteditable=true])`)
    config.modifier,
    showErrorsInside(handler)
  )

  private def inputModifiers[T](config: Config, handler: Handler[EditInteraction[T]]) = VDomModifier(
    config.inputModifier,
    VDomModifier.ifTrue(config.selectTextOnFocus)(
      onFocus foreach { e => dom.document.execCommand("selectAll", false, null) }, // select text on focus
    ),
    whiteSpace.preWrap, // preserve white space in Markdown code
    onClick.stopPropagation.discard, // prevent e.g. selecting node, but only when editing
    VDomModifier.ifTrue (config.autoFocus)(
      onDomMount.asHtml --> inNextAnimationFrame[dom.html.Element] { elem => if (shouldFocusInput) elem.focus() },
      onDomUpdate.asHtml --> inNextAnimationFrame[dom.html.Element] { elem => if (shouldFocusInput) elem.focus() },
    )
  )

  private def blurEmitter(config: Config): EmitterBuilder[Any, VDomModifier] = {
    config.submitMode match {
      case SubmitMode.Automatic if config.submitOnBlur => onBlur.transform(_.delay(200 millis))
      case _ => EmitterBuilder.empty
    }
  }

  private def inputEmitter(config: Config): EmitterBuilder[Any, VDomModifier] = {
    val emitters = config.submitMode match {
      case SubmitMode.Automatic => if (config.submitOnEnter) List(onEnter) else List(onCtrlEnter)
      case _ => Nil
    }

    EmitterBuilder.combineSeq(config.emitter :: emitters)
  }
}
