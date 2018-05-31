package wust.db


import io.getquill._
import io.circe.parser._
import io.circe.syntax._
import supertagged._
import wust.ids._
import wust.ids.serialize.Circe._
import wust.util._

import java.util.Date

// Converters between scala classes and database entities
// Quill needs an implicit encoder/decoder for each occuring type.
// encoder/decoders for primitives and case classes are provided by quill.
class DbCodecs(val ctx: PostgresAsyncContext[LowerCase]) {
  import Data._
  import ctx._

  private def encodeJson[T : io.circe.Encoder](json: T): String = json.asJson.noSpaces
  private def decodeJson[T : io.circe.Decoder](json: String): T = decode[T](json) match {
    case Right(v) => v
    case Left(e) => throw new Exception(s"Failed to decode json: '$json': $e")
  }

  implicit val encodingNodeId: MappedEncoding[NodeId, String] = MappedEncoding[NodeId, String](identity)
  implicit val decodingNodeId: MappedEncoding[String, NodeId] = MappedEncoding[String, NodeId](NodeId(_))
  implicit val encodingUserId: MappedEncoding[UserId, String] = MappedEncoding[UserId, String](identity)
  implicit val decodingUserId: MappedEncoding[String, UserId] = MappedEncoding[String, UserId](id => UserId(NodeId(id)))

  implicit val encodingEdgeDataType: MappedEncoding[EdgeData.Type, String] = MappedEncoding[EdgeData.Type, String](identity)
  implicit val decodingEdgeDataType: MappedEncoding[String, EdgeData.Type] = MappedEncoding[String, EdgeData.Type](EdgeData.Type(_))
  implicit val encodingEdgeData: MappedEncoding[EdgeData, String] = MappedEncoding[EdgeData, String](encodeJson[EdgeData])
  implicit val decodingEdgeData: MappedEncoding[String, EdgeData] = MappedEncoding[String, EdgeData](decodeJson[EdgeData])

  implicit val encodingNodeDataType: MappedEncoding[NodeData.Type, String] = MappedEncoding[NodeData.Type, String](identity)
  implicit val decodingNodeDataType: MappedEncoding[String, NodeData.Type] = MappedEncoding[String, NodeData.Type](NodeData.Type(_))
  implicit def encodingNodeData[Data <: NodeData]: MappedEncoding[Data, String] = MappedEncoding[Data, String](encodeJson[NodeData]) // encodeJson[PostData] is here on purpose, we want to serialize the base trait.
  implicit val decodingNodData: MappedEncoding[String, NodeData] = MappedEncoding[String, NodeData](decodeJson[NodeData])
  implicit val decodingNodeDataUser: MappedEncoding[String, NodeData.User] = MappedEncoding[String, NodeData.User](decodeJson[NodeData.User]) // explicitly provided for query[User] where data has type PostData.User

  implicit val encodingEpochMilli: MappedEncoding[EpochMilli, Date] = MappedEncoding[EpochMilli, Date] { d => new Date(d) }
  implicit val decodingEpochMilli: MappedEncoding[Date, EpochMilli] = MappedEncoding[Date, EpochMilli] { d => EpochMilli(d.toInstant.toEpochMilli) }

  implicit val encodingDeletedDate: MappedEncoding[DeletedDate, Date] = MappedEncoding[DeletedDate, Date] { d => encodingEpochMilli.f(d.timestamp) }
  implicit val decodingDeletedDate: MappedEncoding[Date, DeletedDate] = MappedEncoding[Date, DeletedDate] { d => DeletedDate.from(decodingEpochMilli.f(d)) }
  implicit val encodingJoinDate: MappedEncoding[JoinDate, Date] = MappedEncoding[JoinDate, Date] { d => encodingEpochMilli.f(d.timestamp) }
  implicit val decodingJoinDate: MappedEncoding[Date, JoinDate] = MappedEncoding[Date, JoinDate] { d => JoinDate.from(decodingEpochMilli.f(d)) }
  implicit val encodingAccessLevel: MappedEncoding[AccessLevel, String] = MappedEncoding[AccessLevel, String] { _.str }
  implicit val decodingAccessLevel: MappedEncoding[String, AccessLevel] = MappedEncoding[String, AccessLevel] { AccessLevel.from }

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
  implicit class IngoreDuplicateKey[T](q: Insert[T]) {
    //TODO: https://github.com/getquill/quill#postgres
    def ignoreDuplicates = quote(infix"$q ON CONFLICT DO NOTHING".as[Insert[T]])
  }
}
