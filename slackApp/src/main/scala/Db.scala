package wust.slack

import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import com.typesafe.config.{Config => TConfig}
import wust.ids._
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import io.getquill._
import wust.api.Authentication
import wust.ids.NodeId

object Db {
  def apply(config: TConfig) = {
    new Db(new PostgresAsyncContext(LowerCase, config))
  }
}

object Data {
  case class Team_Mapping(slack_team_id: String, wust_id: NodeId)
  case class User_Mapping(slack_user_id: String, wust_id: NodeId, slack_token: AccessToken, wust_token: Authentication.Verified)
  case class Conversation_Mapping(slack_conversation_id: String, wust_id: NodeId)
  case class Message_Mapping(slack_channel_id: String, slack_message_ts: String, wust_id: NodeId)
}

class DbCodecs(val ctx: PostgresAsyncContext[LowerCase]) {
  import ctx._

  implicit val encodingNodeId: MappedEncoding[NodeId, UUID] = MappedEncoding(_.toUuid)
  implicit val decodingNodeId: MappedEncoding[UUID, NodeId] =
    MappedEncoding(uuid => NodeId(Cuid.fromUuid(uuid)))
  implicit val encodingUserId: MappedEncoding[UserId, UUID] = MappedEncoding(_.toUuid)
  implicit val decodingUserId: MappedEncoding[UUID, UserId] =
    MappedEncoding(uuid => UserId(NodeId(Cuid.fromUuid(uuid))))

}

class Db(override val ctx: PostgresAsyncContext[LowerCase]) extends DbCodecs(ctx) {
  import ctx._
  import Data._

  def getTeamMapping(teamId: String)(implicit ec: ExecutionContext): Future[Option[Team_Mapping]] = {
    ctx
      .run(query[Team_Mapping].filter(_.slack_team_id == lift(teamId)).take(1))
      .map(_.headOption)
  }

  def getChannelNode(teamId: String)(implicit ec: ExecutionContext): Future[Option[NodeId]] = {
    ctx
      .run(query[Team_Mapping].filter(_.slack_team_id == lift(teamId)).take(1).map(_.wust_id))
      .map(_.headOption)
  }

  def getSlackChannel(wustNodeId: NodeId)(implicit ec: ExecutionContext): Future[Option[String]] = {
    ctx
      .run(query[Team_Mapping].filter(_.wust_id == lift(wustNodeId)).take(1).map(_.slack_team_id))
      .map(_.headOption)
  }


}
