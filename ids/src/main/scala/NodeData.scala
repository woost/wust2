package wust.ids

import supertagged._

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

  sealed trait Content extends NodeData {
    def updateStr(str: String): Content
  }

  case class File(key: String, fileName: String, contentType: String, description: String) extends Named with Content {
    def str = if (description.isEmpty) "[File]" else description
    override def updateStr(str: String): Content = copy(description = str)
  }
  object File extends Named

  case class Markdown(content: String) extends Named with Content {
    def str = content
    override def updateStr(str: String): Content = copy(content = str)
  }
  object Markdown extends Named

  case class PlainText(content: String) extends Named with Content {
    def str = content
    override def updateStr(str: String): Content = copy(content = str)
  }
  object PlainText extends Named

  object User extends Named
  case class User(name: String, isImplicit: Boolean, revision: Int) extends Named with NodeData {
    def str = name
  }
}
