package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.backend.Dsl._
import wust.backend.config.PushNotificationConfig
import wust.db.Db

import scala.concurrent.{ExecutionContext, Future}

class PushApiImpl(dsl: GuardDsl, db: Db, pushConfig: Option[PushNotificationConfig])(
    implicit ec: ExecutionContext
) extends PushApi[ApiFunction] {
  import dsl._

  override def subscribeWebPush(subscription: WebPushSubscription): ApiFunction[Boolean] =
    Action.assureDbUser { (_, user) =>
      db.notifications.subscribeWebPush(forDb(user.id, subscription))
    }

  override def getPublicKey(): ApiFunction[Option[String]] = Action {
    Future.successful(pushConfig.map(_.keys.publicKey))
  }
}
