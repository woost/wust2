package wust.webApp.views

import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
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
    case object Show extends ErrorMode
    case object Ignore extends ErrorMode
  }

  sealed trait SubmitMode
  object SubmitMode {
    case class Emitter(builder: List[EmitterBuilder[dom.Event, VDomModifier]]) extends SubmitMode
    def OnChange = Emitter(dsl.onChange :: Nil)
    def OnInput = Emitter(dsl.onInput :: Nil)
    def OnEnter = Emitter(Elements.onEnter :: Nil)
    def Off = Emitter(Nil)
    case object Explicit extends SubmitMode
  }

  case class Config(
    inputModifier: VDomModifier = VDomModifier.empty,
    submitMode: SubmitMode = SubmitMode.Explicit,
    errorMode: ErrorMode = ErrorMode.Show,
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

  @inline def inputField[T: EditParser]: EmitterBuilder[EditInteraction[T], VDomModifier] = inputField[T](Config.default)
  def inputField[T: EditParser](config: Config): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier{ action =>
    val subject = PublishSubject[EditInteraction[T]]
    div(
      width := "100%",
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      input(
        onDomMount.asHtml --> inNextAnimationFrame { elem => elem.focus() },
        onDomUpdate.asHtml --> inNextAnimationFrame { elem => elem.focus() },

        emitter(subject) --> action,
        Some(config.errorMode).collect { case ErrorMode.Show => subject.map(showErrorsInside(_)) },

        commonEditMods(config, subject),
      ),

      Some(config.errorMode).collect { case ErrorMode.Show => subject.map(showErrorsOutside(_)) },
    )
  }

  def inputField[T: EditParser: ValueStringifier](current: T, config: Config = Config.default): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val subject = PublishSubject[EditInteraction[T]]

    val newConfig = config.copy(
      inputModifier = VDomModifier(
        config.inputModifier,
        subject.prepend(EditInteraction.Input(current)).scan("")(foldUserValue).map(value := _)
      )
    )

    VDomModifier(
      emitter(subject) --> action,
      inputField(newConfig)(distinctParser(EditInteraction.Input(current))) --> subject,
    )
  }

  @inline def inputFieldRx[T: EditParser: ValueStringifier](current: Var[Option[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = inputFieldRxInteraction[T](zoomOutToEditInteraction(current), config)
  def inputFieldRxInteraction[T: EditParser: ValueStringifier](current: Var[EditInteraction[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = {
    val newConfig = config.copy(
      inputModifier = VDomModifier(
        config.inputModifier,
        current.fold("")(foldUserValue).map(value := _)
      )
    )

    inputField[T](newConfig)(distinctParser(current.now)) --> current,
  }

  @inline def inputInline[T: EditParser]: EmitterBuilder[EditInteraction[T], VDomModifier] = inputInline[T](Config.default)
  def inputInline[T: EditParser](config: Config): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier{ action =>
    val subject = PublishSubject[EditInteraction[T]]
    VDomModifier(
      onDomMount.asHtml --> inNextAnimationFrame { elem => elem.focus() },
      onDomUpdate.asHtml --> inNextAnimationFrame { elem => elem.focus() },
      contentEditable := true,
      backgroundColor := "#FFF",
      color := "#000",
      cursor.auto,

      emitter(subject) --> action,
      Some(config.errorMode).collect { case ErrorMode.Show => subject.map(showErrorsInside(_)) },

      commonEditMods(config, subject), // innerText because textContent would remove line-breaks in firefox
    )
  }

  def inputInline[T: EditParser: ValueStringifier](current: T, config: Config = Config.default): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val subject = PublishSubject[EditInteraction[T]]

    val newConfig = config.copy(
      inputModifier = VDomModifier(
        config.inputModifier,
        subject.prepend(EditInteraction.Input(current)).scan("")(foldUserValue)
      )
    )

    VDomModifier(
      emitter(subject) --> action,
      inputInline[T](newConfig)(distinctParser(EditInteraction.Input(current))) --> subject
    )
  }

  @inline def inputInlineRx[T: EditParser: ValueStringifier](current: Var[Option[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = inputInlineRxInteraction[T](zoomOutToEditInteraction(current), config)
  def inputInlineRxInteraction[T: EditParser: ValueStringifier](current: Var[EditInteraction[T]], config: Config = Config.default)(implicit ctx: Ctx.Owner): VDomModifier = {
    val newConfig = config.copy(
      inputModifier = VDomModifier(
        config.inputModifier,
        current.fold("")(foldUserValue)
      )
    )

    inputInline[T](newConfig)(distinctParser(current.now)) --> current,
  }

  def inputFieldOrRender[T: EditParser : ValueStringifier](current: T, editMode: Var[Boolean], renderFn: T => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val initialElement: Var[T] = Var(current)

    val editRender = inputField[T](current, config) --> action.redirectMap(handleEditInteractionInOrRender(editMode, initialElement))
    editOrRender(editMode, initialElement.map(renderFn), editRender)
  }

  def inputInlineOrRender[T: EditParser : ValueStringifier](current: T, editMode: Var[Boolean], renderFn: T => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[T], VDomModifier] = EmitterBuilder.ofModifier { action =>
    val initialElement: Var[T] = Var(current)

    val editRender = inputInline[T](current, config) --> action.redirectMap(handleEditInteractionInOrRender(editMode, initialElement))
    editOrRender(editMode, initialElement.map(renderFn), editRender)
  }

  def ofNode(node: Node, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[Node], VDomModifier] = EmitterBuilder.ofModifier[EditInteraction[Node]] { action =>

    EditParser.forNode(node).map { implicit editParser =>
      node.data match {
        case _: NodeData.EditableText => inputInline[Node](node, config) --> action
        case _ => inputField[Node](node) --> action
      }
    }
  }

  def ofNodeOrRender(node: Node, editMode: Var[Boolean], renderFn: Node => VDomModifier, config: Config = Config.default)(implicit ctx: Ctx.Owner): EmitterBuilder[EditInteraction[Node], VDomModifier] = EmitterBuilder.ofModifier[EditInteraction[Node]] { action =>

    EditParser.forNode(node).fold[VDomModifier](renderFn(node)) { implicit editParser =>
      node.data match {
        case _: NodeData.EditableText | _: NodeData.User => inputInlineOrRender[Node](node, editMode, renderFn, config) --> action
        case _ => inputFieldOrRender[Node](node, editMode, renderFn, config) --> action
      }
    }
  }

  private def commonEditMods[T: EditParser](config: Config, rawAction: Observer[EditInteraction[T]]) = {
    var lastValue: EditInteraction[T] = null
    val action = rawAction.redirectMap[EditInteraction[T]] { e =>
      val next = if (lastValue == e) EditInteraction.Cancel else e
      lastValue = e
      handleEditInteraction[T](config)(next)
    }
    VDomModifier(
      cls := "enable-text-selection", // fix for macos safari (contenteditable should already be selectable, but safari seems to have troube with interpreting `:not(input):not(textarea):not([contenteditable=true])`)
      whiteSpace.preWrap, // preserve white space in Markdown code

      EditParser[T].inputModifier,

      onClick.stopPropagation --> Observer.empty, // prevent e.g. selecting node, but only when editing

      VDomModifier.ifTrue(config.selectTextOnFocus)(
        onFocus foreach { e => dom.document.execCommand("selectAll", false, null) }, // select text on focus
      ),

      config.submitMode match {
        case SubmitMode.Explicit => VDomModifier(
          onBlur.transform(_.delayOnNext(200 millis)).map { e => // we delay the blur event, because otherwise in chrome it will trigger Before the onEscape, and we want onEscape to trigger frist.
            EditParser[T].parse(e.target.asInstanceOf[dom.Element])
          } --> action,
          BrowserDetect.isMobile.ifFalse[VDomModifier](VDomModifier(
            onEnter.map(e => EditParser[T].parse(e.target.asInstanceOf[dom.Element])) --> action,
            onEscape(EditInteraction.Cancel) --> action
            //TODO how to revert back if you wrongly edited something on mobile?
          )),
        )
        case SubmitMode.Emitter(builders) => VDomModifier(
          builders.map(_.map(e => EditParser[T].parse(e.target.asInstanceOf[dom.Element])) --> action)
        )

      },

      config.inputModifier,
    )
  }

  private def editOrRender(editMode: Var[Boolean], initialRender: Rx[VDomModifier], editRender: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {

    p( // has different line-height than div and is used for text by markdown
      outline := "none", // hides contenteditable outline
      keyed, // when updates come in, don't disturb current editing session
      Rx {
        if(editMode()) VDomModifier(keyed, editRender) else VDomModifier(keyed, initialRender())
      },
    )
  }

  private def handleEditInteraction[T](config: Config): EditInteraction[T] => EditInteraction[T] = {
    case e@EditInteraction.Error(origin, error) =>
      scribe.info(s"Edit Parser failed on value '$origin': $error")
      config.errorMode match {
        case ErrorMode.Cancel => EditInteraction.Cancel
        case _ => e
      }
    case e => e
  }

  private def handleEditInteractionInOrRender[T](editMode: Var[Boolean], initialElement: Var[T]): EditInteraction[T] => EditInteraction[T] = {
    case e@EditInteraction.Cancel =>
      editMode() = false
      e
    case e@EditInteraction.Input(t) =>
      Var.set(
        editMode -> false,
        initialElement -> t
      )
      e

    case e => e
  }

  private def showErrorsOutside[T](interaction: EditInteraction[T]): VDomModifier = {
    interaction match {
      case EditInteraction.Error(_, error) => div(
        cls := "ui pointing red basic mini label",
        error
      )
      case _ =>
        VDomModifier.empty
    }
  }

  private def showErrorsInside[T](interaction: EditInteraction[T]): VDomModifier = {
    interaction match {
      case EditInteraction.Error(_, error) => VDomModifier(
        boxShadow := s"0 0 1px 1px #e0b4b4",
        borderColor := "#e0b4b4",
        backgroundColor := "#fff6f6",
        color := "#9f3a38",
      )
      case _ =>
        VDomModifier.empty
    }
  }

  private def zoomOutToEditInteraction[T](current: Var[Option[T]])(implicit ctx: Ctx.Owner): Var[EditInteraction[T]] = {
    current.zoom[EditInteraction[T]]((value: Option[T]) => EditInteraction.fromOption(value)) {
      case (_, EditInteraction.Input(value)) => Some(value)
      case (_, EditInteraction.Error(_, _)) => None
      case (value, _) => value
    }
  }

  private def distinctParser[T: EditParser](current: => EditInteraction[T]): EditParser[T] = {
    EditParser[T].flatMap { t =>
      val isDistinct = current.fold(true)(_ != t)
      EditInteraction.fromOption(if (isDistinct) Some(t) else None)
    }
  }

  private def foldUserValue[T: ValueStringifier](prev: String, current: EditInteraction[T]): String = current match {
    case EditInteraction.Input(value) => ValueStringifier[T].stringify(value)
    case EditInteraction.Error(origin, _) => origin
    case _ => prev
  }
}
