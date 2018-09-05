package wust.slack

import akka.actor.ActorSystem
import cats.data.OptionT
import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import wust.api.Authentication
import wust.ids.{NodeAccess, NodeData, NodeId, UserId}
import com.typesafe.config.{Config => TConfig}
import monix.eval.Task
import monix.execution.Scheduler
import slack.api.SlackApiClient
import wust.graph.{GraphChanges, Node, NodeMeta}
import wust.sdk.WustClient

import scala.util.{Failure, Success}
import wust.slack.Data._

trait PersistenceAdapter {
  // Store
  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Task[Boolean]
  def storeOrUpdateMessageMapping(messageMapping: Message_Mapping): Task[Boolean]
  def storeOrUpdateChannelMapping(channelMapping: Channel_Mapping): Task[Boolean]
  def storeOrUpdateTeamMapping(teamMapping: Team_Mapping): Task[Boolean]


  // Update
  def updateMessageMapping(messageMapping: Message_Mapping): Task[Boolean]
  def updateChannelMapping(channelMapping: Channel_Mapping): Task[Boolean]


  // Delete
  def deleteChannelBySlackId(channelId: SlackChannelId): Task[Boolean]
  def unDeleteChannelBySlackId(channelId: SlackChannelId): Task[Boolean]
  def deleteMessageBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Task[Boolean]


  // Queries
  // Query Wust NodeId by Slack Id
  def getWustUserBySlackUserId(slackUser: SlackUserId): Task[Option[WustUserData]]
  def getTeamNodeBySlackId(teamId: SlackTeamId): Task[Option[NodeId]]
  def getChannelNodeBySlackId(channelId: SlackChannelId): Task[Option[NodeId]]
  def getMessageNodeBySlackIdData(channel: SlackChannelId, timestamp: SlackTimestamp): Task[Option[NodeId]]


  // Query Slack Id by Wust NodeId
  def getSlackChannelByWustId(nodeId: NodeId): Task[Option[SlackChannelId]]


  // Query Data by Slack Id


  // Query Data by Wust Id
  def getSlackUserDataByWustId(userId: UserId): Task[Option[SlackUserData]]
  def getTeamMappingByWustId(nodeId: NodeId): Task[Option[Team_Mapping]]
  def getChannelMappingByWustId(nodeId: NodeId): Task[Option[Channel_Mapping]]
  def getMessageMappingByWustId(nodeId: NodeId): Task[Option[Message_Mapping]]


  // Guards
  def channelExistsByNameAndTeam(teamId: SlackTeamId, channelName: String): Task[Boolean]
  def isChannelDeletedBySlackId(channelId: SlackChannelId): Task[Boolean]
  def isChannelUpToDateBySlackDataElseGetNodes(channelId: SlackChannelId, name: String): Task[Option[(NodeId, NodeId)]]
  def isMessageDeletedBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Task[Boolean]
  def isMessageUpToDateBySlackData(channel: SlackChannelId, timestamp: SlackTimestamp, text: String): Task[Boolean]

  // Boolean Guards / Filter by wust id
  def teamExistsByWustId(nodeId: NodeId): Task[Boolean]
  def channelExistsByWustId(nodeId: NodeId): Task[Boolean]
  def threadExistsByWustId(nodeId: NodeId): Task[Boolean]
  def messageExistsByWustId(nodeId: NodeId): Task[Boolean]


  /**
    * Old Interface
    */
//  def getChannelMappingBySlackName(channelName: String): Task[Option[Channel_Mapping]]
//  def getOrCreateSlackUser(wustUser: SlackUserId): Task[Option[SlackUserData]]
//  def getChannelNodeByName(channelName: String): Task[Option[NodeId]]
//  def getOrCreateChannelNode(channel: SlackChannelId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Task[Option[NodeId]]
//  def getOrCreateKnowChannelNode(channel: SlackChannelId, slackWorkspaceNode: NodeId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Task[Option[NodeId]]
//  def getMessageNodeByContent(text: String): Task[Option[NodeId]]
//  def getSlackChannelOfMessageNodeId(nodeId: NodeId): Task[Option[NodeId]]
//  def isSlackChannelUpToDate(channelId: String, name: String): Task[Boolean]
}

object PostgresAdapter {
  def apply(config: TConfig)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem) extends PersistenceAdapter {

  // Store
  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.storeOrUpdateUserMapping(userMapping))
  }

  def storeOrUpdateMessageMapping(messageMapping: Message_Mapping): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.storeOrUpdateMessageMapping(messageMapping))
  }

  def storeOrUpdateChannelMapping(channelMapping: Channel_Mapping): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.storeOrUpdateChannelMapping(channelMapping))
  }

  def storeOrUpdateTeamMapping(teamMapping: Team_Mapping): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.storeOrUpdateTeamMapping(teamMapping))
  }


  // Update
  def updateMessageMapping(messageMapping: Message_Mapping): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.updateMessageMapping(messageMapping))
  }

  def updateChannelMapping(channelMapping: Channel_Mapping): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.updateChannelMapping(channelMapping))
  }


  // Delete
  def deleteChannelBySlackId(channelId: SlackChannelId): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.deleteChannelBySlackId(channelId))
  }

  def unDeleteChannelBySlackId(channelId: SlackChannelId): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.unDeleteChannelBySlackId(channelId))
  }

  def deleteMessageBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.deleteMessageBySlackIdData(channelId, timestamp))
  }


  // Queries
  // Query Wust NodeId by Slack Id
  def getWustUserBySlackUserId(slackUser: SlackUserId): Task[Option[WustUserData]] = {
    Task.deferFutureAction(implicit Scheduler => db.getWustUserDataBySlackUserId(slackUser))
  }

  def getTeamNodeBySlackId(teamId: SlackTeamId): Task[Option[NodeId]] = {
    Task.deferFutureAction(implicit Scheduler => db.getTeamNodeBySlackId(teamId))
  }

  def getChannelNodeBySlackId(channelId: SlackChannelId): Task[Option[NodeId]] = {
    Task.deferFutureAction(implicit Scheduler => db.getChannelNodeBySlackId(channelId))
  }

  def getMessageNodeBySlackIdData(channel: SlackChannelId, timestamp: SlackTimestamp): Task[Option[NodeId]] = {
    Task.deferFutureAction(implicit Scheduler => db.getMessageNodeBySlackIdData(channel, timestamp))
  }


  // Query Slack Id by Wust NodeId
  def getSlackChannelByWustId(nodeId: NodeId): Task[Option[SlackChannelId]] = {
    Task.deferFutureAction(implicit Scheduler => db.getSlackChannelByWustId(nodeId))
  }


  // Query Data by Wust Id
  def getSlackUserDataByWustId(userId: UserId): Task[Option[SlackUserData]] = {
    Task.deferFutureAction(implicit Scheduler => db.getSlackUserDataByWustId(userId))
  }
  
  def getTeamMappingByWustId(nodeId: NodeId): Task[Option[Team_Mapping]] = {
    Task.deferFutureAction(implicit Scheduler => db.getTeamMappingByWustId(nodeId))
  }

  def getChannelMappingByWustId(nodeId: NodeId): Task[Option[Channel_Mapping]] = {
    Task.deferFutureAction(implicit Scheduler => db.getChannelMappingByWustId(nodeId))
  }

  def getMessageMappingByWustId(nodeId: NodeId): Task[Option[Message_Mapping]] = {
    Task.deferFutureAction(implicit Scheduler => db.getMessageMappingByWustId(nodeId))
  }


  // Guards
  def channelExistsByNameAndTeam(teamId: SlackTeamId, channelName: String): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.channelExistsByNameAndTeam(teamId, channelName))
  }

  def isChannelDeletedBySlackId(channelId: SlackChannelId): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.isChannelDeletedBySlackId(channelId))
  }

  def isChannelUpToDateBySlackDataElseGetNodes(channelId: SlackChannelId, name: String): Task[Option[(NodeId, NodeId)]] = {
    Task.deferFutureAction(implicit Scheduler => db.isChannelUpToDateBySlackDataElseGetNodes(channelId, name))
  }


  def isMessageDeletedBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.isMessageDeletedBySlackIdData(channelId, timestamp))
  }

  def isMessageUpToDateBySlackData(channel: SlackChannelId, timestamp: SlackTimestamp, text: String): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.isMessageUpToDateBySlackData(channel, timestamp, text))
  }



  // Boolean Guards / Filter by wust id
  def teamExistsByWustId(nodeId: NodeId): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.teamExistsByWustId(nodeId))
  }

  def channelExistsByWustId(nodeId: NodeId): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.channelExistsByWustId(nodeId))
  }

  def threadExistsByWustId(nodeId: NodeId): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.threadExistsByWustId(nodeId))
  }

  def messageExistsByWustId(nodeId: NodeId): Task[Boolean] = {
    Task.deferFutureAction(implicit Scheduler => db.messageExistsByWustId(nodeId))
  }



  /**
    *
    * Old interface
    *
    */


//  def getOrCreateSlackUser(wustUser: SlackUserId): Task[Option[SlackUserData]] = ???
//
//
//
//
//  def getChannelNodeByName(channelName: String): Task[Option[NodeId]] = {
//    db.getChannelNodeByName(channelName)
//  }
//
//
//  def getChannelMappingBySlackName(channelName: String): Task[Option[Channel_Mapping]] = {
//    db.getChannelMappingBySlackName(channelName)
//  }
//
//  def getMessageNodeByContent(text: String): Task[Option[NodeId]] = {
//    db.getMessageNodeByContent(text)
//  }
//
//
//  def getSlackChannelOfMessageNodeId(nodeId: NodeId): Task[Option[NodeId]] = {
//    db.getSlackMessageByWustId(nodeId).flatMap {
//      case Some(m) => m.slack_channel_id match {
//        case Some(id) => db.getChannelNodeBySlackId(id)
//        case _ => Task.successful(None)
//      }
//      case _ => Task.successful(None)
//    }
//  }
//
//
//
//
//
//
//
//  def isSlackChannelUpToDate(channelId: String, name: String): Task[Boolean] = {
//    db.isSlackChannelUpToDate(channelId, name)
//  }






}
