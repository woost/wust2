package wust.ids.serialize

import wust.ids._
import io.circe._, io.circe.generic.extras.semiauto._, io.circe.generic.extras.Configuration
import io.treev.tag._

trait Circe {
  // makes circe decode sealed hierarchies with { "_tpe": typename, ..props }
  implicit val genericConfiguration: Configuration = Configuration.default.withDiscriminator("type")

  private def encodeTaggedType[T: Encoder, Type <: TaggedType[T]#Type]: Encoder[Type] = Encoder[T].contramap[Type](identity)
  private def decodeTaggedType[T: Decoder, Type <: TaggedType[T]#Type]: Decoder[Type] = Decoder[T].map(_.asInstanceOf[Type])

  // cannot resolve automatically for any T, so need specialized implicit defs
  implicit def encodeStringTaggedType[Type <: TaggedType[String]#Type]: Encoder[Type] = encodeTaggedType[String, Type]
  implicit def encodeLongTaggedType[Type <: TaggedType[Long]#Type]: Encoder[Type] = encodeTaggedType[Long, Type]
  implicit def encodeIntTaggedType[Type <: TaggedType[Int]#Type]: Encoder[Type] = encodeTaggedType[Int, Type]
  implicit def decodeStringTaggedType[Type <: TaggedType[String]#Type]: Decoder[Type] = decodeTaggedType[String, Type]
  implicit def decodeLongTaggedType[Type <: TaggedType[Long]#Type]: Decoder[Type] = decodeTaggedType[Long, Type]
  implicit def decodeIntTaggedType[Type <: TaggedType[Int]#Type]: Decoder[Type] = decodeTaggedType[Int, Type]

  implicit val AccessLevelDecoder: Decoder[AccessLevel] = deriveDecoder[AccessLevel]
  implicit val AccessLevelEncoder: Encoder[AccessLevel] = deriveEncoder[AccessLevel]
  implicit val JoinDateDecoder: Decoder[JoinDate] = deriveDecoder[JoinDate]
  implicit val JoinDateEncoder: Encoder[JoinDate] = deriveEncoder[JoinDate]

  implicit val postContentDecoder: Decoder[PostContent] = deriveDecoder[PostContent]
  implicit val postContentEncoder: Encoder[PostContent] = deriveEncoder[PostContent]
  implicit val connectionContentDecoder: Decoder[ConnectionContent] = deriveDecoder[ConnectionContent]
  implicit val connectionContentEncoder: Encoder[ConnectionContent] = deriveEncoder[ConnectionContent]
}
object Circe extends Circe
