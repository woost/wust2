package wust.webApp.views

import monix.eval.Task
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive._
import outwatch.reactive.handler._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp._
import wust.webUtil.{Elements, UI}
import wust.webUtil.outwatchHelpers._

import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.Date
import scala.util.Try

// How to stringify a type to fit into the value/textcontent field of an html element.
trait ValueStringifier[-T] { self =>
  def stringify(current: T): String

  final def map[R](f: R => T): ValueStringifier[R] = new ValueStringifier[R] {
    def stringify(current: R) = self.stringify(f(current))
  }
}
object ValueStringifier {
  @inline def apply[T](implicit stringifier: ValueStringifier[T]): ValueStringifier[T] = stringifier

  def combine[A: ValueStringifier, B: ValueStringifier, T](f: T => Either[A, B]): ValueStringifier[T] = new ValueStringifier[T] {
    def stringify(current: T) = f(current) match {
      case Left(a) => ValueStringifier[A].stringify(a)
      case Right(b) => ValueStringifier[B].stringify(b)
    }
  }

  implicit object accesslevel extends ValueStringifier[AccessLevel] {
    def stringify(current: AccessLevel): String = current.str
  }
  implicit object nodeAccess extends ValueStringifier[NodeAccess] {
    def stringify(current: NodeAccess): String = current.str
  }

  implicit object ValueString extends ValueStringifier[String] {
    def stringify(value: String) = value
  }
  implicit val ValueNonEmptyString: ValueStringifier[NonEmptyString] = ValueString.map(_.string)
  implicit object ValueInteger extends ValueStringifier[Int] {
    def stringify(value: Int) = value.toString
  }
  implicit object ValueDouble extends ValueStringifier[Double] {
    def stringify(value: Double) = value.toString
  }
  implicit object ValueTimeMilli extends ValueStringifier[TimeMilli] {
    def stringify(value: TimeMilli) = StringJsOps.timeToTimeString(value)
  }
  implicit object ValueDateMilli extends ValueStringifier[DateMilli] {
    def stringify(value: DateMilli) = value.isoDate
  }
  implicit object ValueDurationMilli extends ValueStringifier[DurationMilli] {
    def stringify(value: DurationMilli) = StringJsOps.durationToString(value)
  }
  implicit val ValueFile: ValueStringifier[dom.File] = ValueString.map[dom.File](_.name)
  implicit val ValueUploadableFile: ValueStringifier[AWS.UploadableFile] = ValueFile.map[AWS.UploadableFile](_.file)

  implicit def ValueOption[T: ValueStringifier]: ValueStringifier[Option[T]] = new ValueStringifier[Option[T]] {
    def stringify(current: Option[T]): String = current.fold("")(ValueStringifier[T].stringify)
  }

  implicit val ValueNodeId: ValueStringifier[NodeId] = ValueString.map[NodeId](_.toCuidString)

  //TODO: remove, is not correct for compound parsers...
  implicit object ValueNodeData extends ValueStringifier[NodeData] {
    override def stringify(current: NodeData): String = current match {
      case data: NodeData.Integer      => ValueInteger.stringify(data.content)
      case data: NodeData.Decimal      => ValueDouble.stringify(data.content)
      case data: NodeData.Date         => ValueDateMilli.stringify(data.content)
      case data: NodeData.RelativeDate => ValueDurationMilli.stringify(data.content)
      case data: NodeData.Duration     => ValueDurationMilli.stringify(data.content)
      case data: NodeData.File         => data.fileName
      case data                        => ValueString.stringify(data.str)
    }
  }

  implicit val ValueNode: ValueStringifier[Node] = ValueNodeData.map(_.data)
}

trait EditStringParser[+T] { self =>
  def parse(elem: String): Task[EditInteraction[T]]

  @inline final def map[R](f: T => R): EditStringParser[R] = flatMap[R](t => EditInteraction.Input(f(t)))
  @inline final def mapEval[R](f: T => Task[R]): EditStringParser[R] = flatMapEval[R](t => f(t).map(EditInteraction.Input(_)))
  @inline final def flatMap[R](f: T => EditInteraction[R]): EditStringParser[R] = flatMapEval(t => Task.pure(f(t)))
  final def flatMapEval[R](f: T => Task[EditInteraction[R]]): EditStringParser[R] = new EditStringParser[R] {
    def parse(elem: String) = self.parse(elem).flatMap(_.toEither.fold(Task.pure, f))
  }
  final def flatMapParser[R](f: T => EditStringParser[R]): EditStringParser[R] = new EditStringParser[R] {
    def parse(elem: String) = self.parse(elem).flatMap(_.toEither.fold(Task.pure, t => f(t).parse(elem)))
  }
  @inline final def mapEditInteraction[R](f: EditInteraction[T] => R): EditStringParser[R] = flatMapEditInteraction[R](t => EditInteraction.Input(f(t)))
  final def flatMapEditInteraction[R](f: EditInteraction[T] => EditInteraction[R]): EditStringParser[R] = new EditStringParser[R] {
    override def parse(elem: String) = self.parse(elem).map(f)
  }
}
trait EditStringParserInstances0 {
  implicit object EditString extends EditStringParser[String] {
    def parse(str: String) = Task.pure(EditInteraction.Input(str))
  }
  implicit val EditNonEmptyString: EditStringParser[NonEmptyString] = EditString.flatMap(str => EditInteraction.fromEither(NonEmptyString(str).toRight("Cannot be empty")))
}
object EditStringParser extends EditStringParserInstances0 {
  @inline def apply[T](implicit parser: EditStringParser[T]): EditStringParser[T] = parser

  //TODO only allow valid node data type strings
  implicit val EditNodeDataType: EditStringParser[NodeData.Type] = EditString.map(NodeData.Type(_))
  implicit val EditNodeDataPlainText: EditStringParser[NodeData.PlainText] = EditString.map[NodeData.PlainText](NodeData.PlainText.apply)
  implicit val EditNodeDataMarkdown: EditStringParser[NodeData.Markdown] = EditString.map[NodeData.Markdown](NodeData.Markdown.apply)
  implicit def EditNodeDataUser(user: NodeData.User): EditStringParser[NodeData.User] = EditNonEmptyString.flatMap[NodeData.User](s => EditInteraction.fromOption(user.updateName(s.string)))

  implicit val EditAccessLevel: EditStringParser[AccessLevel] = new EditStringParser[AccessLevel] {
    def parse(elem: String): Task[EditInteraction[AccessLevel]] = Task(EditInteraction.fromOption(AccessLevel.fromString.lift(elem)))
  }
  implicit val EditNodeAccess: EditStringParser[NodeAccess] = new EditStringParser[NodeAccess] {
    def parse(elem: String): Task[EditInteraction[NodeAccess]] = Task(EditInteraction.fromOption(NodeAccess.fromString.lift(elem)))
  }

  implicit def EditOption[T: EditStringParser]: EditStringParser[Option[T]] = EditStringParser[T].map(Some(_))
  implicit def EditEditInteraction[T: EditStringParser]: EditStringParser[EditInteraction[T]] = EditStringParser[T].mapEditInteraction[EditInteraction[T]](identity)
  implicit def EditEither[T: EditStringParser]: EditStringParser[Either[EditInteraction.Error, T]] = EditStringParser[T].flatMapEditInteraction {
    case EditInteraction.Input(value) => EditInteraction.Input(Right(value))
    case edit: EditInteraction.Error => EditInteraction.Input(Left(edit))
    case EditInteraction.Cancel => EditInteraction.Cancel
  }

  implicit def EditNodeId: EditStringParser[NodeId] = EditString.flatMap[NodeId] { cuid =>
    EditInteraction.fromEither(Cuid.fromCuidString(cuid).map(NodeId(_)))
  }

  def forNodeDataType(tpe: NodeData.Type): Option[EditStringParser[NodeData.Content]] = tpe match {
    case NodeData.PlainText.tpe => Some(EditNodeDataPlainText)
    case NodeData.Markdown.tpe => Some(EditNodeDataMarkdown)
    case _ => None
  }
  def forNode(node: Node): Option[EditStringParser[Node]] = node match {
    case node: Node.Content => node.data match {
      case NodeData.Placeholder(Some(NodeTypeSelection.Data(dataType))) => forNodeDataType(dataType).map(_.map(data => node.copy(data = data)))
      case _ => forNodeDataType(node.data.tpe).map(_.map(data => node.copy(data = data)))
    }
    case user: Node.User => Some(EditNodeDataUser(user.data).map[Node](data => user.copy(data = data)))
  }
}

trait EditElementParser[T] { self =>
  import EditElementParser.Config

  def render(config: Config, initial: Task[Option[T]], handler: Handler[EditInteraction[T]])(implicit ctx: Ctx.Owner): VDomModifier

  def widen[R >: T](implicit tag: ClassTag[T]): EditElementParser[R] = new EditElementParser[R] {
    override def render(config: Config, initial: Task[Option[R]], handler: Handler[EditInteraction[R]])(implicit ctx: Ctx.Owner): VDomModifier = {
      val newHandler = ProHandler(
        handler,
        handler.collect {
          case EditInteraction.Cancel => EditInteraction.Cancel
          case edit: EditInteraction.Error => edit
          case EditInteraction.Input(t: T) => EditInteraction.Input(t)
        }
      )
      self.render(config, initial.map(_.collect { case t: T => t }), newHandler)
    }
  }

  @inline final def map[R](f: T => R)(g: R => T): EditElementParser[R] = flatMap[R](t => EditInteraction.Input(f(t)))(r => EditInteraction.Input(g(r)))
  @inline final def flatMap[R](f: T => EditInteraction[R])(g: R => EditInteraction[T]): EditElementParser[R] = new EditElementParser[R] {
    def render(config: Config, initial: Task[Option[R]], handler: Handler[EditInteraction[R]])(implicit ctx: Ctx.Owner) = {
      val newHandler = ProHandler(
        handler.contramap[EditInteraction[T]](_.toEither.map(f).merge),
        handler.map[EditInteraction[T]](_.toEither.map(g).merge)
      )
      self.render(config, initial.map(_.flatMap(g(_).toOption)), newHandler)
    }
  }
  @inline final def mapEval[R](f: T => Task[R])(g: R => Task[T]): EditElementParser[R] = flatMapEval[R](t => f(t).map(EditInteraction.Input(_)))(r => g(r).map(EditInteraction.Input(_)))
  final def flatMapEval[R](f: T => Task[EditInteraction[R]])(g: R => Task[EditInteraction[T]]): EditElementParser[R] = new EditElementParser[R] {
    def render(config: Config, initial: Task[Option[R]], handler: Handler[EditInteraction[R]])(implicit ctx: Ctx.Owner) = {
      //TODO: all observable methods in SourceStream, then have Observer methods without connectable?
      val connectableSink = handler.redirect[SourceStream, EditInteraction[T]](_.concatMapAsync(_.toEither.fold(Task.pure(_), f)))
      VDomModifier(managedFunction(() => connectableSink.connect()), self.render(
        config,
        initial.flatMap(_.fold[Task[Option[T]]](Task.pure(None))(g(_).map(_.toOption))),
        ProHandler(connectableSink.sink, handler.concatMapAsync(_.toEither.fold(Task.pure(_), g)))
      ))
    }
  }
  @inline final def mapEditInteraction[R](f: EditInteraction[T] => R)(g: EditInteraction[R] => EditInteraction[T]): EditElementParser[R] = flatMapEditInteraction[R](t => EditInteraction.Input(f(t)))(g)
  final def flatMapEditInteraction[R](f: EditInteraction[T] => EditInteraction[R])(g: EditInteraction[R] => EditInteraction[T]): EditElementParser[R] = new EditElementParser[R] {
    def render(config: Config, initial: Task[Option[R]], handler: Handler[EditInteraction[R]])(implicit ctx: Ctx.Owner) =
      self.render(
        config,
        initial.map(initial => g(EditInteraction.fromOption(initial)).toOption),
        ProHandler(handler.contramap(f), handler.map(g))
      )
  }
}
object EditElementParser {
  import EditHelper.renderSimpleInput

  @inline def apply[T](implicit parser: EditElementParser[T]): EditElementParser[T] = parser

  final case class Config(
    inputEmitter: EmitterBuilder[Any, VDomModifier], // emitter to be applied to an input element. but can be overwritten by element parser if not applicable. e.g. for file input only onChange/onInput makes sense.
    inputModifier: VDomModifier, // modifiers to be applied to an input element. but can be overwritten by element parser if not applicable.
    blurEmitter: EmitterBuilder[Any, VDomModifier], // emitter for blur event, if enabled, it may be used by the edit element
    emitter: EmitterBuilder[Any, VDomModifier], // mandatory emitter for any edit element. when this trigger we expect to parse and emit the current value.
    modifier: VDomModifier, // mandatory modifiers for any edit element. we expect this to be applied to the main edit element.
  )

  object Disabled extends EditElementParser[Nothing] {
    def render(config: Config, initial: Task[Option[Nothing]], handler: Handler[EditInteraction[Nothing]])(implicit ctx: Ctx.Owner) = input(disabled := true)
  }

  implicit def EditStringParsing[T: EditStringParser: ValueStringifier]: EditElementParser[T] = new EditElementParser[T] {
    def render(config: Config, initial: Task[Option[T]], handler: Handler[EditInteraction[T]])(implicit ctx: Ctx.Owner) = renderSimpleInput(
      initial, handler, EmitterBuilder.combine(config.emitter, config.inputEmitter, config.blurEmitter), VDomModifier(config.inputModifier, config.modifier, Elements.textInputMod),
      elem => EditStringParser[T].parse(elem.value)
    )
  }

  implicit object EditInteger extends EditElementParser[Int] {
    def render(config: Config, initial: Task[Option[Int]], handler: Handler[EditInteraction[Int]])(implicit ctx: Ctx.Owner) = renderSimpleInput(
      initial, handler, EmitterBuilder.combine(config.emitter, config.inputEmitter, config.blurEmitter), VDomModifier(config.inputModifier, config.modifier, Elements.integerInputMod),
      elem => Task.pure(EditInteraction.fromEither(Try(elem.valueAsNumber).toOption.collect { case num if !num.isNaN => num.toInt }.toRight("Not an Integer Number")))
    )
  }
  implicit object EditDouble extends EditElementParser[Double] {
    def render(config: Config, initial: Task[Option[Double]], handler: Handler[EditInteraction[Double]])(implicit ctx: Ctx.Owner) = renderSimpleInput(
      initial, handler, EmitterBuilder.combine(config.emitter, config.inputEmitter, config.blurEmitter), VDomModifier(config.inputModifier, config.modifier, Elements.decimalInputMod),
      elem => Task.pure(EditInteraction.fromEither(Try(elem.valueAsNumber).toOption.filterNot(_.isNaN).toRight("Not a Double Number")))
    )
  }
  implicit object EditDateMilli extends EditElementParser[DateMilli] {
    def render(config: Config, initial: Task[Option[DateMilli]], handler: Handler[EditInteraction[DateMilli]])(implicit ctx: Ctx.Owner) = {
      SourceStream.fromAsync(initial).prepend(None).map { dateTime =>
        val initialDate = dateTime.map(new js.Date(_))
        Elements.flatpickr("Select Date", withTime = false, initialDate = initialDate).foreach { date =>
          handler.onNext(EditInteraction.Input(DateMilli(EpochMilli(date.getTime.toLong))))
        }
      }
    }
  }
  implicit object EditTimeMilli extends EditElementParser[TimeMilli] {
    def render(config: Config, initial: Task[Option[TimeMilli]], handler: Handler[EditInteraction[TimeMilli]])(implicit ctx: Ctx.Owner) = renderSimpleInput(
      initial, handler, EmitterBuilder.combine(config.emitter, config.inputEmitter), VDomModifier(config.modifier, Elements.timeInputMod),
      elem => Task.pure(EditInteraction.Input(TimeMilli(StringJsOps.timeStringToTime(elem.value).getOrElse(EpochMilli.zero))))
    )
  }
  implicit object EditDurationMilli extends EditElementParser[DurationMilli] {
    def render(config: Config, initial: Task[Option[DurationMilli]], handler: Handler[EditInteraction[DurationMilli]])(implicit ctx: Ctx.Owner) = renderSimpleInput(
      initial, handler, EmitterBuilder.combine(config.emitter, onChange), VDomModifier(config.inputModifier, config.modifier, Elements.durationInputMod),
      elem => Task.pure(EditInteraction.fromEither(StringJsOps.safeToDuration(elem.value)))
    )
  }

  implicit object EditDateTimeMilli extends EditElementParser[DateTimeMilli] {

    def render(config: Config, initial: Task[Option[DateTimeMilli]], handler: Handler[EditInteraction[DateTimeMilli]])(implicit ctx: Ctx.Owner) = {

      SourceStream.fromAsync(initial).prepend(None).map { dateTime =>
        val initialDate = dateTime.map(new js.Date(_))
        Elements.flatpickr("Select Date and Time", withTime = true, initialDate = initialDate).foreach { date =>
          handler.onNext(EditInteraction.Input(DateTimeMilli(EpochMilli(date.getTime.toLong))))
        }
      }
    }
  }

  implicit def EditOption[T: EditElementParser]: EditElementParser[Option[T]] = EditElementParser[T].flatMap[Option[T]](t => EditInteraction.Input(Some(t)))(_.fold[EditInteraction[T]](EditInteraction.Cancel)(EditInteraction.Input(_)))
  implicit def EditEditInteraction[T: EditElementParser]: EditElementParser[EditInteraction[T]] = EditElementParser[T].mapEditInteraction[EditInteraction[T]](identity)(_.flatMap(identity))
  implicit def EditEither[T: EditElementParser]: EditElementParser[Either[EditInteraction.Error, T]] = EditElementParser[T].flatMapEditInteraction {
    case EditInteraction.Input(value) => EditInteraction.Input(Right(value))
    case edit: EditInteraction.Error => EditInteraction.Input(Left(edit))
    case EditInteraction.Cancel => EditInteraction.Cancel
  }(_.flatMap(_.map(EditInteraction.Input(_)).merge))

  implicit object EditFile extends EditElementParser[dom.File] {
    private def parse(elem: dom.html.Input) = {
      val file = elem.asInstanceOf[js.Dynamic].files.asInstanceOf[js.UndefOr[dom.FileList]].collect { case list if list.length > 0 => list(0) }
      EditInteraction.fromOption(file.toOption)
    }
    def render(config: Config, initial: Task[Option[dom.File]], handler: Handler[EditInteraction[dom.File]])(implicit ctx: Ctx.Owner) = {
      val randomId = scala.util.Random.nextInt.toString

      div(
        config.modifier,

        input(
          config.inputModifier,
          display.none, id := randomId, Elements.fileInputMod,
          onChange.map(e => parse(e.target.asInstanceOf[dom.html.Input])) --> handler,
          handler.map {
            case EditInteraction.Cancel => value := ""
            case _ => VDomModifier.empty
          }
        ),
        EditHelper.uploadFieldModifier(SourceStream.concatAsync(initial, handler.map(_.toOption)), randomId)
      )
    }
  }

  implicit val EditNodeDataInteger: EditElementParser[NodeData.Integer] = EditInteger.map[NodeData.Integer](NodeData.Integer.apply)(_.content)
  implicit val EditNodeDataDecimal: EditElementParser[NodeData.Decimal] = EditDouble.map[NodeData.Decimal](NodeData.Decimal.apply)(_.content)
  implicit val EditNodeDataDate: EditElementParser[NodeData.Date] = EditDateMilli.map[NodeData.Date](NodeData.Date.apply)(_.content)
  implicit val EditNodeDataDateTime: EditElementParser[NodeData.DateTime] = EditDateTimeMilli.map[NodeData.DateTime](NodeData.DateTime.apply)(_.content)
  implicit val EditNodeDataRelativeDate: EditElementParser[NodeData.RelativeDate] = EditDurationMilli.map[NodeData.RelativeDate](NodeData.RelativeDate.apply)(_.content)
  implicit val EditNodeDataDuration: EditElementParser[NodeData.Duration] = EditDurationMilli.map[NodeData.Duration](NodeData.Duration.apply)(_.content)

  implicit def EditUploadableFile: EditElementParser[AWS.UploadableFile] = EditFile.flatMap(file => EditInteraction.fromEither(AWS.upload(file)))(aws => EditInteraction.Input(aws.file))
  implicit def EditNodeDataFile: EditElementParser[NodeData.File] = EditUploadableFile.flatMapEval(aws => AWS.uploadFileAndCreateNodeData(aws).map(EditInteraction.Input(_)))(_ => Task.pure(EditInteraction.Cancel))

  //TODO: FIX! as instance of buillshit. one parser for node.
  def forNodeDataType(tpe: NodeData.Type): Option[EditElementParser[NodeData.Content]] = EditStringParser.forNodeDataType(tpe).map(EditStringParsing[NodeData.Content](_, ValueStringifier.ValueNodeData)).orElse(tpe match {
    case NodeData.Integer.tpe => Some(EditNodeDataInteger.widen[NodeData.Content])
    case NodeData.Decimal.tpe => Some(EditNodeDataDecimal.widen[NodeData.Content])
    case NodeData.Date.tpe => Some(EditNodeDataDate.widen[NodeData.Content])
    case NodeData.DateTime.tpe => Some(EditNodeDataDateTime.widen[NodeData.Content])
    case NodeData.RelativeDate.tpe => Some(EditNodeDataRelativeDate.widen[NodeData.Content])
    case NodeData.Duration.tpe => Some(EditNodeDataDuration.widen[NodeData.Content])
    case NodeData.File.tpe => Some(EditNodeDataFile.widen[NodeData.Content])
    case _ => None
  })
  def forNode(node: Node): Option[EditElementParser[Node]] = EditStringParser.forNode(node).map(EditStringParsing[Node](_, ValueStringifier.ValueNode)).orElse(node match {
    case node: Node.Content => node.data match {
      case NodeData.Placeholder(Some(NodeTypeSelection.Data(dataType))) => forNodeDataType(dataType).map(_.map(data => node.copy(data = data))(_.data).widen[Node])
      case _ => forNodeDataType(node.data.tpe).map(_.map(data => node.copy(data = data))(_.data).widen[Node])
    }
    case _ => None
  })
}

sealed trait EditInteraction[+T] {
  @inline def foreach[U](f: T => U): Unit = map(f)
  @inline def map[R](f: T => R): EditInteraction[R] = flatMap[R](t => EditInteraction.Input(f(t)))
  @inline def flatMap[R](f: T => EditInteraction[R]): EditInteraction[R]
  @inline def toEither: Either[EditInteraction.WithoutValue, T]
  @inline def toOption: Option[T]
}
object EditInteraction {
  sealed trait WithoutValue extends EditInteraction[Nothing] {
    @inline def flatMap[R](f: Nothing => EditInteraction[R]) = this
    @inline def toEither = Left(this)
    @inline def toOption = None
  }
  case object Cancel extends WithoutValue
  final case class Error(msg: String) extends WithoutValue
  final case class Input[T](value: T) extends EditInteraction[T] {
    @inline def flatMap[R](f: T => EditInteraction[R]) = f(value)
    @inline def toEither = Right(value)
    @inline def toOption = Some(value)
  }

  def fromEither[T](either: Either[String, T]): EditInteraction[T] = either match {
    case Right(value) => EditInteraction.Input(value)
    case Left(error) => EditInteraction.Error(error)
  }
  def fromOption[T](option: Option[T]): EditInteraction[T] = option match {
    case Some(value) => EditInteraction.Input(value)
    case None => EditInteraction.Cancel
  }

  implicit class RichEmitterBuilderEditInteraction[T,R](val builder: EmitterBuilder[EditInteraction[T],R]) extends AnyVal {
    def editValue: EmitterBuilder[T, R] = builder.collect {
      case EditInteraction.Input(value) => value
    }
    def editValueOption: EmitterBuilder[Option[T], R] = builder.collect {
      case EditInteraction.Input(value) => Some(value)
      case EditInteraction.Error(_) => None
    }
  }
}

object EditHelper {

  def uploadFieldModifier(selected: SourceStream[Option[dom.File]], fileInputId: String, tooltipDirection: String = "top left")(implicit ctx: Ctx.Owner): VDomModifier = {

    val iconAndPopup: SourceStream[(VNode, Option[VNode])] = selected.map {
      case None =>
        (span(Icons.fileUpload), None)
      case Some(file) =>
        val popupNode = file.`type` match {
          case t if t.startsWith("image/") =>
            val dataUrl = dom.URL.createObjectURL(file)
            img(src := dataUrl, height := "100px", maxWidth := "400px") //TODO: proper scaling and size restriction
          case _ => div(file.name)
        }
        val icon = VDomModifier(
          Icons.fileUpload,
          color := "orange",
        )

        (span(icon), Some(popupNode))
    }

    val onDragOverModifier = Handler.unsafe[VDomModifier]

    VDomModifier(
      label(
        forId := fileInputId, // label for input will trigger input element on click.

        iconAndPopup.map {
          case (icon, popup) =>
            VDomModifier(
              popup.map(UI.popupHtml(tooltipDirection) := _),
              div(icon, cls := "icon")
            )
        },
        cls := "ui circular basic icon button",
        fontSize := "1.1em", // same size as submit-button in Chat/InputRow
      ),

      onDragOverModifier,
      onDragEnter.preventDefault.use(opacity := 0.5) --> onDragOverModifier,
      onDragLeave.preventDefault.onlyOwnEvents.use(VDomModifier.empty) --> onDragOverModifier,
      onDragOver.preventDefault.discard,

      onDrop.preventDefault.foreach { ev =>
        val elem = dom.window.document.getElementById(fileInputId).asInstanceOf[dom.html.Input]
        elem.files = ev.dataTransfer.files
      },
    )
  }


  def valueParsingModifier[T: ValueStringifier, Elem >: Null <: dom.html.Element](
    initial: Task[Option[T]],
    handler: Handler[EditInteraction[T]],
    inputEmitter: EmitterBuilder[Any, VDomModifier],
    valueSetter: String => VDomModifier,
    valueGetter: Elem => String,
    parse: Elem => Task[EditInteraction[T]])(implicit ctx: Ctx.Owner): VDomModifier = {
    var ownValueParsed: Option[T] = None
    val valueHandler = Var[String]("")
    var elem: Elem = null
    initial.runToFuture.foreach { initial => //TODO meh?
      val str = initial.fold("")(ValueStringifier[T].stringify)
      valueHandler() = str
    }

    VDomModifier(
      onDomMount.foreach { e => elem = e.asInstanceOf[Elem] },

      emitter(handler).foreach({
        case EditInteraction.Input(v) if ownValueParsed.forall(_ != v) =>
          valueHandler() = ValueStringifier[T].stringify(v)
        case _ =>
      }: EditInteraction[T] => Unit),

      inputEmitter.transform(_.concatMapAsync { _ =>
        val str = valueGetter(elem)
        valueHandler() = str
        parse(elem).map {
          case e@EditInteraction.Input(v) =>
            ownValueParsed = Some(v)
            e
          case e => e
        }
      }) --> handler,

      valueHandler.map(valueSetter)
    )
  }

  def renderSimpleInput[T: ValueStringifier](
    initial: Task[Option[T]],
    handler: Handler[EditInteraction[T]],
    inputEmitter: EmitterBuilder[Any, VDomModifier],
    inputModifier: VDomModifier,
    parse: dom.html.Input => Task[EditInteraction[T]])(implicit ctx: Ctx.Owner): VDomModifier = {
    input(
      inputModifier,
      EditHelper.valueParsingModifier[T, dom.html.Input](initial, handler, inputEmitter, value := _, _.value, parse)
    )
  }
}

// Get EditStringParser and ValueStringifier instances from/to json. It uses circe decoders. Should be explicitly imported.
object EditImplicits {
  import io.circe._
  import io.circe.parser._
  import io.circe.syntax._

  object circe {
    implicit def StringParser[T : Decoder]: EditStringParser[T] = new EditStringParser[T] {
      def parse(str: String) = Task.pure(EditInteraction.fromEither(decode[T](str).toOption.toRight("Cannot parse")))
    }
    implicit def Stringifier[T : Encoder]: ValueStringifier[T] = new ValueStringifier[T] {
      def stringify(current: T) = current.asJson.noSpaces
    }
  }

}
