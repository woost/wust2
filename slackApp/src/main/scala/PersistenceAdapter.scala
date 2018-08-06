package wust.slack

import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import wust.api.Authentication
import wust.ids.{NodeData, NodeId, UserId}
import com.typesafe.config.{Config => TConfig}
import wust.graph.{GraphChanges, Node}
import wust.sdk.WustClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import wust.slack.Data._

object PostgresAdapter {
  def apply(config: TConfig)(implicit ec: scala.concurrent.ExecutionContext) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db)(implicit ec: scala.concurrent.ExecutionContext) extends PersistenceAdapter {
  type SlackChannelId = String
  type SlackTimestamp = String
  type SlackUserId = String

  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean] = {
    db.storeOrUpdateUserMapping(userMapping)
  }
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = {
    db.storeMessageMapping(messageMapping)
  }
  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean] = ???

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]] = {
    val existingUser = db.getWustUser(slackUser)
    val user = existingUser.flatMap {
      case Some(u) => Future.successful(Some(u))
      case None =>
        wustClient.auth.createImplicitUserForApp().map {
          case Some(implicitUser) =>
            val newUser = WustUserData(implicitUser.user.id, implicitUser.token)
            storeOrUpdateUserAuthData(User_Mapping(slackUser, newUser.wustUserId, None, newUser.wustUserToken)).onComplete{
              case Success(userMap) =>
                if(userMap) scribe.info("Created new user mapping")
                else scribe.error(s"DB error when creating user: $newUser")
              case Failure(_) => scribe.error("Error creating user mapping during creation of a wust user")
            }
            Some(newUser)
          case _ => None
        }
    }

    user.onComplete {
      case Success(userOpt) => if(userOpt.isEmpty) scribe.error("Could not get or create user")
      case Failure(userOpt) => scribe.error(s"Could not communicate with backend to get or create user $userOpt")
    }

    user
  }

  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]] = ???

  // TODO: Move API calls to EventComposer?
  // 1. Get channel name by id (Slack API call)
  // 2. Create with NodeData = name (Wust API call)
  // 3. Create channel mapping (DB)
  def getOrCreateChannelNode(channel: SlackChannelId, wustClient: WustClient): Future[Option[NodeId]] = {
    val existingChannelNode = db.getChannelNode(channel)
    existingChannelNode.flatMap{
      case Some(c) => Future.successful(Some(c))
      case None =>

        val newChannelNode = Node.Content(NodeData.Markdown())
        ,
        GraphChanges.addNode()
        wustClient.api.changeGraph()
        db.storeTeamMapping(Team_Mapping())
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
