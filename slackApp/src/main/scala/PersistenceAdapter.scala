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
  type SlackTeamId = String
  type SlackChannelId = String
  type SlackTimestamp = String
  type SlackUserId = String

  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean]
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean]
  def storeChannelMapping(channelMapping: Channel_Mapping): Future[Boolean]
  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean]

  def updateMessageMapping(messageMapping: Message_Mapping): Future[Boolean]
  def updateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean]
//  def updateTeamMapping(teamMapping: Team_Mapping): Future[Boolean]

  def getTeamNodeBySlackId(teamId: SlackTeamId): Future[Option[NodeId]]

  def getChannelMappingBySlackName(channelName: String): Future[Option[Channel_Mapping]]
  def getChannelMappingByWustId(nodeId: NodeId): Future[Option[Channel_Mapping]]

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]]
  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]]

  def getChannelNodeById(channelId: SlackChannelId): Future[Option[NodeId]]
  def getChannelNodeByName(channelName: String): Future[Option[NodeId]]
  def getOrCreateChannelNode(channel: SlackChannelId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]]
  def getOrCreateKnowChannelNode(channel: SlackChannelId, slackWorkspaceNode: NodeId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]]

  def getSlackChannelId(nodeId: NodeId): Future[Option[SlackChannelId]]
  def getSlackMessage(nodeId: NodeId): Future[Option[Message_Mapping]]
  def getSlackUser(userId: UserId): Future[Option[SlackUserData]]

  def getMessageNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]]
  def getMessageNodeByContent(text: String): Future[Option[NodeId]]

  def getSlackChannelOfMessageNodeId(nodeId: NodeId): Future[Option[NodeId]]


  def deleteMessage(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean]
  def deleteChannel(channelId: SlackChannelId): Future[Boolean]
  def unDeleteChannel(channelId: SlackChannelId): Future[Boolean]



  // Guards
  def isSlackMessageDeleted(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean]
  def isSlackMessageUpToDate(channel: String, timestamp: String, text: String): Future[Boolean]
  def isSlackChannelDeleted(channelId: String): Future[Boolean]
  def isSlackChannelUpToDate(channelId: String, name: String): Future[Boolean]
  def isSlackChannelUpToDateElseGetNode(channelId: String, name: String): Future[Option[NodeId]]
}

object PostgresAdapter {
  def apply(config: TConfig)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem) extends PersistenceAdapter {

  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean] = {
    db.storeOrUpdateUserMapping(userMapping)
  }
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = {
    db.storeMessageMapping(messageMapping)
  }
  def storeChannelMapping(channelMapping: Channel_Mapping): Future[Boolean] = {
    db.storeChannelMapping(channelMapping)
  }

  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean] = {
    db.storeTeamMapping(teamMapping)
  }

  def updateMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = {
    db.updateMessageMapping(messageMapping)
  }

  def updateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean] = {
    db.updateChannelMapping(channelMapping)
  }

  // TODO: Move API calls and composition to separate event composer
  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]] = {
    val existingUser = db.getWustUser(slackUser)
    val user: Future[Option[WustUserData]] = existingUser.flatMap {
      case Some(u) => Future.successful(Some(u))
      case None =>
        // 1. Create new implicit user
        // 2. Store user mapping

        import cats.implicits._
        val wustUser = OptionT[Future, Authentication.Verified](wustClient.auth.createImplicitUserForApp()).map { implicitUser =>
          WustUserData(implicitUser.user.id, implicitUser.token)
        }

        val userMap = wustUser.map { newUser =>
          storeOrUpdateUserAuthData(User_Mapping(slackUser, newUser.wustUserId, None, newUser.wustUserToken))
        }

        wustUser.value.onComplete {
          case Failure(ex) =>
            scribe.error("Error creating user mapping during creation of a wust user: ", ex)
          case _ =>
        }

        userMap.value.onComplete {
          case Failure(ex) =>
            scribe.error("DB error when creating user: ", ex)
          case _ =>
        }

        wustUser.value
    }

    user.onComplete {
      case Success(userOpt) => if(userOpt.isEmpty) scribe.error("Could not get or create user")
      case Failure(userOpt) => scribe.error(s"Could not communicate with backend to get or create user $userOpt")
    }

    user
  }

  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]] = ???

  def getOrCreateKnowChannelNode(channel: SlackChannelId, slackWorkspaceNode: NodeId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]] = {
    val existingChannelNode = db.getChannelNodeById(channel)
    existingChannelNode.flatMap {
      case Some(c) => Future.successful(Some(c))
      case None =>
        // TODO:
        // 1. Get channel name by id (Slack API call)
        // 2. Create with NodeData = name (Wust API call)
        // 3. Create channel mapping (DB)

        val channelName = slackClient.getChannelInfo(channel).map(ci => ci.name)
        val newChannelNode = channelName.map(name =>
          Node.Content(NodeId.fresh, NodeData.Markdown(name), NodeMeta(NodeAccess.ReadWrite))
        )

        newChannelNode.onComplete {
          case Success(node) =>
            wustReceiver.push(List(GraphChanges.addNodeWithParent(node, slackWorkspaceNode)), None)
//            wustClient.api.changeGraph(GraphChanges.addNode(node)).onComplete {
//              case Failure(ex) => scribe.error("Could not create channel node in wust: ", ex)
//              case _ =>
//            }
            db.storeChannelMapping(Channel_Mapping(Some(channel), node.str, slack_deleted_flag = false, node.id)).onComplete {
              case Failure(ex) => scribe.error("Could not create channel mapping in slack app: ", ex)
              case _ =>
            }
          case Failure(ex) =>
            scribe.error("Could not request channel info: ", ex)
        }

        newChannelNode.map(n => Some(n.id))
    }
  }

  def getOrCreateChannelNode(channel: SlackChannelId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]] = {
    for {
      t <- slackClient.getTeamInfo()
      workspaceNodeId <- db.getTeamNodeById(t.id).flatMap {
        case Some(wustId) => getOrCreateKnowChannelNode(channel, wustId, wustReceiver, slackClient)
        case _            => Future.successful(None)
      }
    } yield {
      workspaceNodeId
    }
  }


  def getChannelNodeById(channelId: SlackChannelId): Future[Option[NodeId]] = {
      db.getChannelNodeById(channelId)
  }


  def getTeamNodeBySlackId(teamId: SlackTeamId): Future[Option[NodeId]] = {
    db.getTeamNodeById(teamId)
  }


  def getChannelNodeByName(channelName: String): Future[Option[NodeId]] = {
    db.getChannelNodeByName(channelName)
  }


  def getSlackChannelId(nodeId: NodeId): Future[Option[SlackChannelId]] = {
    db.getSlackChannelId(nodeId)
  }

  def getChannelMappingBySlackName(channelName: String): Future[Option[Channel_Mapping]] = {
    db.getChannelMappingBySlackName(channelName)
  }

  def getChannelMappingByWustId(nodeId: NodeId): Future[Option[Channel_Mapping]] = {
    db.getChannelMappingByWustId(nodeId)
  }

  def getSlackMessage(nodeId: NodeId): Future[Option[Message_Mapping]] = {
    db.getSlackMessage(nodeId)
  }

  def getSlackUser(userId: UserId): Future[Option[SlackUserData]] = {
    db.getSlackUser(userId)
  }

  def getMessageNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = {
    db.getMessageFromSlackData(channel, timestamp)
  }

  def getMessageNodeByContent(text: String): Future[Option[NodeId]] = {
    db.getMessageNodeByContent(text)
  }


  def getSlackChannelOfMessageNodeId(nodeId: NodeId): Future[Option[NodeId]] = {
    db.getSlackMessage(nodeId).flatMap {
      case Some(m) => m.slack_channel_id match {
        case Some(id) => db.getChannelNodeById(id)
        case _ => Future.successful(None)
      }
      case _ => Future.successful(None)
    }
  }




  def deleteMessage(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = {
    scribe.info(s"!!!!!!!!!!!!!! channelId = $channelId, ts = $timestamp")
    db.deleteMessage(channelId, timestamp)
  }

  def deleteChannel(channelId: SlackChannelId): Future[Boolean] = {
    db.deleteChannel(channelId)
  }

  def unDeleteChannel(channelId: SlackChannelId): Future[Boolean] = {
    db.unDeleteChannel(channelId)
  }








  def isSlackMessageDeleted(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = {
    db.isSlackMessageDeleted(channelId, timestamp)
  }

  def isSlackMessageUpToDate(channel: String, timestamp: String, text: String): Future[Boolean] = {
    db.isSlackMessageUpToDate(channel, timestamp, text)
  }

  def isSlackChannelDeleted(channelId: String): Future[Boolean] = {
    db.isSlackChannelDeleted(channelId)
  }

  def isSlackChannelUpToDate(channelId: String, name: String): Future[Boolean] = {
    db.isSlackChannelUpToDate(channelId, name)
  }

  def isSlackChannelUpToDateElseGetNode(channelId: String, name: String): Future[Option[NodeId]] = {
    db.isSlackChannelUpToDateElseGetNode(channelId, name)
  }

//  def method(): = ???
}
