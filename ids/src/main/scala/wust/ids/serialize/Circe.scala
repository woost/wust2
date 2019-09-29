package wust.ids.serialize

import java.util.UUID

import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import supertagged._
import wust.ids._

import scala.util.Try

trait Circe {
  // makes circe decode sealed hierarchies with { "_tpe": typename, ..props }
  implicit val genericConfiguration: Configuration = Configuration.default.withDiscriminator("type")

  //TODO: IMPORTANT we actually want different encoders for api and db json. in the db we
  //cuid as uuid strings but in the json api, we want to have a more
  //space-efficent base than 16 (as of uuid) or encode as two numbers.
  implicit val CuidDecoder: Decoder[Cuid] = Decoder.decodeString.emap(
    s => Try(Cuid.fromUuid(UUID.fromString(s))).toEither.left.map(_.getMessage)
  ) // don't change!
  implicit val CuidEncoder: Encoder[Cuid] = cuid => Json.fromString(cuid.toUuid.toString) // don't change!

  implicit def liftEncoderTagged[T, U](implicit f: Encoder[T]): Encoder[T @@ U] =
    f.asInstanceOf[Encoder[T @@ U]]
  implicit def liftDecoderTagged[T, U](implicit f: Decoder[T]): Decoder[T @@ U] =
    f.asInstanceOf[Decoder[T @@ U]]
  implicit def liftEncoderOverTagged[R, T <: TaggedType[R], U](
      implicit f: Encoder[T#Type]
  ): Encoder[T#Type @@ U] = f.asInstanceOf[Encoder[T#Type @@ U]]
  implicit def liftDecoderOverTagged[R, T <: TaggedType[R], U](
      implicit f: Decoder[T#Type]
  ): Decoder[T#Type @@ U] = f.asInstanceOf[Decoder[T#Type @@ U]]

  // decode accesslevel as string instead of
  implicit val AccessLevelDecoder: Decoder[AccessLevel] =
    Decoder.decodeString.emap(
      s => AccessLevel.fromString.lift(s).toRight(s"Is not an access level: $s")
    )
  implicit val AccessLevelEncoder: Encoder[AccessLevel] = level => Json.fromString(level.str)
  implicit val nodeAccessDecoder: Decoder[NodeAccess] = deriveDecoder[NodeAccess]
  implicit val nodeAccessEncoder: Encoder[NodeAccess] = deriveEncoder[NodeAccess]

  implicit val KanbanSettingsDecoder: Decoder[KanbanSettings] = deriveDecoder[KanbanSettings]
  implicit val KanbanSettingsEncoder: Encoder[KanbanSettings] = deriveEncoder[KanbanSettings]
  implicit val NodeSettingsDecoder: Decoder[NodeSettings] = deriveDecoder[NodeSettings]
  implicit val NodeSettingsEncoder: Encoder[NodeSettings] = deriveEncoder[NodeSettings]

  implicit val postContentDecoder2: Decoder[NodeData.Content] = deriveDecoder[NodeData.Content]
  implicit val postContentEncoder2: Encoder[NodeData.Content] = deriveEncoder[NodeData.Content]
  implicit val postContentDecoder3: Decoder[NodeData.User] = deriveDecoder[NodeData.User]
  implicit val postContentEncoder3: Encoder[NodeData.User] = deriveEncoder[NodeData.User]
  implicit val postContentDecoder: Decoder[NodeData] = deriveDecoder[NodeData]
  implicit val postContentEncoder: Encoder[NodeData] = deriveEncoder[NodeData]

  implicit val nodeRoleDecoder: Decoder[NodeRole] = deriveDecoder[NodeRole]
  implicit val nodeRoleEncoder: Encoder[NodeRole] = deriveEncoder[NodeRole]
  implicit val connectionContentDecoder2: Decoder[EdgeData.Child] = deriveDecoder[EdgeData.Child]
  implicit val connectionContentEncoder2: Encoder[EdgeData.Child] = deriveEncoder[EdgeData.Child]
  implicit val connectionContentDecoder3: Decoder[EdgeData.Member] = deriveDecoder[EdgeData.Member]
  implicit val connectionContentEncoder3: Encoder[EdgeData.Member] = deriveEncoder[EdgeData.Member]
  implicit val connectionContentDecoder4: Decoder[EdgeData.Author] = deriveDecoder[EdgeData.Author]
  implicit val connectionContentEncoder4: Encoder[EdgeData.Author] = deriveEncoder[EdgeData.Author]
  implicit val connectionContentDecoder5: Decoder[EdgeData.LabeledProperty] = deriveDecoder[EdgeData.LabeledProperty]
  implicit val connectionContentEncoder5: Encoder[EdgeData.LabeledProperty] = deriveEncoder[EdgeData.LabeledProperty]
  implicit val connectionContentDecoder6: Decoder[EdgeData.DerivedFromTemplate] = deriveDecoder[EdgeData.DerivedFromTemplate]
  implicit val connectionContentEncoder6: Encoder[EdgeData.DerivedFromTemplate] = deriveEncoder[EdgeData.DerivedFromTemplate]
  implicit val connectionContentDecoder7: Decoder[EdgeData.Read] = deriveDecoder[EdgeData.Read]
  implicit val connectionContentEncoder7: Encoder[EdgeData.Read] = deriveEncoder[EdgeData.Read]
  implicit val connectionContentDecoder8: Decoder[EdgeData.Expanded] = deriveDecoder[EdgeData.Expanded]
  implicit val connectionContentEncoder8: Encoder[EdgeData.Expanded] = deriveEncoder[EdgeData.Expanded]
  implicit val connectionContentDecoder9: Decoder[EdgeData.Mention] = deriveDecoder[EdgeData.Mention]
  implicit val connectionContentEncoder9: Encoder[EdgeData.Mention] = deriveEncoder[EdgeData.Mention]
  implicit val connectionContentDecoder10: Decoder[EdgeData.ReferencesTemplate] = deriveDecoder[EdgeData.ReferencesTemplate]
  implicit val connectionContentEncoder10: Encoder[EdgeData.ReferencesTemplate] = deriveEncoder[EdgeData.ReferencesTemplate]
  implicit val connectionContentDecoder: Decoder[EdgeData] = deriveDecoder[EdgeData]
  implicit val connectionContentEncoder: Encoder[EdgeData] = deriveEncoder[EdgeData]

  implicit val viewVisibleEncoder: Encoder[View.Visible] = deriveEncoder[View.Visible]
  implicit val viewVisibleDecoder: Decoder[View.Visible] = deriveDecoder[View.Visible]

  implicit val FeatureDecoder: Decoder[Feature] = deriveDecoder[Feature]
  implicit val FeatureEncoder: Encoder[Feature] = deriveEncoder[Feature]
}
object Circe extends Circe
