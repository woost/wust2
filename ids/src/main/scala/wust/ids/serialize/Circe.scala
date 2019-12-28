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

  implicit val EmailAddressDecoder: Decoder[EmailAddress] = Decoder.decodeString.emap(str => Right(EmailAddress(str)))
  implicit val EmailAddressEncoder: Encoder[EmailAddress] = email => Json.fromString(email.value)

  // decode accesslevel as string instead of
  implicit val AccessLevelDecoder: Decoder[AccessLevel] =
    Decoder.decodeString.emap(
      s => AccessLevel.fromString.lift(s).toRight(s"Is not an access level: $s")
    )
  implicit val AccessLevelEncoder: Encoder[AccessLevel] = level => Json.fromString(level.str)
  implicit val nodeAccessDecoder: Decoder[NodeAccess] = deriveConfiguredDecoder[NodeAccess]
  implicit val nodeAccessEncoder: Encoder[NodeAccess] = deriveConfiguredEncoder[NodeAccess]

  implicit val NodeTypeSelectionDecoder: Decoder[NodeTypeSelection] = deriveConfiguredDecoder[NodeTypeSelection]
  implicit val NodeTypeSelectionEncoder: Encoder[NodeTypeSelection] = deriveConfiguredEncoder[NodeTypeSelection]

  implicit val KanbanSettingsDecoder: Decoder[KanbanSettings] = deriveConfiguredDecoder[KanbanSettings]
  implicit val KanbanSettingsEncoder: Encoder[KanbanSettings] = deriveConfiguredEncoder[KanbanSettings]
  implicit val NodeSettingsDecoder: Decoder[NodeSettings] = deriveConfiguredDecoder[NodeSettings]
  implicit val NodeSettingsEncoder: Encoder[NodeSettings] = deriveConfiguredEncoder[NodeSettings]

  implicit val postContentDecoder2: Decoder[NodeData.Content] = deriveConfiguredDecoder[NodeData.Content]
  implicit val postContentEncoder2: Encoder[NodeData.Content] = deriveConfiguredEncoder[NodeData.Content]
  implicit val postContentDecoder3: Decoder[NodeData.User] = deriveConfiguredDecoder[NodeData.User]
  implicit val postContentEncoder3: Encoder[NodeData.User] = deriveConfiguredEncoder[NodeData.User]
  implicit val postContentDecoder: Decoder[NodeData] = deriveConfiguredDecoder[NodeData]
  implicit val postContentEncoder: Encoder[NodeData] = deriveConfiguredEncoder[NodeData]

  implicit val nodeRoleDecoder: Decoder[NodeRole] = deriveConfiguredDecoder[NodeRole]
  implicit val nodeRoleEncoder: Encoder[NodeRole] = deriveConfiguredEncoder[NodeRole]
  implicit val connectionContentDecoder2: Decoder[EdgeData.Child] = deriveConfiguredDecoder[EdgeData.Child]
  implicit val connectionContentEncoder2: Encoder[EdgeData.Child] = deriveConfiguredEncoder[EdgeData.Child]
  implicit val connectionContentDecoder3: Decoder[EdgeData.Member] = deriveConfiguredDecoder[EdgeData.Member]
  implicit val connectionContentEncoder3: Encoder[EdgeData.Member] = deriveConfiguredEncoder[EdgeData.Member]
  implicit val connectionContentDecoder4: Decoder[EdgeData.Author] = deriveConfiguredDecoder[EdgeData.Author]
  implicit val connectionContentEncoder4: Encoder[EdgeData.Author] = deriveConfiguredEncoder[EdgeData.Author]
  implicit val connectionContentDecoder5: Decoder[EdgeData.LabeledProperty] = deriveConfiguredDecoder[EdgeData.LabeledProperty]
  implicit val connectionContentEncoder5: Encoder[EdgeData.LabeledProperty] = deriveConfiguredEncoder[EdgeData.LabeledProperty]
  implicit val connectionContentDecoder6: Decoder[EdgeData.DerivedFromTemplate] = deriveConfiguredDecoder[EdgeData.DerivedFromTemplate]
  implicit val connectionContentEncoder6: Encoder[EdgeData.DerivedFromTemplate] = deriveConfiguredEncoder[EdgeData.DerivedFromTemplate]
  implicit val connectionContentDecoder7: Decoder[EdgeData.Read] = deriveConfiguredDecoder[EdgeData.Read]
  implicit val connectionContentEncoder7: Encoder[EdgeData.Read] = deriveConfiguredEncoder[EdgeData.Read]
  implicit val connectionContentDecoder8: Decoder[EdgeData.Expanded] = deriveConfiguredDecoder[EdgeData.Expanded]
  implicit val connectionContentEncoder8: Encoder[EdgeData.Expanded] = deriveConfiguredEncoder[EdgeData.Expanded]
  implicit val connectionContentDecoder9: Decoder[EdgeData.Mention] = deriveConfiguredDecoder[EdgeData.Mention]
  implicit val connectionContentEncoder9: Encoder[EdgeData.Mention] = deriveConfiguredEncoder[EdgeData.Mention]
  implicit val connectionContentDecoder10: Decoder[EdgeData.ReferencesTemplate] = deriveConfiguredDecoder[EdgeData.ReferencesTemplate]
  implicit val connectionContentEncoder10: Encoder[EdgeData.ReferencesTemplate] = deriveConfiguredEncoder[EdgeData.ReferencesTemplate]
  implicit val connectionContentDecoder: Decoder[EdgeData] = deriveConfiguredDecoder[EdgeData]
  implicit val connectionContentEncoder: Encoder[EdgeData] = deriveConfiguredEncoder[EdgeData]

  implicit val PaymentPlanEncoder: Encoder[PaymentPlan] = deriveConfiguredEncoder[PaymentPlan]
  implicit val PaymentPlanDecoder: Decoder[PaymentPlan] = deriveConfiguredDecoder[PaymentPlan]

  implicit val viewVisibleEncoder: Encoder[View.Visible] = deriveConfiguredEncoder[View.Visible]
  implicit val viewVisibleDecoder: Decoder[View.Visible] = deriveConfiguredDecoder[View.Visible]

  implicit val FeatureDecoder: Decoder[Feature] = deriveConfiguredDecoder[Feature]
  implicit val FeatureEncoder: Encoder[Feature] = deriveConfiguredEncoder[Feature]
}
object Circe extends Circe
