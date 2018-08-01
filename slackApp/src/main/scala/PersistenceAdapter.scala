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

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]]
  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]]

  def getChannelNode(channel: SlackChannelId): Future[Option[NodeId]]
  def getNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]]

}

object PostgresAdapter {
  def apply(config: TConfig)(implicit ec: scala.concurrent.ExecutionContext) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db)(implicit ec: scala.concurrent.ExecutionContext) extends PersistenceAdapter {

  def storeUserAuthData(userMapping: User_Mapping): Future[Boolean] = {
    db.storeUserMapping(userMapping)
  }

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]] = {
//    db.getWustUser(slackUser).map(_.orElse {
//      wustClient.auth.
//
//    })
    ???
  }

  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]] = ???

  def getChannelNode(channel: SlackChannelId): Future[Option[NodeId]] = {
      db.getChannelNode(channel)
  }

  def getNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = ???

//  def method(): = ???
}
