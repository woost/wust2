package wust.api.serialize

import wust.graph.GraphChanges
import wust.api.ApiEvent
import wust.ids._
import boopickle.Default._
import scalaz.Tag

import java.time.LocalDateTime

object Boopickle {
  import Helper._

  implicit val PostIdPickler = transformPickler[PostId, UuidType](PostId(_))(Tag.unwrap _)
  implicit val GroupIdPickler = transformPickler[GroupId, IdType](GroupId(_))(Tag.unwrap _)
  implicit val UserIdPickler = transformPickler[UserId, UuidType](UserId(_))(Tag.unwrap _)
  implicit val LabelPickler = transformPickler[Label, String](Label(_))(Tag.unwrap _)

  implicit val localDateTimePickler: Pickler[LocalDateTime] = transformPickler((t: Long) => fromMillis(t))(x => toMillis(x))

  implicit val apiEventPickler = generatePickler[ApiEvent]
  implicit val graphChangesPickler = generatePickler[GraphChanges]
}
