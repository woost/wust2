package wust.api

import wust.ids.UserId

import scala.concurrent.Future

trait PluginApi {
  def connectUser(auth: Authentication.Token): Future[Option[String]]
  def isAuthenticated(userId: UserId): Future[Boolean]
  def importContent(identifier: String): Future[Boolean]
}
