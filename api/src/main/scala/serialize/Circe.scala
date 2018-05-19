package wust.api.serialize

import wust.api._
import wust.ids._
import wust.graph._
import io.circe._, io.circe.syntax._, io.circe.generic.semiauto._

object Circe {
  implicit val encodePostId: Encoder[PostId] = Encoder.encodeString.contramap[PostId](identity)
  implicit val decodePostId: Decoder[PostId] = Decoder.decodeString.map(PostId(_))
  implicit val encodeUserId: Encoder[UserId] = Encoder.encodeString.contramap[UserId](identity)
  implicit val decodeUserId: Decoder[UserId] = Decoder.decodeString.map(UserId(_))
  implicit val encodeLabel: Encoder[Label] = Encoder.encodeString.contramap[Label](identity)
  implicit val decodeLabel: Decoder[Label] = Decoder.decodeString.map(Label(_))

  implicit val encodeEpochMilli: Encoder[EpochMilli] = Encoder.encodeLong.contramap[EpochMilli](identity)
  implicit val decodeEpochMilli: Decoder[EpochMilli] = Decoder.decodeLong.map(EpochMilli(_))

  implicit val JoinDateDecoder: Decoder[JoinDate] = deriveDecoder[JoinDate]
  implicit val JoinDateEncoder: Encoder[JoinDate] = deriveEncoder[JoinDate]
  implicit val AccessLevelDecoder: Decoder[AccessLevel] = deriveDecoder[AccessLevel]
  implicit val AccessLevelEncoder: Encoder[AccessLevel] = deriveEncoder[AccessLevel]
  implicit val PostDecoder: Decoder[Post] = deriveDecoder[Post]
  implicit val PostEncoder: Encoder[Post] = deriveEncoder[Post]
  implicit val ConnectionDecoder: Decoder[Connection] = deriveDecoder[Connection]
  implicit val ConnectionEncoder: Encoder[Connection] = deriveEncoder[Connection]
  implicit val MembershipDecoder: Decoder[Membership] = deriveDecoder[Membership]
  implicit val MembershipEncoder: Encoder[Membership] = deriveEncoder[Membership]

  implicit val postContentDecoder1: Decoder[PostContent.Markdown] = deriveDecoder[PostContent.Markdown]
  implicit val postContentEncoder1: Encoder[PostContent.Markdown] = deriveEncoder[PostContent.Markdown]
  implicit val postContentDecoder2: Decoder[PostContent.Text] = deriveDecoder[PostContent.Text]
  implicit val postContentEncoder2: Encoder[PostContent.Text] = deriveEncoder[PostContent.Text]
  implicit val postContentDecoder3: Decoder[PostContent.Channels.type] = deriveDecoder[PostContent.Channels.type]
  implicit val postContentEncoder3: Encoder[PostContent.Channels.type] = deriveEncoder[PostContent.Channels.type]
  implicit val postContentDecoder4: Decoder[PostContent.Link] = deriveDecoder[PostContent.Link]
  implicit val postContentEncoder4: Encoder[PostContent.Link] = deriveEncoder[PostContent.Link]
  implicit val postContentDecoder: Decoder[PostContent] = deriveDecoder[PostContent]
  implicit val postContentEncoder: Encoder[PostContent] = deriveEncoder[PostContent]

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

  implicit val userIdKeyDecoder: KeyDecoder[UserId] = KeyDecoder[String].map(UserId(_))
  implicit val userIdKeyEncoder: KeyEncoder[UserId] = KeyEncoder[String].contramap[UserId](identity)
  implicit val postIdKeyDecoder: KeyDecoder[PostId] = KeyDecoder[String].map(PostId(_))
  implicit val postIdKeyEncoder: KeyEncoder[PostId] = KeyEncoder[String].contramap[PostId](identity)
  implicit val labelKeyDecoder: KeyDecoder[Label] = KeyDecoder[String].map(Label(_))
  implicit val labelKeyEncoder: KeyEncoder[Label] = KeyEncoder[String].contramap[Label](identity)

  implicit val GraphDecoder: Decoder[Graph] = deriveDecoder[Graph]
  implicit val GraphEncoder: Encoder[Graph] = deriveEncoder[Graph]
}
