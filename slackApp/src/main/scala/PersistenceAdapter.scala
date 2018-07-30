package wust.slack

import wust.api.Authentication
import wust.ids.{NodeId, UserId}
import com.typesafe.config.{Config => TConfig}


trait PersistenceAdapter {
  type SlackChannelId = String
  type SlackTimestamp = String
  type SlackUserId = String

  def storeUserToken(auth: Authentication.Token): Boolean

  def getOrCreateWustUser(slackUser: SlackUserId): (UserId, Authentication.Token)
  def getChannelNode(channel: SlackChannelId): NodeId
  def getNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): NodeId

}

object PostgresAdapter {
  def apply(config: TConfig) = new PostgresAdapter( Db(config) )
}

case class PostgresAdapter(db: Db) extends PersistenceAdapter {

  def storeUserToken(auth: Authentication.Token): Boolean = {
    ???
  }

  def getOrCreateWustUser(slackUser: SlackUserId): (UserId, Authentication.Token) = {
   ???
  }

  def getChannelNode(channel: SlackChannelId): NodeId = {
    ???
  }

  def getNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): NodeId = {
    ???
  }

//  def method(): = ???
}
