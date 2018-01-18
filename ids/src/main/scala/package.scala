package wust

import boopickle.Default._
import io.circe._
import java.time.{ZoneId, Instant, LocalDateTime}

import scalaz._

package object ids {
  import types._

  type IdType = Long
  type UuidType = String

  //TODO: simpler tagged types: https://github.com/acjay/taggy
  // unboxed types with scalaz: http://eed3si9n.com/learning-scalaz/Tagged+type.html
  object PostId extends UuidTypeFactory[PostIdType]
  type PostId = PostId.Type
  implicit def UuidTypeIsPostId(id: UuidType) = PostId(id)

  object GroupId extends IdTypeFactory[GroupIdType]
  type GroupId = GroupId.Type
  implicit def UuidTypeIsGroupId(id: IdType): GroupId = GroupId(id)

  object UserId extends IdTypeFactory[UserIdType]
  type UserId = UserId.Type
  implicit def UuidTypeIsUserId(id: IdType): UserId = UserId(id)

  object Label extends TypeFactory[String, LabelType] {
    val parent = Label("parent")
  }
  type Label = Label.Type
  implicit def StringIsLabel(id: String): Label = Label(id)

  //TODO: LocalDateTime is internally stored as two Longs, does the precision loss matter?
  def toMillis(ldt: LocalDateTime) = ldt.atZone(ZoneId.systemDefault).toInstant.toEpochMilli
  def fromMillis(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault).toLocalDateTime
  implicit val ldtPickler: Pickler[LocalDateTime] = transformPickler((t: Long) => fromMillis(t))(x => toMillis(x))

  implicit def PostIdPickler = transformPickler[PostId, UuidType](PostId(_))(Tag.unwrap _)
  implicit def GroupIdPickler = transformPickler[GroupId, IdType](GroupId(_))(Tag.unwrap _)
  implicit def UserIdPickler = transformPickler[UserId, IdType](UserId(_))(Tag.unwrap _)
  implicit def LabelPickler = transformPickler[Label, String](Label(_))(Tag.unwrap _)
  implicit val dateTimePickler = transformPickler((t: Long) => new java.util.Date(t))(_.getTime)

  implicit val encodePostId: Encoder[PostId] = Encoder.encodeString.contramap[PostId](Tag.unwrap _)
  implicit val decodePostId: Decoder[PostId] = Decoder.decodeString.map(PostId(_))
  implicit val encodeGroupId: Encoder[GroupId] = Encoder.encodeLong.contramap[GroupId](Tag.unwrap _)
  implicit val decodeGroupId: Decoder[GroupId] = Decoder.decodeLong.map(GroupId(_))
  implicit val encodeUserId: Encoder[UserId] = Encoder.encodeLong.contramap[UserId](Tag.unwrap _)
  implicit val decodeUserId: Decoder[UserId] = Decoder.decodeLong.map(UserId(_))
  implicit val encodeLabel: Encoder[Label] = Encoder.encodeString.contramap[Label](Tag.unwrap _)
  implicit val decodeLabel: Decoder[Label] = Decoder.decodeString.map(Label(_))
  implicit val encodeLocalDateTime: Encoder[LocalDateTime] = Encoder.encodeLong.contramap[LocalDateTime](toMillis)
  implicit val decodeLocalDateTime: Decoder[LocalDateTime] = Decoder.decodeLong.map(fromMillis)
}
