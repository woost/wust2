package wust.webApp.views

import monix.eval.Task
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
import wust.api.ApiEvent
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Elements._

import scala.concurrent.duration._
import scala.scalajs.js
import scala.util.Try

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
  implicit object ValueEpochMilli extends ValueStringifier[EpochMilli] {
    def stringify(value: EpochMilli) = value.isoDate
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

  implicit object ValueNodeData extends ValueStringifier[NodeData] {
    override def stringify(current: NodeData): String = current match {
      case data: NodeData.Integer      => ValueInteger.stringify(data.content)
      case data: NodeData.Decimal      => ValueDouble.stringify(data.content)
      case data: NodeData.Date         => ValueEpochMilli.stringify(data.content)
      case data: NodeData.RelativeDate => ValueDurationMilli.stringify(data.content)
      case data: NodeData.File         => data.fileName
      case data                        => ValueString.stringify(data.str)
    }
  }

  implicit val ValueNode: ValueStringifier[Node] = ValueNodeData.map(_.data)
}

case class EditContext(state: GlobalState) extends AnyVal

trait EditStringParser[+T] { self =>
  def parse(elem: String): Task[EditInteraction[T]]

  @inline final def map[R](f: T => R): EditStringParser[R] = flatMap[R](t => EditInteraction.Input(f(t)))
  @inline final def mapEval[R](f: T => Task[R]): EditStringParser[R] = flatMapEval[R](t => f(t).map(EditInteraction.Input(_)))
  @inline final def flatMap[R](f: T => EditInteraction[R]): EditStringParser[R] = flatMapEval(t => Task.pure(f(t)))
  final def flatMapEval[R](f: T => Task[EditInteraction[R]]): EditStringParser[R] = new EditStringParser[R] {
    def parse(elem: String) = self.parse(elem).flatMap(_.toEither.fold(Task.pure, f))
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

  def combine[A: EditStringParser, B: EditStringParser, T](fa: A => T, fb: B => T): EditStringParser[T] = combineParsers(EditStringParser[A].map(fa), EditStringParser[B].map(fb))

  def combineParsers[T](parserA: EditStringParser[T], parserB: EditStringParser[T]): EditStringParser[T] = new EditStringParser[T] {
    def parse(str: String) = parserA.parse(str).flatMap {
      case errA: EditInteraction.Error => parserB.parse(str).map {
        case errB: EditInteraction.Error => EditInteraction.Error(s"$errA and $errB")
        case e => e
      }
      case e => Task.pure(e)
    }
  }


  //TODO only allow valid node data type strings
  implicit val EditNodeDataType: EditStringParser[NodeData.Type] = EditString.map(NodeData.Type(_))
  implicit val EditNodeDataPlainText: EditStringParser[NodeData.PlainText] = EditString.map[NodeData.PlainText](NodeData.PlainText.apply)
  implicit val EditNodeDataMarkdown: EditStringParser[NodeData.Markdown] = EditString.map[NodeData.Markdown](NodeData.Markdown.apply)
  def EditNodeDataUser(user: NodeData.User): EditStringParser[NodeData.User] = EditNonEmptyString.flatMap[NodeData.User](s => EditInteraction.fromOption(user.updateName(s.string)))

  implicit def EditOption[T: EditStringParser]: EditStringParser[Option[T]] = EditStringParser[T].map(Some(_))

  implicit def EditNodeId: EditStringParser[NodeId] = EditString.flatMap[NodeId] { cuid =>
    EditInteraction.fromEither(Try(Cuid.fromCuidString(cuid)).toEither.fold(e => Left(e.getMessage), id => Right(NodeId(id))))
  }

  def forNodeDataType(tpe: NodeData.Type): Option[EditStringParser[NodeData.Content]] = tpe match {
    case NodeData.PlainText.tpe => Some(EditNodeDataPlainText)
    case NodeData.Markdown.tpe => Some(EditNodeDataMarkdown)
    case _ => None
  }
  def forNode(node: Node): Option[EditStringParser[Node]] = node match {
    case node: Node.Content => forNodeDataType(node.data.tpe).map(_.map(data => node.copy(data = data)))
    case user: Node.User => Some(EditNodeDataUser(user.data).map[Node](data => user.copy(data = data)))
  }

  def parseElement[T: EditStringParser](element: dom.Element): Task[EditInteraction[T]] = EditStringParser[T].parse(element.asInstanceOf[js.Dynamic].innerText.asInstanceOf[String]) // innerText because textContent would remove line-breaks in firefox
  def parseTextArea[T: EditStringParser](element: dom.html.TextArea): Task[EditInteraction[T]] = EditStringParser[T].parse(element.value)
  def parseSelect[T: EditStringParser](element: dom.html.Select): Task[EditInteraction[T]] = EditStringParser[T].parse(element.value)
}

trait EditInputParser[+T] { self =>
  def parse(elem: dom.html.Input): Task[EditInteraction[T]]
  def modifier(implicit ctx: Ctx.Owner): EditInputParser.Modifier

  @inline final def map[R](f: T => R): EditInputParser[R] = flatMap[R](t => EditInteraction.Input(f(t)))
  @inline final def mapEval[R](f: T => Task[R]): EditInputParser[R] = flatMapEval[R](t => f(t).map(EditInteraction.Input(_)))
  @inline final def flatMap[R](f: T => EditInteraction[R]): EditInputParser[R] = flatMapEval(t => Task.pure(f(t)))
  final def flatMapEval[R](f: T => Task[EditInteraction[R]]): EditInputParser[R] = new EditInputParser[R] {
    def parse(elem: dom.html.Input) = self.parse(elem).flatMap(_.toEither.fold(Task.pure, f))
    def modifier(implicit ctx: Ctx.Owner) = self.modifier
  }
}
object EditInputParser {
  @inline def apply[T](implicit parser: EditInputParser[T]): EditInputParser[T] = parser

  case class Modifier(
    mod: VDomModifier,
    outerMod: VDomModifier = VDomModifier.empty,
    fixedSubmitEvent: Option[EmitterBuilder[dom.Event, VDomModifier]] = None,
    valueSetter: AttributeBuilder[Either[String, String], VDomModifier] = either => either.fold(value := _, value := _)
  )

  object Disabled extends EditInputParser[Nothing] {
    def parse(elem: dom.html.Input) = Task.pure(EditInteraction.Cancel)
    def modifier(implicit ctx: Ctx.Owner) = Modifier(disabled := true)
  }

  implicit def EditStringParsing[T: EditStringParser] = new EditInputParser[T] {
    def parse(elem: dom.html.Input) = EditStringParser[T].parse(elem.value)
    def modifier(implicit ctx: Ctx.Owner) = Modifier(Elements.textInputMod)
  }

  implicit object EditInteger extends EditInputParser[Int] {
    def parse(elem: dom.html.Input) = Task.pure(EditInteraction.fromEither(Try(elem.valueAsNumber.toInt).toOption.toRight("Not an Integer Number")))
    def modifier(implicit ctx: Ctx.Owner) = Modifier(Elements.integerInputMod)
  }
  implicit object EditDouble extends EditInputParser[Double] {
    def parse(elem: dom.html.Input) = Task.pure(EditInteraction.fromEither(util.Try(elem.valueAsNumber).toOption.toRight("Not a Double Number")))
    def modifier(implicit ctx: Ctx.Owner) = Modifier(Elements.decimalInputMod)
  }
  implicit object EditEpochMilli extends EditInputParser[EpochMilli] {
    def parse(elem: dom.html.Input) = Task.pure(EditInteraction.fromEither(StringJsOps.safeToEpoch(elem.value).toRight("Not a Date")))
    def modifier(implicit ctx: Ctx.Owner) = Modifier(Elements.dateInputMod)
  }
  implicit object EditDurationMilli extends EditInputParser[DurationMilli] {
    def parse(elem: dom.html.Input) = Task.pure(EditInteraction.fromEither(StringJsOps.safeToDuration(elem.value)))
    def modifier(implicit ctx: Ctx.Owner) = Modifier(Elements.durationInputMod)
  }

  implicit def EditOption[T: EditInputParser]: EditInputParser[Option[T]] = EditInputParser[T].map(Some(_))

  implicit object EditFile extends EditInputParser[dom.File] {
    def parse(elem: dom.html.Input) = Task {
      val file = elem.asInstanceOf[js.Dynamic].files.asInstanceOf[js.UndefOr[dom.FileList]].collect { case list if list.length > 0 => list(0) }
      EditInteraction.fromOption(file.toOption)
    }
    def modifier(implicit ctx: Ctx.Owner) = {
      // TODO: we do not have the current state of the file input field, so we just get it again on change
      // This belongs into a more sophisticated ValueStringifier for not only writing strings...
      val fileValue = Handler.unsafe[Option[dom.File]](None)
      var fileElem: dom.html.Input = null
      val getFileValue = VDomModifier(
        onDomMount.foreach { e => fileElem = e.asInstanceOf[dom.html.Input] },
        onChange.transform(_.mapEval(_ => parse(fileElem))).editValueOption --> fileValue
      )

      val randomId = scala.util.Random.nextInt.toString
      val fileLabel = Components.uploadFieldModifier(fileValue, randomId)
      Modifier(
        VDomModifier(display.none, id := randomId, Elements.fileInputMod, getFileValue),
        outerMod = fileLabel,
        fixedSubmitEvent = Some(onChange),
        valueSetter = {
          case Right(value) =>
           parse(fileElem).runToFuture.map(e => fileValue.onNext(e.toOption))
           VDomModifier.empty
          case Left(resetValue) =>
            fileValue.onNext(None)
            value := ""
        }
      )
    }
  }

  implicit val EditNodeDataInteger: EditInputParser[NodeData.Integer] = EditInteger.map[NodeData.Integer](NodeData.Integer.apply)
  implicit val EditNodeDataDecimal: EditInputParser[NodeData.Decimal] = EditDouble.map[NodeData.Decimal](NodeData.Decimal.apply)
  implicit val EditNodeDataDate: EditInputParser[NodeData.Date] = EditEpochMilli.map[NodeData.Date](NodeData.Date.apply)
  implicit val EditNodeDataRelativeDate: EditInputParser[NodeData.RelativeDate] = EditDurationMilli.map[NodeData.RelativeDate](NodeData.RelativeDate.apply)

  implicit def EditUploadableFile(implicit context: EditContext): EditInputParser[AWS.UploadableFile] = EditFile.flatMap(file => EditInteraction.fromEither(AWS.upload(context.state, file)))
  implicit def EditNodeDataFile(implicit context: EditContext): EditInputParser[NodeData.File] = EditUploadableFile.mapEval(AWS.uploadFileAndCreateNodeData(context.state, _))

  def forNodeDataType(tpe: NodeData.Type)(implicit context: EditContext): Option[EditInputParser[NodeData.Content]] = EditStringParser.forNodeDataType(tpe).map(EditStringParsing[NodeData.Content](_)).orElse(tpe match {
    case NodeData.Integer.tpe => Some(EditNodeDataInteger)
    case NodeData.Decimal.tpe => Some(EditNodeDataDecimal)
    case NodeData.Date.tpe => Some(EditNodeDataDate)
    case NodeData.RelativeDate.tpe => Some(EditNodeDataRelativeDate)
    case NodeData.File.tpe => Some(EditNodeDataFile)
    case _ => None
  })
  def forNode(node: Node)(implicit context: EditContext): Option[EditInputParser[Node]] = EditStringParser.forNode(node).map(EditStringParsing[Node](_)).orElse(node match {
    case node: Node.Content => forNodeDataType(node.data.tpe).map(_.map(data => node.copy(data = data)))
    case _ => None
  })
}

sealed trait EditInteraction[+T] {
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
  case class Error(msg: String) extends WithoutValue
  case class Input[T](value: T) extends EditInteraction[T] {
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
}
