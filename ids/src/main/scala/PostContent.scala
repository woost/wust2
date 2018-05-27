package wust.ids

import io.treev.tag._

sealed trait PostContent {
  def str: String //TODO: define this properly
  val tpe: PostContent.Type
}
object PostContent {
  object Type extends TaggedType[String]
  type Type = Type.Type

  abstract class Named(implicit name: sourcecode.Name) {
    val tpe = Type(name.value)
  }

  case class Markdown(content: String) extends Named with PostContent {
    def str = content
  }
  object Markdown extends Named
  case class Text(content: String) extends Named with PostContent {
    def str = content
  }
  object Text extends Named
  case class Link(url: String) extends Named with PostContent {
    //TODO: require url format?
    def str = url
  }
  object Link extends Named
  case object Channels extends Named with PostContent {
    def str = "Channels"
  }
}
