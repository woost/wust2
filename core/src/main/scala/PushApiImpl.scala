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
    Action.requireDbUser { (_, user) =>
      db.notifications.subscribeWebPush(forDb(user.id, subscription))
    }

  override def cancelSubscription(subscription: WebPushSubscription): ApiFunction[Boolean] = Action {
    db.notifications.cancelWebPush(endpointUrl = subscription.endpointUrl, p256dh = subscription.p256dh, auth = subscription.auth)
  }

  override def getPublicKey(): ApiFunction[Option[String]] = Action {
    Future.successful(pushConfig.map(_.keys.publicKey))
  }
}
