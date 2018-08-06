package wust.slack

import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import com.typesafe.config.{Config => TConfig}
import wust.ids._
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import io.getquill._
import wust.api.Authentication
import wust.dbUtil.DbCommonCodecs
import wust.graph.Node.User
import wust.ids.NodeId
import wust.util._

object Db {
  def apply(config: TConfig) = {
    new Db(new PostgresAsyncContext(LowerCase, config))
  }
}

object Data {
  //  case class WustUserData(wustUserId: UserId, wustUserToken: Authentication.Token)
  //  case class SlackUserData(slackUserId: String, slackUserToken: AccessToken)
  case class WustUserData(wustUserId: UserId, wustUserToken: String)
  case class SlackUserData(slackUserId: String, slackUserToken: Option[String])

  case class Team_Mapping(slack_team_id: String, wust_id: NodeId)
  //  case class User_Mapping(slack_user_id: String, wust_id: NodeId, slack_token: Option[AccessToken], wust_token: Authentication.Verified)
  case class User_Mapping(slack_user_id: String, wust_id: UserId, slack_token: Option[String], wust_token: String)
  case class Conversation_Mapping(slack_conversation_id: String, wust_id: NodeId)
  case class Message_Mapping(slack_channel_id: String, slack_message_ts: String, wust_id: NodeId)
}

class DbSlackCodecs(override val ctx: PostgresAsyncContext[LowerCase]) extends DbCommonCodecs(ctx) {
}

class Db(override val ctx: PostgresAsyncContext[LowerCase]) extends DbSlackCodecs(ctx) {
  import ctx._
  import Data._

  // schema meta: we can define how a type corresponds to a db table
  private implicit val userSchema = schemaMeta[User]("node") // User type is stored in node table with same properties.
  // enforce check of json-type for extra safety. additional this makes sure that partial indices on user.data are used.
  private val queryUser = quote { query[User].filter(_.data.jsonType == lift(NodeData.User.tpe)) }

  def storeOrUpdateUserMapping(userMapping: User_Mapping)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = quote {
      query[User_Mapping].insert(lift(userMapping))
        .onConflictUpdate(_.slack_user_id, _.wust_id)(
          (t, e) => t.slack_token -> e.slack_token,
          (t, e) => t.wust_token -> e.wust_token
        )
    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }

  def getWustUser(slackUserId: String)(implicit ec: ExecutionContext): Future[Option[WustUserData]] = {
    ctx
      .run(query[User_Mapping].filter(_.slack_user_id == lift(slackUserId)).take(1))
      .map(_.headOption.map(u => WustUserData(u.wust_id, u.wust_token)))
  }

  def getSlackUser(wustUserId: UserId)(implicit ec: ExecutionContext): Future[Option[SlackUserData]] = {
    ctx
      .run(query[User_Mapping].filter(_.wust_id == lift(wustUserId)).take(1))
      .map(_.headOption.map(u => SlackUserData(u.slack_user_id, u.slack_token)))
  }

  def storeTeamMapping(teamMapping: Team_Mapping)(implicit ec: ExecutionContext): Future[Boolean]  = {
    val q = quote {
      query[Team_Mapping].insert(lift(teamMapping))
        .onConflictUpdate(_.slack_team_id, _.wust_id)(
          (t, e) => t.slack_team_id -> e.slack_team_id,
          (t, e) => t.wust_id -> e.wust_id
        )

    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }

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

  def storeMessageMapping(messageMapping: Message_Mapping)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = quote {
      query[Message_Mapping].insert(lift(messageMapping))
        .onConflictUpdate(_.slack_channel_id, _.slack_message_ts, _.wust_id)(
          (t, e) => t.slack_message_ts -> e.slack_message_ts
        )
    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }

  def getMessageFromSlackData(channel: String, timestamp: String)(implicit ec: ExecutionContext): Future[Option[NodeId]] = {
    ctx
      .run(query[Message_Mapping].filter(m => m.slack_channel_id == lift(channel) && m.slack_message_ts == lift(timestamp)).take(1).map(_.wust_id))
      .map(_.headOption)
  }

}
