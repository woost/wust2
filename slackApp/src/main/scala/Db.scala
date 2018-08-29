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
  type SlackTeamId = String
  type SlackChannelId = String
  type SlackGroupId = String
  type SlackTimestamp = String
  type SlackUserId = String

  //  case class WustUserData(wustUserId: UserId, wustUserToken: Authentication.Token)
  //  case class SlackUserData(slackUserId: String, slackUserToken: AccessToken)
  case class WustUserData(
    wustUserId: UserId,
    wustUserToken: String
  )
  case class SlackUserData(
    slackUserId: SlackUserId,
    slackUserToken: Option[String]
  )

  //  case class User_Mapping(slack_user_id: String, wust_id: NodeId, slack_token: Option[AccessToken], wust_token: Authentication.Verified)
  case class User_Mapping(
    slack_user_id: SlackUserId,
    wust_id: UserId,
    slack_token: Option[String],
    wust_token: String
  )
  case class Team_Mapping(
    slack_team_id: Option[SlackTeamId],
    slack_team_name: String,
    wust_id: NodeId
  )
  case class Channel_Mapping(
    slack_channel_id: Option[SlackChannelId],
    slack_channel_name: String,
    is_private: Boolean,
    is_archived: Boolean,
    wust_id: NodeId,
    team_wust_id: NodeId
  )
  case class Message_Mapping(
    slack_channel_id: Option[SlackChannelId],
    slack_message_ts: Option[SlackTimestamp],
    slack_thread_ts: Option[SlackTimestamp],
    is_deleted: Boolean,
    slack_message_text: String,
    wust_id: NodeId,
    channel_wust_id: NodeId
  )
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




  // Store
  def storeOrUpdateUserMapping(userMapping: User_Mapping)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = quote {
      query[User_Mapping].insert(lift(userMapping))
        .onConflictUpdate(_.slack_user_id)(
          (t, e) => t.slack_token -> e.slack_token,
          (t, e) => t.wust_token -> e.wust_token
        )
    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }

  def storeOrUpdateTeamMapping(teamMapping: Team_Mapping)(implicit ec: ExecutionContext): Future[Boolean]  = {
    val q = quote {
      query[Team_Mapping].insert(lift(teamMapping))
        .onConflictUpdate(_.slack_team_id, _.wust_id)(
          (t, e) => t.slack_team_name -> e.slack_team_name,
        )
    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }

  def storeOrUpdateChannelMapping(channelMapping: Channel_Mapping)(implicit ec: ExecutionContext): Future[Boolean]  = {
    val q = quote {
      query[Channel_Mapping].insert(lift(channelMapping))
        .onConflictUpdate(_.slack_channel_id, _.wust_id)(
          (t, e) => t.slack_channel_name -> e.slack_channel_name,
          (t, e) => t.is_archived -> e.is_archived,
          (t, e) => t.is_private -> e.is_private,
        )

    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }

  def storeOrUpdateMessageMapping(messageMapping: Message_Mapping)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = quote {
      query[Message_Mapping].insert(lift(messageMapping))
        .onConflictUpdate(_.slack_channel_id, _.slack_message_ts, _.wust_id)(
          (t, e) => t.slack_message_ts -> e.slack_message_ts,
          (t, e) => t.slack_thread_ts -> e.slack_thread_ts,
          (t, e) => t.slack_message_text -> e.slack_message_text,
          (t, e) => t.slack_deleted_flag -> e.slack_deleted_flag
        )
    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }


  // Update
  def updateChannelMapping(channelMapping: Channel_Mapping)(implicit ec: ExecutionContext): Future[Boolean]  = {
    val q = quote {
      query[Channel_Mapping].filter(_.wust_id == lift(channelMapping.wust_id)).update(lift(channelMapping))
    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }

  def updateMessageMapping(messageMapping: Message_Mapping)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = quote {
      query[Message_Mapping].filter(m =>
        m.wust_id == lift(messageMapping.wust_id)
      ).update(lift(messageMapping))
    }

    ctx.run(q)
      .map(_ >= 1)
      .recoverValue(false)
  }


  // Delete
  def deleteChannelBySlackId(channelId: SlackChannelId)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(
        query[Channel_Mapping].filter(_.slack_channel_id.getOrElse("") == lift(channelId)).update(_.is_archived -> true)
      )
      .map(_ >= 1)
      .recoverValue(false)
  }

  def unDeleteChannelBySlackId(channelId: SlackChannelId)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(
        query[Channel_Mapping].filter(_.slack_channel_id.getOrElse("") == lift(channelId)).update(_.is_archived -> false)
      )
      .map(_ >= 1)
      .recoverValue(false)
  }

  def deleteMessageBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(
        query[Message_Mapping].filter( m =>
          m.slack_channel_id.getOrElse("") == lift(channelId) && m.slack_message_ts.getOrElse("") == lift(timestamp)
        ).update(_.slack_deleted_flag -> true)
      )
      .map(_ >= 1)
      .recoverValue(false)
  }



  // Queries
  // Query Wust NodeId by Slack Id
  def getTeamNodeBySlackId(teamId: SlackTeamId)(implicit ec: ExecutionContext): Future[Option[NodeId]] = {
    ctx
      .run(query[Team_Mapping].filter(_.slack_team_id.getOrElse("") == lift(teamId)).take(1).map(_.wust_id))
      .map(_.headOption)
  }

  def getChannelNodeBySlackId(channelId: SlackChannelId)(implicit ec: ExecutionContext): Future[Option[NodeId]] = {
    ctx
      .run(query[Channel_Mapping].filter(_.slack_channel_id.getOrElse("") == lift(channelId)).take(1).map(_.wust_id))
      .map(_.headOption)
  }

  def getMessageNodeBySlackIdData(channel: SlackChannelId, timestamp: SlackTimestamp)(implicit ec: ExecutionContext): Future[Option[NodeId]] = {
    ctx
      .run(query[Message_Mapping].filter(m => m.slack_channel_id.getOrElse("") == lift(channel) && m.slack_message_ts.getOrElse("") == lift(timestamp)).take(1).map(_.wust_id))
      .map(_.headOption)
  }


  // Query Slack Id by Wust NodeId
  def getSlackChannelByWustId(wustNodeId: NodeId)(implicit ec: ExecutionContext): Future[Option[SlackChannelId]] = {
    ctx
      .run(query[Channel_Mapping].filter(_.wust_id == lift(wustNodeId)).take(1).map(_.slack_channel_id))
      .map(_.headOption.flatten)
  }


  // Query Data by Wust Id
  def getWustUserDataBySlackUserId(slackUserId: SlackUserId)(implicit ec: ExecutionContext): Future[Option[WustUserData]] = {
    ctx
      .run(query[User_Mapping].filter(_.slack_user_id == lift(slackUserId)).take(1))
      .map(_.headOption.map(u => WustUserData(u.wust_id, u.wust_token)))
  }

  def getSlackUserDataByWustId(wustUserId: UserId)(implicit ec: ExecutionContext): Future[Option[SlackUserData]] = {
    ctx
      .run(query[User_Mapping].filter(_.wust_id == lift(wustUserId)).take(1))
      .map(_.headOption.map(u => SlackUserData(u.slack_user_id, u.slack_token)))
  }

  def getTeamMappingByWustId(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Option[Team_Mapping]] = {
    ctx
      .run(query[Team_Mapping].filter(_.wust_id == lift(nodeId)).take(1))
      .map(_.headOption)
  }

  def getChannelMappingByWustId(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Option[Channel_Mapping]] = {
    ctx
      .run(query[Channel_Mapping].filter(_.wust_id == lift(nodeId)).take(1))
      .map(_.headOption)
  }

  def getMessageMappingByWustId(wustId: NodeId)(implicit ec: ExecutionContext): Future[Option[Message_Mapping]] = {
    ctx
      .run(query[Message_Mapping].filter(_.wust_id == lift(wustId)).take(1))
      .map(_.headOption)
  }


  // Guards
  def channelExistsByNameAndTeam(teamId: SlackTeamId, channelName: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val q = quote {
      for {
        c <- query[Channel_Mapping].filter(_.slack_channel_name == lift(channelName))
        t <- query[Team_Mapping].filter(_.wust_id == c.team_wust_id)
      } yield c.wust_id
    }

    ctx
      .run(q.nonEmpty)
  }

  def isChannelDeletedBySlackId(channelId: SlackChannelId)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Channel_Mapping].filter(t => t.slack_channel_id.getOrElse("") == lift(channelId) && t.is_archived).nonEmpty)
  }

  def isChannelUpToDateBySlackDataElseGetNodes(channelId: SlackChannelId, name: String)(implicit ec: ExecutionContext): Future[Option[(NodeId, NodeId)]] = {
    isSlackChannelUpToDate(channelId, name).flatMap {
      case true => Future.successful(None)
      case false =>
        ctx.run(query[Channel_Mapping].filter(t => t.slack_channel_id.getOrElse("") == lift(channelId)).take(1).map(c => (c.wust_id, c.team_wust_id)))
          .map(_.headOption)
    }
  }

  def isMessageDeletedBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Message_Mapping].filter(m => m.slack_channel_id.getOrElse("") == lift(channelId) && m.slack_message_ts.getOrElse("") == lift(timestamp) && m.slack_deleted_flag).nonEmpty)
  }

  def isMessageUpToDateBySlackData(channelId: SlackChannelId, timestamp: SlackTimestamp, text: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Message_Mapping].filter(m => m.slack_channel_id.getOrElse("") == lift(channelId) && m.slack_message_ts.getOrElse("") == lift(timestamp) && m.slack_message_text == lift(text)).nonEmpty)
  }

  def isSlackChannelUpToDate(channelId: SlackChannelId, name: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Channel_Mapping].filter(t => t.slack_channel_id.getOrElse("") == lift(channelId) && t.slack_channel_name == lift(name)).nonEmpty)
  }

  // Boolean Guards / Filter by wust id
  def teamExistsByWustId(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Team_Mapping].filter(_.wust_id == lift(nodeId)).take(1).nonEmpty)
  }

  def channelExistsByWustId(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Channel_Mapping].filter(_.wust_id == lift(nodeId)).take(1).nonEmpty)
  }

  def threadExistsByWustId(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Message_Mapping].filter(m => m.wust_id == lift(nodeId) && m.slack_thread_ts.isDefined).take(1).nonEmpty)
  }

  def messageExistsByWustId(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    ctx
      .run(query[Message_Mapping].filter(m => m.wust_id == lift(nodeId) && m.slack_thread_ts.isEmpty).take(1).nonEmpty)
  }













  /**
    * Old interface
    */

//  def getChannelMapping(channelId: SlackChannelId)(implicit ec: ExecutionContext): Future[Option[Channel_Mapping]] = {
//    ctx
//      .run(query[Channel_Mapping].filter(_.slack_channel_id.getOrElse("") == lift(channelId)).take(1))
//      .map(_.headOption)
//  }
//
//
//  def getChannelNodeByName(channelName: String)(implicit ec: ExecutionContext): Future[Option[NodeId]] = {
//    ctx
//      .run(query[Channel_Mapping].filter(_.slack_channel_name == lift(channelName)).take(1).map(_.wust_id))
//      .map(_.headOption)
//  }
//
//  def getChannelMappingBySlackName(channelName: String)(implicit ec: ExecutionContext): Future[Option[Channel_Mapping]] = {
//    ctx
//      .run(query[Channel_Mapping].filter(_.slack_channel_name == lift(channelName)).take(1))
//      .map(_.headOption)
//  }
//
//  def getMessageNodeByContent(text: String)(implicit ec: ExecutionContext): Future[Option[NodeId]] = {
//    ctx
//      .run(query[Message_Mapping].filter(_.slack_message_text == lift(text)).take(1).map(_.wust_id))
//      .map(_.headOption)
//  }


}
