package wust.api.serialize

import wust.api._
import wust.ids._
import wust.graph._
import io.circe._, io.circe.syntax._, io.circe.generic.semiauto._
import scalaz.Tag

object Circe {
  implicit val encodePostId: Encoder[PostId] = Encoder.encodeString.contramap[PostId](Tag.unwrap _)
  implicit val decodePostId: Decoder[PostId] = Decoder.decodeString.map(PostId(_))
  implicit val encodeGroupId: Encoder[GroupId] = Encoder.encodeLong.contramap[GroupId](Tag.unwrap _)
  implicit val decodeGroupId: Decoder[GroupId] = Decoder.decodeLong.map(GroupId(_))
  implicit val encodeUserId: Encoder[UserId] = Encoder.encodeString.contramap[UserId](Tag.unwrap _)
  implicit val decodeUserId: Decoder[UserId] = Decoder.decodeString.map(UserId(_))
  implicit val encodeLabel: Encoder[Label] = Encoder.encodeString.contramap[Label](Tag.unwrap _)
  implicit val decodeLabel: Decoder[Label] = Decoder.decodeString.map(Label(_))

  implicit val encodeEpochMilli: Encoder[EpochMilli] = Encoder.encodeLong.contramap[EpochMilli](Tag.unwrap)
  implicit val decodeEpochMilli: Decoder[EpochMilli] = Decoder.decodeLong.map(EpochMilli(_))

  implicit val PostDecoder: Decoder[Post] = deriveDecoder[Post]
  implicit val PostEncoder: Encoder[Post] = deriveEncoder[Post]
  implicit val ConnectionDecoder: Decoder[Connection] = deriveDecoder[Connection]
  implicit val ConnectionEncoder: Encoder[Connection] = deriveEncoder[Connection]
  implicit val OwnershipDecoder: Decoder[Ownership] = deriveDecoder[Ownership]
  implicit val OwnershipEncoder: Encoder[Ownership] = deriveEncoder[Ownership]

  implicit val UserAssumedDecoder: Decoder[User.Assumed] = deriveDecoder[User.Assumed]
  implicit val UserAssumedEncoder: Encoder[User.Assumed] = deriveEncoder[User.Assumed]
  implicit val UserVerifiedDecoder: Decoder[User.Persisted] = deriveDecoder[User.Persisted]
  implicit val UserVerifiedEncoder: Encoder[User.Persisted] = deriveEncoder[User.Persisted]
  implicit val userDecoder: Decoder[User] = deriveDecoder[User]
  implicit val userEncoder: Encoder[User] = deriveEncoder[User]
  implicit val AuthenticationDecoder: Decoder[Authentication] = deriveDecoder[Authentication]
  implicit val AuthenticationEncoder: Encoder[Authentication] = deriveEncoder[Authentication]
  implicit val GraphChangesDecoder: Decoder[GraphChanges] = deriveDecoder[GraphChanges]
  implicit val GraphChangesEncoder: Encoder[GraphChanges] = deriveEncoder[GraphChanges]
}
