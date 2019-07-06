package wust.ids

import supertagged._

sealed trait NodeData {
  def str: String //TODO: define this properly via typeclass to plugin from the outside.
  val tpe: NodeData.Type

  @inline def as[T <: NodeData]: T = asInstanceOf[T]
}
object NodeData {
  object Type extends TaggedType[String]
  type Type = Type.Type

  abstract class Named(implicit name: sourcecode.Name) {
    val tpe = Type(name.value)
  }

  sealed trait EditableText extends Content {
    def updateStr(str: String): Option[EditableText]
  }

  sealed trait Content extends NodeData

  final case class File(key: String, fileName: String, contentType: String) extends Named with Content {
    def str = fileName
  }
  object File extends Named

  final case class Markdown(content: String) extends Named with EditableText {
    def str = content
    override def updateStr(str: String) = if (content != str) Some(copy(content = str)) else None
  }
  object Markdown extends Named

  final case class PlainText(content: String) extends Named with EditableText {
    def str = content
    override def updateStr(str: String) = if (content != str) Some(copy(content = str)) else None
  }
  object PlainText extends Named

  final case class Integer(content: Int) extends Named with Content {
    def str = content.toString
  }
  object Integer extends Named

  final case class Decimal(content: Double) extends Named with Content {
    def str = content.toString
  }
  object Decimal extends Named

  final case class DateTime(content: DateTimeMilli) extends Named with Content {
    def plainStr = content.toString
    def str = content.isoDateAndTime
  }
  object DateTime extends Named
  final case class Date(content: DateMilli) extends Named with Content {
    def plainStr = content.toString
    def str = content.isoDate
  }
  object Date extends Named
  final case class Duration(content: DurationMilli) extends Named with Content {
    def str = content.toString
  }
  object Duration extends Named
  //TODO: should be renamed to datetime, because now+duration does not only give a date.
  final case class RelativeDate(content: DurationMilli) extends Named with Content {
    def str = content.toString
  }
  object RelativeDate extends Named

  final case class Placeholder(targetType: Option[NodeTypeSelection]) extends Named with Content {
    def str = ""
  }
  object Placeholder extends Named

  final case class User(name: String, isImplicit: Boolean, revision: Int) extends Named with NodeData {
    def str = name
    def updateName(newName: String) = if (name != newName) Some(copy(name = newName.trim)) else None
  }
  object User extends Named

}

sealed trait NodeTypeSelection
object NodeTypeSelection {
  final case class Data(data: NodeData.Type) extends NodeTypeSelection
  case object Ref extends NodeTypeSelection
}
