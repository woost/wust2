package wust.api

import wust.ids.UserId

import scala.concurrent.Future

case class PluginUserAuthentication(userId: UserId, platformId: String, auth: Option[String])

trait PluginApi[F[_]] {
  def connectUser(auth: Authentication.Token): F[Option[String]]
  def isAuthenticated(userId: UserId): F[Boolean]
  def getAuthentication(userId: UserId, auth: Authentication.Token): F[Option[PluginUserAuthentication]]
  def importContent(identifier: String): F[Boolean]
}
