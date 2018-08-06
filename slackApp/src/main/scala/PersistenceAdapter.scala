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
  type SlackChannelId = String
  type SlackTimestamp = String
  type SlackUserId = String

  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean]
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean]
  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean]

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]]
  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]]

  def getChannelNode(channel: SlackChannelId): Future[Option[NodeId]]
//  def getOrCreateChannelNode(channel: SlackChannelId, wustClient: WustClient, slackClient: SlackApiClient)(implicit system: ActorSystem): Future[Option[NodeId]]
  def getOrCreateChannelNode(channel: SlackChannelId, slackNode: NodeId, wustReceiver: WustReceiver, slackClient: SlackApiClient)(implicit system: ActorSystem): Future[Option[NodeId]]

  def getMessageNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]]

}

object PostgresAdapter {
  def apply(config: TConfig)(implicit ec: scala.concurrent.ExecutionContext) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db)(implicit ec: scala.concurrent.ExecutionContext) extends PersistenceAdapter {

  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean] = {
    db.storeOrUpdateUserMapping(userMapping)
  }
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = {
    db.storeMessageMapping(messageMapping)
  }
  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean] = {
    db.storeTeamMapping(teamMapping)
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

  def getOrCreateChannelNode(channel: SlackChannelId, slackNode: NodeId, wustReceiver: WustReceiver, slackClient: SlackApiClient)(implicit system: ActorSystem): Future[Option[NodeId]] = {
    val existingChannelNode = db.getChannelNode(channel)
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
            wustReceiver.push(List(GraphChanges.addNodeWithParent(node, slackNode)), None)
//            wustClient.api.changeGraph(GraphChanges.addNode(node)).onComplete {
//              case Failure(ex) => scribe.error("Could not create channel node in wust: ", ex)
//              case _ =>
//            }
            db.storeTeamMapping(Team_Mapping(channel, node.id)).onComplete {
              case Failure(ex) => scribe.error("Could not create team mapping in slack app: ", ex)
              case _ =>
            }
          case Failure(ex) =>
            scribe.error("Could not request channel info: ", ex)
        }

        newChannelNode.map(n => Some(n.id))
    }
  }

  def getChannelNode(channel: SlackChannelId): Future[Option[NodeId]] = {
      db.getChannelNode(channel)
  }

  def getMessageNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = {
    db.getMessageFromSlackData(channel, timestamp)
  }

//  def method(): = ???
}
