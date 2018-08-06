package wust.sdk

import wust.api.Authentication
import wust.ids.{NodeData, NodeId, UserId}
import wust.graph.{GraphChanges, Node}

import scala.concurrent.{Future}

object Data {
  //  case class WustUserData(wustUserId: UserId, wustUserToken: Authentication.Token)
  //  case class PlatformUserData(platformUserId: String, platformUserToken: AccessToken)
  case class WustUserData(wustUserId: UserId, wustUserToken: String)
  case class PlatformUserData(platformUserId: String, platformUserToken: Option[String])

  case class Team_Mapping(platform_team_id: String, wust_id: NodeId)
  //  case class User_Mapping(platform_user_id: String, wust_id: NodeId, platform_token: Option[AccessToken], wust_token: Authentication.Verified)
  case class User_Mapping(platform_user_id: String, wust_id: UserId, platform_token: Option[String], wust_token: String)
  case class Conversation_Mapping(platform_conversation_id: String, wust_id: NodeId)
  case class Message_Mapping(platform_channel_id: String, platform_message_ts: String, wust_id: NodeId)
}

trait PersistenceAdapter {

  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean]
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean]
  def storeTeamMapping(teamMapping: Team_Mapping): Future[Boolean]

  def getOrCreateWustUser(platformUser: PlatformUserId, wustClient: WustClient): Future[Option[WustUserData]]
  def getOrCreatePlatformUser(wustUser: PlatformUserId): Future[Option[PlatformUserData]]

  def getChannelNode(channel: PlatformChannelId): Future[Option[NodeId]]
  def getOrCreateChannelNode(channel: PlatformChannelId, wustClient: WustClient): Future[Option[NodeId]]

  def getMessageNodeByChannelAndTimestamp(channel: PlatformChannelId, timestamp: PlatformTimestamp): Future[Option[NodeId]]

}
