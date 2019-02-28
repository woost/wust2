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
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Elements._

import scala.concurrent.duration._
import scala.scalajs.js

trait ValueStringifier[-T] { self =>
  def stringify(current: T): String

  final def map[R](f: R => T): ValueStringifier[R] = new ValueStringifier[R] {
    def stringify(current: R) = self.stringify(f(current))
  }
}
object ValueStringifier {
  @inline def apply[T](implicit stringifier: ValueStringifier[T]): ValueStringifier[T] = stringifier

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

  implicit def ValueOption[T: ValueStringifier]: ValueStringifier[Option[T]] = new ValueStringifier[Option[T]] {
    def stringify(current: Option[T]): String = current.fold("")(ValueStringifier[T].stringify)
  }

  implicit object ValueFile extends ValueStringifier[dom.File] {
    def stringify(current: dom.File): String = current.name
  }

  implicit val ValueNodeData: ValueStringifier[NodeData] = new ValueStringifier[NodeData] {
    override def stringify(current: NodeData): String = current match {
      case data: NodeData.Integer      => ValueInteger.stringify(data.content)
      case data: NodeData.Decimal      => ValueDouble.stringify(data.content)
      case data: NodeData.Date         => ValueEpochMilli.stringify(data.content)
      case data: NodeData.RelativeDate => ValueDurationMilli.stringify(data.content)
      case data                        => ValueString.stringify(data.str)
    }
  }

  implicit val ValueNode: ValueStringifier[Node] = ValueNodeData.map(_.data)
}

trait EditParser[+T] { self =>
  def parse(elem: dom.Element): EditInteraction[T]
  def inputModifier: VDomModifier

  final def map[R](f: T => R): EditParser[R] = flatMap[R](t => EditInteraction.Input(f(t)))
  final def flatMap[R](f: T => EditInteraction[R]): EditParser[R] = new EditParser[R] {
    def parse(elem: dom.Element): EditInteraction[R] = self.parse(elem).flatMap(f)
    def inputModifier: VDomModifier = self.inputModifier
  }
}
object EditParser {
  @inline def apply[T](implicit parser: EditParser[T]): EditParser[T] = parser

  trait StringBased[+T] extends EditParser[T] {
    final def parse(elem: dom.Element) = {
      val str = elem.asInstanceOf[js.Dynamic].value.asInstanceOf[js.UndefOr[String]].getOrElse(elem.asInstanceOf[js.Dynamic].innerText.asInstanceOf[String]) // innerText because textContent would remove line-breaks in firefox
      parse(str)
    }
    def parse(str: String): EditInteraction[T]
  }

  object Disabled extends EditParser[Nothing] {
    def parse(elem: dom.Element) = EditInteraction.Cancel
    def inputModifier = VDomModifier(disabled := true)
  }

  implicit object EditString extends StringBased[String] {
    def parse(str: String) = EditInteraction.Input(str)
    def inputModifier = Elements.textInputMod
  }
  implicit val EditNonEmptyString: EditParser[NonEmptyString] = EditString.flatMap(str => EditInteraction.fromEither(str, NonEmptyString(str).toRight("Cannot be empty")))
  implicit object EditInteger extends StringBased[Int] {
    def parse(str: String) = EditInteraction.fromEither(str, StringOps.safeToInt(str).toRight("Not an Integer Number"))
    def inputModifier = Elements.integerInputMod
  }
  implicit object EditDouble extends StringBased[Double] {
    def parse(str: String) = EditInteraction.fromEither(str, StringOps.safeToDouble(str).toRight("Not a Double Number"))
    def inputModifier = Elements.decimalInputMod
  }
  implicit object EditEpochMilli extends StringBased[EpochMilli] {
    def parse(str: String) = EditInteraction.fromEither(str, StringJsOps.safeToEpoch(str).toRight("Not a Date"))
    def inputModifier = Elements.dateInputMod
  }
  implicit object EditDurationMilli extends StringBased[DurationMilli] {
    def parse(str: String) = EditInteraction.fromEither(str, StringJsOps.safeToDuration(str))
    def inputModifier = Elements.durationInputMod
  }

  implicit def EditOption[T: EditParser]: EditParser[Option[T]] = EditParser[T].map(Some(_))

  implicit object EditParserFile extends EditParser[dom.File] {
    def parse(elem: dom.Element) = {
      val file = elem.asInstanceOf[js.Dynamic].files.asInstanceOf[js.UndefOr[dom.FileList]].collect { case list if list.length > 0 => list(0) }
      EditInteraction.fromEither(file.fold("")(_.name), file.toRight("Cannot find file"))
    }
    def inputModifier = Elements.fileInputMod
  }

  implicit val EditNodeDataInteger: EditParser[NodeData.Integer] = EditInteger.map[NodeData.Integer](NodeData.Integer.apply)
  implicit val EditNodeDataDecimal: EditParser[NodeData.Decimal] = EditDouble.map[NodeData.Decimal](NodeData.Decimal.apply)
  implicit val EditNodeDataDate: EditParser[NodeData.Date] = EditEpochMilli.map[NodeData.Date](NodeData.Date.apply)
  implicit val EditNodeDataRelativeDate: EditParser[NodeData.RelativeDate] = EditDurationMilli.map[NodeData.RelativeDate](NodeData.RelativeDate.apply)
  implicit val EditNodeDataPlainText: EditParser[NodeData.PlainText] = EditString.map[NodeData.PlainText](NodeData.PlainText.apply)
  implicit val EditNodeDataMarkdown: EditParser[NodeData.Markdown] = EditString.map[NodeData.Markdown](NodeData.Markdown.apply)

  val forNodeDataType: NodeData.Type => Option[EditParser[NodeData]] = {
    case NodeData.Integer.tpe => Some(EditNodeDataInteger)
    case NodeData.Decimal.tpe => Some(EditNodeDataDecimal)
    case NodeData.Date.tpe => Some(EditNodeDataDate)
    case NodeData.RelativeDate.tpe => Some(EditNodeDataRelativeDate)
    case NodeData.PlainText.tpe => Some(EditNodeDataPlainText)
    case NodeData.Markdown.tpe => Some(EditNodeDataMarkdown)
    case _ => None
  }
  val forNode: Node => Option[EditParser[Node]] = {
    case node: Node.Content =>
      val parser = node.data match {
        case _: NodeData.Integer => Some(EditNodeDataInteger)
        case _: NodeData.Decimal => Some(EditNodeDataDecimal)
        case _: NodeData.Date => Some(EditNodeDataDate)
        case _: NodeData.RelativeDate => Some(EditNodeDataRelativeDate)
        case _: NodeData.PlainText => Some(EditNodeDataPlainText)
        case _: NodeData.Markdown => Some(EditNodeDataMarkdown)
        case _ => None
      }
      parser.map(_.map(data => node.copy(data = data)))
    case user: Node.User =>
      Some(EditNonEmptyString.flatMap[Node](str => EditInteraction.fromOption(user.data.updateName(str.string).map(data => user.copy(data = data)))))
  }
}

sealed trait EditInteraction[+T] {
  def map[R](f: T => R): EditInteraction[R] = flatMap[R](t => EditInteraction.Input(f(t)))
  def flatMap[R](f: T => EditInteraction[R]): EditInteraction[R]
  def fold[R](default: => R)(f: T => R): R
}
object EditInteraction {
  sealed trait EditInteractionDefault extends EditInteraction[Nothing] {
    def flatMap[R](f: Nothing => EditInteraction[R]) = this
    def fold[R](default: => R)(f: Nothing => R) = default
  }
  case object Cancel extends EditInteractionDefault
  case class Error(failingValue: String, msg: String) extends EditInteractionDefault
  case class Input[T](value: T) extends EditInteraction[T] {
    def flatMap[R](f: T => EditInteraction[R]) = f(value)
    def fold[R](default: => R)(f: T => R) = f(value)
  }

  def fromEither[T](origin: String, either: Either[String, T]): EditInteraction[T] = either match {
    case Right(value) => EditInteraction.Input(value)
    case Left(error) => EditInteraction.Error(origin, error)
  }
  def fromOption[T](option: Option[T]): EditInteraction[T] = option match {
    case Some(value) => EditInteraction.Input(value)
    case None => EditInteraction.Cancel
  }
}
