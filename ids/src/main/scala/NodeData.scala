package wust.ids

import supertagged._

import scala.util.Try

sealed trait NodeData {
  def str: String //TODO: define this properly
  val tpe: NodeData.Type
}
object NodeData {
  object Type extends TaggedType[String]
  type Type = Type.Type

  abstract class Named(implicit name: sourcecode.Name) {
    val tpe = Type(name.value)
  }

  sealed trait PropertyValue {
//    def constraint: Boolean
  }
  sealed trait Content extends NodeData {
    def updateStr(str: String): Option[Content]
  }

  case class File(key: String, fileName: String, contentType: String, description: String) extends Named with Content {
    def str = if (description.isEmpty) "[File]" else description
    override def updateStr(str: String) = if (description != str) Some(copy(description = str)) else None
  }
  object File extends Named

  case class Markdown(content: String) extends Named with Content {
    def str = content
    override def updateStr(str: String) = if (content != str) Some(copy(content = str)) else None
  }
  object Markdown extends Named

  case class PlainText(content: String) extends Named with Content with PropertyValue {
    def str = content
    @inline private def update(str: String) = if (content != str) Some(copy(content = str)) else None
    override def updateStr(str: String): Option[Content] = update(str)
  }
  object PlainText extends Named

  case class Integer(content: Int) extends Named with Content with PropertyValue {
    require(content <= Int.MaxValue && content >= Int.MinValue)
    def str = content.toString
    override def updateStr(str: String): Option[Content] =
      Try(str.trim.toInt).toOption.flatMap(value => if(content != value) Some(copy(content = value)) else None) // FIXME: do not use Try
  }
  object Integer extends Named

  case class Float(content: Double) extends Named with Content with PropertyValue {
    require(content <= Double.MaxValue && content >= Double.MinValue)
    def str = content.toString
    override def updateStr(str: String): Option[Content] =
      Try(str.trim.toDouble).toOption.flatMap(value => if(content != value) Some(copy(content = value)) else None) // FIXME: do not use Try
  }
  object Float extends Named

  case class Date(content: EpochMilli) extends Named with Content with PropertyValue {
    def plainStr = content.toString
    def str = content.humanReadable
    override def updateStr(str: String): Option[Content] =
      Try(EpochMilli(str.trim.toLong)).toOption.flatMap(value => if(content != value) Some(copy(content = value)) else None)
  }
  object Date extends Named


  object User extends Named
  case class User(name: String, isImplicit: Boolean, revision: Int) extends Named with NodeData {
    def str = name
  }

}

