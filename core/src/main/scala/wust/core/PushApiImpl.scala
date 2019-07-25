package wust.core

import wust.api._
import wust.core.DbConversions._
import wust.core.Dsl._
import wust.core.auth.JWT
import wust.core.config.PushNotificationConfig
import wust.core.pushnotifications.PushClients
import wust.db.Db

import scala.concurrent.{ExecutionContext, Future}

class PushApiImpl(dsl: GuardDsl, db: Db, pushConfig: Option[PushNotificationConfig])(implicit ec: ExecutionContext) extends PushApi[ApiFunction] {
  import dsl._

  override def subscribeWebPush(subscription: WebPushSubscription): ApiFunction[Boolean] =
    Action.requireDbUser { (_, user) =>
      db.notifications.subscribeWebPush(forDb(user.id, subscription))
        .map(_ => true)
    }

  override def cancelSubscription(subscription: WebPushSubscription): ApiFunction[Boolean] = Action {
    db.ctx.transaction { implicit ec =>
      db.notifications.cancelWebPush(endpointUrl = subscription.endpointUrl, p256dh = subscription.p256dh, auth = subscription.auth)
    }.map(_ => true)
  }

  override def getPublicKey(): ApiFunction[Option[String]] = Action {
    Future.successful(pushConfig.flatMap(_.webPush.map(_.keys.publicKey)))
  }
}
