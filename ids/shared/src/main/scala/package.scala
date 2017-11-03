package wust

import boopickle.Default._
import io.circe._
import wust.idtypes._

import scalaz._

package object ids {
  type IdType = Long
  type UuidType = String

  // unboxed types with scalaz: http://eed3si9n.com/learning-scalaz/Tagged+type.html
  type PostId = UuidType @@ PostIdType
  implicit def PostId(id: UuidType): PostId = Tag[UuidType, PostIdType](id)

  type GroupId = IdType @@ GroupIdType
  implicit def GroupId(id: IdType): GroupId = Tag[IdType, GroupIdType](id)

  type UserId = IdType @@ UserIdType
  implicit def UserId(id: IdType): UserId = Tag[IdType, UserIdType](id)

  implicit def PostIdPickler = transformPickler[PostId, UuidType](PostId _)(Tag.unwrap _)
  implicit def GroupIdPickler = transformPickler[GroupId, IdType](GroupId _)(Tag.unwrap _)
  implicit def UserIdPickler = transformPickler[UserId, IdType](UserId _)(Tag.unwrap _)

  implicit val encodePostId: Encoder[PostId] = Encoder.encodeString.contramap[PostId](Tag.unwrap _)
  implicit val decodePostId: Decoder[PostId] = Decoder.decodeString.map(PostId(_))
  implicit val encodeGroupId: Encoder[GroupId] = Encoder.encodeLong.contramap[GroupId](Tag.unwrap _)
  implicit val decodeGroupId: Decoder[GroupId] = Decoder.decodeLong.map(GroupId(_))
  implicit val encodeUserId: Encoder[UserId] = Encoder.encodeLong.contramap[UserId](Tag.unwrap _)
  implicit val decodeUserId: Decoder[UserId] = Decoder.decodeLong.map(UserId(_))
}
