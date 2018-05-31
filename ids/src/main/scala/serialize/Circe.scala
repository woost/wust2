package wust.ids.serialize

import wust.ids._
import io.circe._, io.circe.generic.extras.semiauto._, io.circe.generic.extras.Configuration
import supertagged._

trait Circe {
  // makes circe decode sealed hierarchies with { "_tpe": typename, ..props }
  implicit val genericConfiguration: Configuration = Configuration.default.withDiscriminator("type")

  implicit def liftEncoderTagged[T, U](implicit f: Encoder[T]): Encoder[T @@ U] = f.asInstanceOf[Encoder[T @@ U]]
  implicit def liftDecoderTagged[T, U](implicit f: Decoder[T]): Decoder[T @@ U] = f.asInstanceOf[Decoder[T @@ U]]
  implicit def liftEncoderOverTagged[R, T <: TaggedType[R], U](implicit f: Encoder[T#Type]): Encoder[T#Type @@ U] = f.asInstanceOf[Encoder[T#Type @@ U]]
  implicit def liftDecoderOverTagged[R, T <: TaggedType[R], U](implicit f: Decoder[T#Type]): Decoder[T#Type @@ U] = f.asInstanceOf[Decoder[T#Type @@ U]]

  implicit val DeletedDateDecoder: Decoder[DeletedDate] = deriveDecoder[DeletedDate]
  implicit val DeletedDateEncoder: Encoder[DeletedDate] = deriveEncoder[DeletedDate]
  implicit val JoinDateDecoder: Decoder[JoinDate] = deriveDecoder[JoinDate]
  implicit val JoinDateEncoder: Encoder[JoinDate] = deriveEncoder[JoinDate]

  // decode accesslevel as string instead of
  implicit val AccessLevelDecoder: Decoder[AccessLevel] = Decoder.decodeString.emap(s => AccessLevel.from.lift(s).toRight(s"Is not an access level: $s"))
  implicit val AccessLevelEncoder: Encoder[AccessLevel] = level => Json.fromString(level.str)

  implicit val postContentDecoder2: Decoder[NodeData.Content] = deriveDecoder[NodeData.Content]
  implicit val postContentEncoder2: Encoder[NodeData.Content] = deriveEncoder[NodeData.Content]
  implicit val postContentDecoder3: Decoder[NodeData.User] = deriveDecoder[NodeData.User]
  implicit val postContentEncoder3: Encoder[NodeData.User] = deriveEncoder[NodeData.User]
  implicit val postContentDecoder: Decoder[NodeData] = deriveDecoder[NodeData]
  implicit val postContentEncoder: Encoder[NodeData] = deriveEncoder[NodeData]
  implicit val connectionContentDecoder1: Decoder[EdgeData.Label] = deriveDecoder[EdgeData.Label]
  implicit val connectionContentEncoder1: Encoder[EdgeData.Label] = deriveEncoder[EdgeData.Label]
  implicit val connectionContentDecoder2: Decoder[EdgeData.Parent.type] = deriveDecoder[EdgeData.Parent.type]
  implicit val connectionContentEncoder2: Encoder[EdgeData.Parent.type] = deriveEncoder[EdgeData.Parent.type]
  implicit val connectionContentDecoder3: Decoder[EdgeData.Member] = deriveDecoder[EdgeData.Member]
  implicit val connectionContentEncoder3: Encoder[EdgeData.Member] = deriveEncoder[EdgeData.Member]
  implicit val connectionContentDecoder4: Decoder[EdgeData.Author] = deriveDecoder[EdgeData.Author]
  implicit val connectionContentEncoder4: Encoder[EdgeData.Author] = deriveEncoder[EdgeData.Author]
  implicit val connectionContentDecoder: Decoder[EdgeData] = deriveDecoder[EdgeData]
  implicit val connectionContentEncoder: Encoder[EdgeData] = deriveEncoder[EdgeData]
}
object Circe extends Circe
