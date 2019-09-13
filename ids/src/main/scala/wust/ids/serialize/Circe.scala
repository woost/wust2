package wust.ids.serialize

import java.util.UUID

import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
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

  implicit val NodeIdDecoder: Decoder[NodeId] = Decoder[Cuid].asInstanceOf[Decoder[NodeId]]
  implicit val NodeIdEncoder: Encoder[NodeId] = Encoder[Cuid].asInstanceOf[Encoder[NodeId]]
  implicit val UserIdDecoder: Decoder[UserId] = Decoder[Cuid].asInstanceOf[Decoder[UserId]]
  implicit val UserIdEncoder: Encoder[UserId] = Encoder[Cuid].asInstanceOf[Encoder[UserId]]
  implicit val TemplateIdDecoder: Decoder[TemplateId] = Decoder[Cuid].asInstanceOf[Decoder[TemplateId]]
  implicit val TemplateIdEncoder: Encoder[TemplateId] = Encoder[Cuid].asInstanceOf[Encoder[TemplateId]]
  implicit val PropertyIdDecoder: Decoder[PropertyId] = Decoder[Cuid].asInstanceOf[Decoder[PropertyId]]
  implicit val PropertyIdEncoder: Encoder[PropertyId] = Encoder[Cuid].asInstanceOf[Encoder[PropertyId]]
  implicit val ChildIdDecoder: Decoder[ChildId] = Decoder[Cuid].asInstanceOf[Decoder[ChildId]]
  implicit val ChildIdEncoder: Encoder[ChildId] = Encoder[Cuid].asInstanceOf[Encoder[ChildId]]
  implicit val ParentIdDecoder: Decoder[ParentId] = Decoder[Cuid].asInstanceOf[Decoder[ParentId]]
  implicit val ParentIdEncoder: Encoder[ParentId] = Encoder[Cuid].asInstanceOf[Encoder[ParentId]]

  // decode accesslevel as string instead of
  implicit val AccessLevelDecoder: Decoder[AccessLevel] =
    Decoder.decodeString.emap(
      s => AccessLevel.fromString.lift(s).toRight(s"Is not an access level: $s")
    )
  implicit val AccessLevelEncoder: Encoder[AccessLevel] = level => Json.fromString(level.str)
  implicit val nodeAccessDecoder: Decoder[NodeAccess] = deriveDecoder[NodeAccess]
  implicit val nodeAccessEncoder: Encoder[NodeAccess] = deriveEncoder[NodeAccess]

  implicit val epochMilliDecoder2: Decoder[EpochMilli] = implicitly[Decoder[Long]].asInstanceOf[Decoder[EpochMilli]]
  implicit val epochMilliEncoder2: Encoder[EpochMilli] = implicitly[Encoder[Long]].asInstanceOf[Encoder[EpochMilli]]
  implicit val DurationMilliDecoder2: Decoder[DurationMilli] = implicitly[Decoder[Long]].asInstanceOf[Decoder[DurationMilli]]
  implicit val DurationMilliEncoder2: Encoder[DurationMilli] = implicitly[Encoder[Long]].asInstanceOf[Encoder[DurationMilli]]
  implicit val DateTimeMilliDecoder2: Decoder[DateTimeMilli] = implicitly[Decoder[Long]].asInstanceOf[Decoder[DateTimeMilli]]
  implicit val DateTimeMilliEncoder2: Encoder[DateTimeMilli] = implicitly[Encoder[Long]].asInstanceOf[Encoder[DateTimeMilli]]
  implicit val DateMilliDecoder2: Decoder[DateMilli] = implicitly[Decoder[Long]].asInstanceOf[Decoder[DateMilli]]
  implicit val DateMilliEncoder2: Encoder[DateMilli] = implicitly[Encoder[Long]].asInstanceOf[Encoder[DateMilli]]

  // implicit val postContentDecoder2: Decoder[NodeData.Content] = deriveDecoder[NodeData.Content]
  // implicit val postContentEncoder2: Encoder[NodeData.Content] = deriveEncoder[NodeData.Content]
  // implicit val postContentDecoder3: Decoder[NodeData.User] = deriveDecoder[NodeData.User]
  // implicit val postContentEncoder3: Encoder[NodeData.User] = deriveEncoder[NodeData.User]
  implicit val NodeDataTypeDecoder: Decoder[NodeData.Type] = implicitly[Decoder[String]].asInstanceOf[Decoder[NodeData.Type]]
  implicit val NodeDataTypeEncoder: Encoder[NodeData.Type] = implicitly[Encoder[String]].asInstanceOf[Encoder[NodeData.Type]]
  implicit val NodeDataDecoder: Decoder[NodeData] = deriveDecoder[NodeData]
  implicit val NodeDataEncoder: Encoder[NodeData] = deriveEncoder[NodeData]

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
  implicit val EdgeDataTypeDecoder: Decoder[EdgeData.Type] = implicitly[Decoder[String]].asInstanceOf[Decoder[EdgeData.Type]]
  implicit val EdgeDataTypeEncoder: Encoder[EdgeData.Type] = implicitly[Encoder[String]].asInstanceOf[Encoder[EdgeData.Type]]
  implicit val connectionContentDecoder: Decoder[EdgeData] = deriveDecoder[EdgeData]
  implicit val connectionContentEncoder: Encoder[EdgeData] = deriveEncoder[EdgeData]

  implicit val viewVisibleEncoder: Encoder[View.Visible] = deriveEncoder[View.Visible]
  implicit val viewVisibleDecoder: Decoder[View.Visible] = deriveDecoder[View.Visible]

  implicit val FeatureDecoder: Decoder[Feature] = deriveDecoder[Feature]
  implicit val FeatureEncoder: Encoder[Feature] = deriveEncoder[Feature]
}
object Circe extends Circe
