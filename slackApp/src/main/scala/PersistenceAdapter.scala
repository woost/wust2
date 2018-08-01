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

  def storeUserAuthData(userMapping: User_Mapping): Future[Boolean]
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

  def storeUserAuthData(userMapping: User_Mapping): Future[Boolean] = {
    db.storeUserMapping(userMapping)
  }
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = {
    db.storeMessageMapping(messageMapping)
  }
  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean] = ???

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]] = {
    val existingUser = db.getWustUser(slackUser)
    existingUser.flatMap {
      case Some(u) => Future.successful(Some(u))
      case None => wustClient.auth.createImplicitUserForApp().map(u => Some(WustUserData(u.user.id, u.token)))
    }
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
