package wust.api

import wust.ids.UserId

import scala.concurrent.Future

case class PluginUserAuthentication(userId: UserId, platformId: String, auth: Option[String])

trait PluginApi {
  def connectUser(auth: Authentication.Token): Future[Option[String]]
  def isAuthenticated(userId: UserId): Future[Boolean]
  def getAuthentication(userId: UserId, auth: Authentication.Token): Future[Option[PluginUserAuthentication]]
  def importContent(identifier: String): Future[Boolean]
}
