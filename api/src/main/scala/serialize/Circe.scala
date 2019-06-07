package wust.api.serialize

import io.circe._
import io.circe.generic.extras.semiauto._
import wust.api._
import wust.graph._
import wust.ids._

object Circe extends wust.ids.serialize.Circe {

  implicit val PasswordDecoder: Decoder[Password] = Decoder[String].map(Password)
  implicit val PasswordEncoder: Encoder[Password] = Encoder[String].contramap(_.string)
  implicit val AuthTokenDecoder: Decoder[Authentication.Token] = Decoder[String].map(Authentication.Token)
  implicit val AuthTokenEncoder: Encoder[Authentication.Token] = Encoder[String].contramap(_.string)

  //TODO anyval with circe?
  implicit val nodeMetaDecoder: Decoder[NodeMeta] = deriveDecoder[NodeMeta]
  implicit val nodeMetaEncoder: Encoder[NodeMeta] = deriveEncoder[NodeMeta]
  implicit val PostDecoder: Decoder[Node] = deriveDecoder[Node]
  implicit val PostEncoder: Encoder[Node] = deriveEncoder[Node]
  implicit val ConnectionDecoder1: Decoder[Edge.Child] = deriveDecoder[Edge.Child]
  implicit val ConnectionEncoder1: Encoder[Edge.Child] = deriveEncoder[Edge.Child]
  implicit val ConnectionDecoder3: Decoder[Edge.Member] = deriveDecoder[Edge.Member]
  implicit val ConnectionEncoder3: Encoder[Edge.Member] = deriveEncoder[Edge.Member]
  implicit val ConnectionDecoder4: Decoder[Edge.Author] = deriveDecoder[Edge.Author]
  implicit val ConnectionEncoder4: Encoder[Edge.Author] = deriveEncoder[Edge.Author]
  implicit val connectionDecoder5: Decoder[Edge.LabeledProperty] = deriveDecoder[Edge.LabeledProperty]
  implicit val connectionEncoder5: Encoder[Edge.LabeledProperty] = deriveEncoder[Edge.LabeledProperty]
  implicit val connectionDecoder6: Decoder[Edge.DerivedFromTemplate] = deriveDecoder[Edge.DerivedFromTemplate]
  implicit val connectionEncoder6: Encoder[Edge.DerivedFromTemplate] = deriveEncoder[Edge.DerivedFromTemplate]
  implicit val ConnectionDecoder7: Decoder[Edge.Read] = deriveDecoder[Edge.Read]
  implicit val ConnectionEncoder7: Encoder[Edge.Read] = deriveEncoder[Edge.Read]
  implicit val ConnectionDecoder: Decoder[Edge] = deriveDecoder[Edge]
  implicit val ConnectionEncoder: Encoder[Edge] = deriveEncoder[Edge]

  implicit val UserAssumedDecoder: Decoder[AuthUser.Assumed] = deriveDecoder[AuthUser.Assumed]
  implicit val UserAssumedEncoder: Encoder[AuthUser.Assumed] = deriveEncoder[AuthUser.Assumed]
  implicit val UserVerifiedDecoder: Decoder[AuthUser.Persisted] = deriveDecoder[AuthUser.Persisted]
  implicit val UserVerifiedEncoder: Encoder[AuthUser.Persisted] = deriveEncoder[AuthUser.Persisted]
  implicit val UserImplicitDecoder: Decoder[AuthUser.Implicit] = deriveDecoder[AuthUser.Implicit]
  implicit val UserImplicitEncoder: Encoder[AuthUser.Implicit] = deriveEncoder[AuthUser.Implicit]
  implicit val userDecoder: Decoder[AuthUser] = deriveDecoder[AuthUser]
  implicit val userEncoder: Encoder[AuthUser] = deriveEncoder[AuthUser]
  implicit val AuthenticationDecoder: Decoder[Authentication] = deriveDecoder[Authentication]
  implicit val AuthenticationEncoder: Encoder[Authentication] = deriveEncoder[Authentication]
  implicit val GraphChangesDecoder: Decoder[GraphChanges] = deriveDecoder[GraphChanges]
  implicit val GraphChangesEncoder: Encoder[GraphChanges] = deriveEncoder[GraphChanges]

  implicit val connectionContentTypeKeyDecoder: KeyDecoder[EdgeData.Type] =
    KeyDecoder[String].map(EdgeData.Type(_))
  implicit val connectionContentTypeKeyEncoder: KeyEncoder[EdgeData.Type] =
    KeyEncoder[String].contramap[EdgeData.Type](identity)

  implicit val graphDecoder: Decoder[Graph] = Decoder.instance[Graph] { cursor =>
    val nodes = cursor.downField("nodes")
    val edges = cursor.downField("edges")
    for {
      nodes <- nodes.as[Array[Node]]
      edges <- edges.as[Array[Edge]]
    } yield Graph(nodes, edges)
  }
  implicit val graphEncoder: Encoder[Graph] = Encoder.instance[Graph] { graph =>
    import io.circe.syntax._
    Json.obj(
      "nodes" -> Json.fromValues(graph.nodes.map(_.asJson)),
      "edges" -> Json.fromValues(graph.edges.map(_.asJson))
    )
  }
}
