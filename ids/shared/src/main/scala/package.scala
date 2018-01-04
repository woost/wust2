package wust

import boopickle.Default._
import io.circe._
import wust.idtypes._
import java.time.{ZoneId, Instant, LocalDateTime}

import scalaz._

package object ids {
  type IdType = Long
  type UuidType = String

  //TODO: simpler tagged types: https://github.com/acjay/taggy
  // unboxed types with scalaz: http://eed3si9n.com/learning-scalaz/Tagged+type.html
  type PostId = UuidType @@ PostIdType
  implicit def PostId(id: UuidType): PostId = Tag[UuidType, PostIdType](id)

  type GroupId = IdType @@ GroupIdType
  implicit def GroupId(id: IdType): GroupId = Tag[IdType, GroupIdType](id)

  type UserId = IdType @@ UserIdType
  implicit def UserId(id: IdType): UserId = Tag[IdType, UserIdType](id)

  type Label = String @@ LabelType
  implicit def Label(name: String): Label = Tag[String, LabelType](name)

  object Label {
    val parent = Label("parent")
  }

  //TODO: LocalDateTime is internally stored as two Longs, does the precision loss matter?
  def toMillis(ldt: LocalDateTime) = ldt.atZone(ZoneId.systemDefault).toInstant.toEpochMilli
  def fromMillis(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault).toLocalDateTime
  implicit val ldtPickler: Pickler[LocalDateTime] = transformPickler((t: Long) => fromMillis(t))(x => toMillis(x))

  implicit def PostIdPickler = transformPickler[PostId, UuidType](PostId _)(Tag.unwrap _)
  implicit def GroupIdPickler = transformPickler[GroupId, IdType](GroupId _)(Tag.unwrap _)
  implicit def UserIdPickler = transformPickler[UserId, IdType](UserId _)(Tag.unwrap _)
  implicit def LabelPickler = transformPickler[Label, String](Label _)(Tag.unwrap _)
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
