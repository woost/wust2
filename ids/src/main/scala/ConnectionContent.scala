package wust.ids

import io.treev.tag._

sealed trait ConnectionContent {
  val tpe: ConnectionContent.Type
}
object ConnectionContent {
  object Type extends TaggedType[String]
  type Type = Type.Type

  abstract class Named(implicit name: sourcecode.Name) {
    val tpe = Type(name.value)
  }

  case object Parent extends Named with ConnectionContent
  case class Number(content: String, weight: Double) extends Named with ConnectionContent
  object Number extends Named
  case class Text(content: String) extends Named with ConnectionContent
  object Text extends Named
  case class Authorship(timestamp: EpochMilli) extends Named with ConnectionContent
  object Authorship extends Named
  case class Membership(userId: UserId, postId: PostId, level: AccessLevel) extends Named with ConnectionContent
  object Membership extends Named
}
