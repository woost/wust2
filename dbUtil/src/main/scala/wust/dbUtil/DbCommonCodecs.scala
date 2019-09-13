package wust.dbUtil

import java.util.{Date, UUID}

import io.circe.parser._
import io.circe.syntax._
import io.getquill._
import wust.ids._
import wust.ids.serialize.Circe._
import java.time.LocalDateTime

abstract class DbCommonCodecs(val ctx: PostgresAsyncContext[LowerCase]) {
  import ctx._

  implicit val encodingNodeId: MappedEncoding[NodeId, UUID] = MappedEncoding(_.toUuid)
  implicit val decodingNodeId: MappedEncoding[UUID, NodeId] =
    MappedEncoding(uuid => NodeId(Cuid.fromUuid(uuid)))
  implicit val encodingUserId: MappedEncoding[UserId, UUID] = MappedEncoding(_.toUuid)
  implicit val decodingUserId: MappedEncoding[UUID, UserId] =
    MappedEncoding(uuid => UserId(NodeId(Cuid.fromUuid(uuid))))

  private def encodeJson[T: io.circe.Encoder](json: T): String = json.asJson.noSpaces
  private def decodeJson[T: io.circe.Decoder](json: String): T = decode[T](json) match {
    case Right(v) => v
    case Left(e)  => throw new Exception(s"Failed to decode json: '$json': $e")
  }

  //TODO: quill PR: add these seq[UUID] encoder/decoder
  //TODO: quill PR: rename arrayRawEncoder To ...Decoder
  implicit def arrayUUIDDecoder[Col <: Seq[UUID]](implicit bf: CBF[UUID, Col]): Decoder[Col] =
    arrayRawEncoder[UUID, Col]
  implicit def arrayUUIDEncoder[Col <: Seq[UUID]]: Encoder[Col] = arrayRawEncoder[UUID, Col]

  implicit val encodingEdgeDataType: MappedEncoding[EdgeData.Type, String] =
    MappedEncoding(identity)
  implicit val decodingEdgeDataType: MappedEncoding[String, EdgeData.Type] =
    MappedEncoding(EdgeData.Type(_))
  implicit val encodingEdgeData: MappedEncoding[EdgeData, String] =
    MappedEncoding(encodeJson[EdgeData])
  implicit val decodingEdgeData: MappedEncoding[String, EdgeData] =
    MappedEncoding(decodeJson[EdgeData])

  implicit val encodingNodeDataType: MappedEncoding[NodeData.Type, String] = MappedEncoding(identity)
  implicit val decodingNodeDataType: MappedEncoding[String, NodeData.Type] = MappedEncoding(NodeData.Type(_))
  implicit def encodingNodeData[Data <: NodeData]: MappedEncoding[Data, String] = MappedEncoding(encodeJson[NodeData]) // encodeJson[PostData] is here on purpose, we want to serialize the base trait.
  implicit val decodingNodeData: MappedEncoding[String, NodeData] = MappedEncoding(decodeJson[NodeData])
  implicit val decodingNodeDataUser: MappedEncoding[String, NodeData.User] = MappedEncoding(decodeJson[NodeData.User]) // explicitly provided for query[User] where data has type PostData.User

  implicit val encodingNodeRole: MappedEncoding[NodeRole, String] = MappedEncoding(encodeJson[NodeRole]) // encodeJson[PostData] is here on purpose, we want to serialize the base trait.
  implicit val decodingNodeRole: MappedEncoding[String, NodeRole] = MappedEncoding(decodeJson[NodeRole])

  implicit val encodingOAuthClientService: MappedEncoding[OAuthClientService, String] = MappedEncoding(_.identifier)
  implicit val decodingOAuthClientService: MappedEncoding[String, OAuthClientService] = MappedEncoding(str => OAuthClientService.fromString(str).getOrElse(throw new Exception(s"Cannot decode OAuthClientService: $str")))

  // implicit val encodingEpochMilli: MappedEncoding[EpochMilli, Date] = MappedEncoding { d => new Date(d) }
  // implicit val decodingEpochMilli: MappedEncoding[Date, EpochMilli] = MappedEncoding { d => EpochMilli(d.toInstant.toEpochMilli) }

  // implicit val encodeEpochMilli: MappedEncoding[EpochMilli, LocalDateTime] = MappedEncoding { d => d.toLocalDateTime }
  // implicit val decodingEpochMilli: MappedEncoding[LocalDateTime, EpochMilli] = MappedEncoding { d => EpochMilli.fromLocalDateTime(d) }

  implicit val encodingNodeAccessLevel: MappedEncoding[NodeAccess, Option[String]] =
    MappedEncoding {
      case NodeAccess.Level(level) => Some(level.str)
      case NodeAccess.Inherited    => None
    }
  implicit val decodingNodeAccessLevel: MappedEncoding[Option[String], NodeAccess] =
    MappedEncoding {
      _.fold[NodeAccess](NodeAccess.Inherited)(AccessLevel.fromString andThen NodeAccess.Level)
    }

  implicit val encodingNodeSchema: MappedEncoding[NodeSchema, String] = MappedEncoding(encodeJson[NodeSchema])
  implicit val decodingNodeSchema: MappedEncoding[String, NodeSchema] = MappedEncoding(decodeJson[NodeSchema])

  implicit val encodingFeature: MappedEncoding[Feature, String] = MappedEncoding(encodeJson[Feature])
  implicit val decodingFeature: MappedEncoding[String, Feature] = MappedEncoding(decodeJson[Feature])

  implicit val encodingAccessLevel: MappedEncoding[AccessLevel, String] =
    MappedEncoding { _.str }
  implicit val decodingAccessLevel: MappedEncoding[String, AccessLevel] =
    MappedEncoding { AccessLevel.fromString }

  implicit class EpochMilliQuillOps(ldt: EpochMilli) {
    def > = ctx.quote((date: EpochMilli) => infix"$ldt > $date".as[Boolean])
    def >= = ctx.quote((date: EpochMilli) => infix"$ldt >= $date".as[Boolean])
    def < = ctx.quote((date: EpochMilli) => infix"$ldt < $date".as[Boolean])
    def <= = ctx.quote((date: EpochMilli) => infix"$ldt <= $date".as[Boolean])
  }

  implicit class JsonPostDataQuillOps(json: NodeData) {
    val ->> = ctx.quote((field: String) => infix"$json->>$field".as[String])
    val jsonType = ctx.quote(infix"$json->>'type'".as[NodeData.Type])
  }
  implicit class JsonConnectionDataQuillOps(json: EdgeData) {
    val ->> = ctx.quote((field: String) => infix"$json->>$field".as[String])
    val jsonType = ctx.quote(infix"$json->>'type'".as[EdgeData.Type])
  }
}
