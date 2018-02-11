package wust.api

import scala.concurrent.Future

trait PluginApi {
  def connectUser(auth: Authentication.Token): Future[Boolean]
}
