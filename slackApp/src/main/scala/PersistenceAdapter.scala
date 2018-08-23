package wust.slack

import akka.actor.ActorSystem
import cats.data.OptionT
import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import wust.api.Authentication
import wust.ids.{NodeAccess, NodeData, NodeId, UserId}
import com.typesafe.config.{Config => TConfig}
import slack.api.SlackApiClient
import wust.graph.{GraphChanges, Node, NodeMeta}
import wust.sdk.WustClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import wust.slack.Data._

trait PersistenceAdapter {
  // Store
  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean]
  def storeOrUpdateMessageMapping(messageMapping: Message_Mapping): Future[Boolean]
  def storeOrUpdateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean]
  def storeOrUpdateTeamMapping(teamMapping: Team_Mapping): Future[Boolean]


  // Update
  def updateMessageMapping(messageMapping: Message_Mapping): Future[Boolean]
  def updateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean]


  // Delete
  def deleteChannelBySlackId(channelId: SlackChannelId): Future[Boolean]
  def unDeleteChannelBySlackId(channelId: SlackChannelId): Future[Boolean]
  def deleteMessageBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean]


  // Queries
  // Query Wust NodeId by Slack Id
  def getTeamNodeBySlackId(teamId: SlackTeamId): Future[Option[NodeId]]
  def getChannelNodeBySlackId(channelId: SlackChannelId): Future[Option[NodeId]]
  def getMessageNodeBySlackIdData(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]]


  // Query Slack Id by Wust NodeId
  def getSlackChannelByWustId(nodeId: NodeId): Future[Option[SlackChannelId]]


  // Query Data by Slack Id


  // Query Data by Wust Id
  def getWustUserBySlackUserId(slackUser: SlackUserId): Future[Option[WustUserData]]
  def getSlackUserByWustId(userId: UserId): Future[Option[SlackUserData]]
  def getChannelMappingByWustId(nodeId: NodeId): Future[Option[Channel_Mapping]]
  def getSlackMessageByWustId(nodeId: NodeId): Future[Option[Message_Mapping]]


  // Guards
  def teamExistsByWustId(nodeId: NodeId): Future[Boolean]
  def isChannelCreatedByNameAndTeam(teamId: SlackTeamId, channelName: String): Future[Boolean]
  def isChannelDeletedBySlackId(channelId: SlackChannelId): Future[Boolean]
  def isChannelUpToDateBySlackDataElseGetNodes(channelId: SlackChannelId, name: String): Future[Option[(NodeId, NodeId)]]
  def isMessageDeletedBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean]
  def isMessageUpToDateBySlackData(channel: SlackChannelId, timestamp: SlackTimestamp, text: String): Future[Boolean]


  /**
    * Old Interface
    */
//  def getChannelMappingBySlackName(channelName: String): Future[Option[Channel_Mapping]]
//  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]]
//  def getChannelNodeByName(channelName: String): Future[Option[NodeId]]
//  def getOrCreateChannelNode(channel: SlackChannelId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]]
//  def getOrCreateKnowChannelNode(channel: SlackChannelId, slackWorkspaceNode: NodeId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]]
//  def getMessageNodeByContent(text: String): Future[Option[NodeId]]
//  def getSlackChannelOfMessageNodeId(nodeId: NodeId): Future[Option[NodeId]]
//  def isSlackChannelUpToDate(channelId: String, name: String): Future[Boolean]
}

object PostgresAdapter {
  def apply(config: TConfig)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem) extends PersistenceAdapter {

  // Store
  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean] = {
    db.storeOrUpdateUserMapping(userMapping)
  }

  def storeOrUpdateMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = {
    db.storeOrUpdateMessageMapping(messageMapping)
  }

  def storeOrUpdateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean] = {
    db.storeOrUpdateChannelMapping(channelMapping)
  }

  def storeOrUpdateTeamMapping(teamMapping: Team_Mapping): Future[Boolean] = {
    db.storeOrUpdateTeamMapping(teamMapping)
  }


  // Update
  def updateMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = {
    db.updateMessageMapping(messageMapping)
  }

  def updateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean] = {
    db.updateChannelMapping(channelMapping)
  }


  // Delete
  def deleteChannelBySlackId(channelId: SlackChannelId): Future[Boolean] = {
    db.deleteChannelBySlackId(channelId)
  }

  def unDeleteChannelBySlackId(channelId: SlackChannelId): Future[Boolean] = {
    db.unDeleteChannelBySlackId(channelId)
  }

  def deleteMessageBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = {
    db.deleteMessageBySlackIdData(channelId, timestamp)
  }


  // Queries
  // Query Wust NodeId by Slack Id
  def getTeamNodeBySlackId(teamId: SlackTeamId): Future[Option[NodeId]] = {
    db.getTeamNodeBySlackId(teamId)
  }

  def getChannelNodeBySlackId(channelId: SlackChannelId): Future[Option[NodeId]] = {
    db.getChannelNodeBySlackId(channelId)
  }

  def getMessageNodeBySlackIdData(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = {
    db.getMessageNodeBySlackIdData(channel, timestamp)
  }


  // Query Slack Id by Wust NodeId
  def getSlackChannelByWustId(nodeId: NodeId): Future[Option[SlackChannelId]] = {
    db.getSlackChannelByWustId(nodeId)
  }


  // Query Data by Wust Id
  def getWustUserBySlackUserId(slackUser: SlackUserId): Future[Option[WustUserData]] = {
    db.getWustUserBySlackUserId(slackUser)
  }

  def getSlackUserByWustId(userId: UserId): Future[Option[SlackUserData]] = {
    db.getSlackUserByWustId(userId)
  }

  def getChannelMappingByWustId(nodeId: NodeId): Future[Option[Channel_Mapping]] = {
    db.getChannelMappingByWustId(nodeId)
  }

  def getSlackMessageByWustId(nodeId: NodeId): Future[Option[Message_Mapping]] = {
    db.getSlackMessageByWustId(nodeId)
  }


  // Guards
  def teamExistsByWustId(nodeId: NodeId): Future[Boolean] = {
    db.teamExistsByWustId(nodeId)
  }

  def isChannelCreatedByNameAndTeam(teamId: SlackTeamId, channelName: String): Future[Boolean] = {
    db.isChannelCreatedByNameAndTeam(teamId, channelName)
  }

  def isChannelDeletedBySlackId(channelId: SlackChannelId): Future[Boolean] = {
    db.isChannelDeletedBySlackId(channelId)
  }

  def isChannelUpToDateBySlackDataElseGetNodes(channelId: SlackChannelId, name: String): Future[Option[(NodeId, NodeId)]] = {
    db.isChannelUpToDateBySlackDataElseGetNodes(channelId, name)
  }


  def isMessageDeletedBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = {
    db.isMessageDeletedBySlackIdData(channelId, timestamp)
  }

  def isMessageUpToDateBySlackData(channel: SlackChannelId, timestamp: SlackTimestamp, text: String): Future[Boolean] = {
    db.isMessageUpToDateBySlackData(channel, timestamp, text)
  }






  /**
    *
    * Old interface
    *
    */


//  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]] = ???
//
//
//
//
//  def getChannelNodeByName(channelName: String): Future[Option[NodeId]] = {
//    db.getChannelNodeByName(channelName)
//  }
//
//
//  def getChannelMappingBySlackName(channelName: String): Future[Option[Channel_Mapping]] = {
//    db.getChannelMappingBySlackName(channelName)
//  }
//
//  def getMessageNodeByContent(text: String): Future[Option[NodeId]] = {
//    db.getMessageNodeByContent(text)
//  }
//
//
//  def getSlackChannelOfMessageNodeId(nodeId: NodeId): Future[Option[NodeId]] = {
//    db.getSlackMessageByWustId(nodeId).flatMap {
//      case Some(m) => m.slack_channel_id match {
//        case Some(id) => db.getChannelNodeBySlackId(id)
//        case _ => Future.successful(None)
//      }
//      case _ => Future.successful(None)
//    }
//  }
//
//
//
//
//
//
//
//  def isSlackChannelUpToDate(channelId: String, name: String): Future[Boolean] = {
//    db.isSlackChannelUpToDate(channelId, name)
//  }






}
