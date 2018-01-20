package wust.api.serialize

import wust.ids._
import io.circe._
import scalaz.Tag

import java.time.LocalDateTime

object Circe {
  import Helper._

  implicit val encodePostId: Encoder[PostId] = Encoder.encodeString.contramap[PostId](Tag.unwrap _)
  implicit val decodePostId: Decoder[PostId] = Decoder.decodeString.map(PostId(_))
  implicit val encodeGroupId: Encoder[GroupId] = Encoder.encodeLong.contramap[GroupId](Tag.unwrap _)
  implicit val decodeGroupId: Decoder[GroupId] = Decoder.decodeLong.map(GroupId(_))
  implicit val encodeUserId: Encoder[UserId] = Encoder.encodeString.contramap[UserId](Tag.unwrap _)
  implicit val decodeUserId: Decoder[UserId] = Decoder.decodeString.map(UserId(_))
  implicit val encodeLabel: Encoder[Label] = Encoder.encodeString.contramap[Label](Tag.unwrap _)
  implicit val decodeLabel: Decoder[Label] = Decoder.decodeString.map(Label(_))

  implicit val encodeLocalDateTime: Encoder[LocalDateTime] = Encoder.encodeLong.contramap[LocalDateTime](toMillis)
  implicit val decodeLocalDateTime: Decoder[LocalDateTime] = Decoder.decodeLong.map(fromMillis)
}
