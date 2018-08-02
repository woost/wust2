package wust.slack

import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import wust.api.Authentication
import wust.ids.{NodeId, UserId}
import com.typesafe.config.{Config => TConfig}
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
  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean] = ???

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]] = {
    val existingUser = db.getWustUser(slackUser)
    val user = existingUser.flatMap {
      case Some(u) => Future.successful(Some(u))
      case None => wustClient.auth.createImplicitUserForApp().map{
        case Some(implicitUser) => Some(WustUserData(implicitUser.user.id, implicitUser.token))
        case _ => None
      }
    }
    user.onComplete {
      case Success(userOpt) => userOpt match {
        case Some(u) =>
          storeOrUpdateUserAuthData(User_Mapping(slackUser, u.wustUserId, None, u.wustUserToken)).onComplete{
            case Success(userMap) =>
              if(userMap) scribe.info("Created new user mapping")
              else scribe.error(s"DB error when creating user: $u")
            case Failure(_) => scribe.error("Error creating user mapping during creation of a wust user")
          }
        case _ => scribe.error("Could not get or create user")
      }
      case Failure(userOpt) => scribe.error(s"Could not communicate with backend to get or create user $userOpt")
    }
    user
  }

  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]] = ???

  def getChannelNode(channel: SlackChannelId): Future[Option[NodeId]] = {
      db.getChannelNode(channel)
  }

  def getMessageNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = {
    db.getMessageFromSlackData(channel, timestamp)
  }

//  def method(): = ???
}
