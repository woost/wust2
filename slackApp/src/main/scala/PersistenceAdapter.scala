package wust.slack

import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import wust.api.Authentication
import wust.ids.{NodeId, UserId}
import com.typesafe.config.{Config => TConfig}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class WustUserData(wustUserId: UserId, wustUserToken: Authentication.Token)
case class SlackUserData(slackUserId: String, slackUserToken: AccessToken)

trait PersistenceAdapter {
  type SlackChannelId = String
  type SlackTimestamp = String
  type SlackUserId = String

  def storeWustUserToken(auth: Authentication.Token): Future[Boolean]
  def storeSlackUserToken(slackUserId: String, oAuthToken: AccessToken): Future[Boolean]

  def getOrCreateWustUser(slackUser: SlackUserId): Future[Option[WustUserData]]
  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]]

  def getChannelNode(channel: SlackChannelId): Future[Option[NodeId]]
  def getNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]]

}

object PostgresAdapter {
  def apply(config: TConfig) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db) extends PersistenceAdapter {

  def storeWustUserToken(auth: Authentication.Token): Future[Boolean] = ???
  def storeSlackUserToken(slackUserId: String, oAuthToken: AccessToken): Future[Boolean] = ???

  def getOrCreateWustUser(slackUser: SlackUserId): Future[Option[WustUserData]] = ???
  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]] = ???

  def getChannelNode(channel: SlackChannelId): Future[Option[NodeId]] = {
    ???
//      db.getChannelNode(channel)
  }

  def getNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = ???

//  def method(): = ???
}
