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
  implicit val nodeMetaDecoder: Decoder[NodeMeta] = deriveConfiguredDecoder[NodeMeta]
  implicit val nodeMetaEncoder: Encoder[NodeMeta] = deriveConfiguredEncoder[NodeMeta]
  implicit val PostDecoder: Decoder[Node] = deriveConfiguredDecoder[Node]
  implicit val PostEncoder: Encoder[Node] = deriveConfiguredEncoder[Node]
  implicit val ConnectionDecoder1: Decoder[Edge.Child] = deriveConfiguredDecoder[Edge.Child]
  implicit val ConnectionEncoder1: Encoder[Edge.Child] = deriveConfiguredEncoder[Edge.Child]
  implicit val ConnectionDecoder3: Decoder[Edge.Member] = deriveConfiguredDecoder[Edge.Member]
  implicit val ConnectionEncoder3: Encoder[Edge.Member] = deriveConfiguredEncoder[Edge.Member]
  implicit val ConnectionDecoder4: Decoder[Edge.Author] = deriveConfiguredDecoder[Edge.Author]
  implicit val ConnectionEncoder4: Encoder[Edge.Author] = deriveConfiguredEncoder[Edge.Author]
  implicit val connectionDecoder5: Decoder[Edge.LabeledProperty] = deriveConfiguredDecoder[Edge.LabeledProperty]
  implicit val connectionEncoder5: Encoder[Edge.LabeledProperty] = deriveConfiguredEncoder[Edge.LabeledProperty]
  implicit val connectionDecoder6: Decoder[Edge.DerivedFromTemplate] = deriveConfiguredDecoder[Edge.DerivedFromTemplate]
  implicit val connectionEncoder6: Encoder[Edge.DerivedFromTemplate] = deriveConfiguredEncoder[Edge.DerivedFromTemplate]
  implicit val ConnectionDecoder7: Decoder[Edge.Read] = deriveConfiguredDecoder[Edge.Read]
  implicit val ConnectionEncoder7: Encoder[Edge.Read] = deriveConfiguredEncoder[Edge.Read]
  implicit val ConnectionDecoder8: Decoder[Edge.Mention] = deriveConfiguredDecoder[Edge.Mention]
  implicit val ConnectionEncoder8: Encoder[Edge.Mention] = deriveConfiguredEncoder[Edge.Mention]
  implicit val ConnectionDecoder: Decoder[Edge] = deriveConfiguredDecoder[Edge]
  implicit val ConnectionEncoder: Encoder[Edge] = deriveConfiguredEncoder[Edge]

  implicit val UserAssumedDecoder: Decoder[AuthUser.Assumed] = deriveConfiguredDecoder[AuthUser.Assumed]
  implicit val UserAssumedEncoder: Encoder[AuthUser.Assumed] = deriveConfiguredEncoder[AuthUser.Assumed]
  implicit val UserVerifiedDecoder: Decoder[AuthUser.Persisted] = deriveConfiguredDecoder[AuthUser.Persisted]
  implicit val UserVerifiedEncoder: Encoder[AuthUser.Persisted] = deriveConfiguredEncoder[AuthUser.Persisted]
  implicit val UserImplicitDecoder: Decoder[AuthUser.Implicit] = deriveConfiguredDecoder[AuthUser.Implicit]
  implicit val UserImplicitEncoder: Encoder[AuthUser.Implicit] = deriveConfiguredEncoder[AuthUser.Implicit]
  implicit val userDecoder: Decoder[AuthUser] = deriveConfiguredDecoder[AuthUser]
  implicit val userEncoder: Encoder[AuthUser] = deriveConfiguredEncoder[AuthUser]
  implicit val AuthenticationDecoder: Decoder[Authentication] = deriveConfiguredDecoder[Authentication]
  implicit val AuthenticationEncoder: Encoder[Authentication] = deriveConfiguredEncoder[Authentication]
  implicit val GraphChangesDecoder: Decoder[GraphChanges] = deriveConfiguredDecoder[GraphChanges]
  implicit val GraphChangesEncoder: Encoder[GraphChanges] = deriveConfiguredEncoder[GraphChanges]

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
