package wust.api.serialize

import wust.api._
import wust.ids._
import wust.graph._
import io.circe._, io.circe.generic.extras.semiauto._

object Circe extends wust.ids.serialize.Circe {

  implicit val PostDecoder: Decoder[Post] = deriveDecoder[Post]
  implicit val PostEncoder: Encoder[Post] = deriveEncoder[Post]
  implicit val ConnectionDecoder: Decoder[Connection] = deriveDecoder[Connection]
  implicit val ConnectionEncoder: Encoder[Connection] = deriveEncoder[Connection]
  implicit val MembershipDecoder: Decoder[Membership] = deriveDecoder[Membership]
  implicit val MembershipEncoder: Encoder[Membership] = deriveEncoder[Membership]

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
  implicit val connectionContentTypeKeyDecoder: KeyDecoder[ConnectionContent.Type] = KeyDecoder[String].map(ConnectionContent.Type(_))
  implicit val connectionContentTypeKeyEncoder: KeyEncoder[ConnectionContent.Type] = KeyEncoder[String].contramap[ConnectionContent.Type](identity)

  implicit val GraphDecoder: Decoder[Graph] = deriveDecoder[Graph]
  implicit val GraphEncoder: Encoder[Graph] = deriveEncoder[Graph]
}
