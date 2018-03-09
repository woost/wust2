package wust.backend

import wust.api._
import wust.db.Db
import wust.ids._
import covenant.ws.api.EventDistributor
import mycelium.server.NotifiableClient
import nl.martijndwars.webpush._

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Failure
import scala.collection.breakOut
import scala.concurrent.duration._
import java.security.{Security, PublicKey, PrivateKey}
import scala.util.{Try, Success, Failure}

class HashSetEventDistributorWithPush(db: Db)(implicit ec: ExecutionContext) extends EventDistributor[ApiEvent, State] {
  //TODO: somewhere else?
  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

  private val subscribers = mutable.HashSet.empty[NotifiableClient[ApiEvent, State]]

  private val vapidPublicKey: String = "BDP21xA-AA6MyDK30zySyHYf78CimGpsv6svUm0dJaRgAjonSDeTlmE111Vj84jRdTKcLojrr5NtMlthXkpY-q0"
  private val vapidPrivateKey: String = "or76yI5iDE-S9gWkVU2g0JuHyq4OD_AtwHTHefkoo3k"
  private val subject: String = "cornerman@unseen.is"

  private val pushService = new PushService(vapidPublicKey, vapidPrivateKey, subject) //TODO: expiry?

  def subscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers -= client
  }

  //TODO is this async? it should be. we do not want to block a running request for sending out events.
  override def publish(events: List[ApiEvent], origin: Option[NotifiableClient[ApiEvent, State]]): Unit = if (events.nonEmpty) {
    scribe.info(s"Event distributor (${subscribers.size} clients): $events from $origin")

    subscribers.foreach { client =>
      if (origin.fold(true)(_ != client)) client.notify(_ => Future.successful(events))
    }

    distributeNotifications(events)
  }

  //TODO
  private def urlsafebase64(s:String) = s.replace("/", "_").replace("+", "-")
  private def distributeNotifications(events: List[ApiEvent]): Unit = {
    // see https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
    val expiryStatusCodes = Set(404, 410)
    val successStatusCode = 201

    val involvedPostIds: Set[PostId] = events.flatMap {
      case ApiEvent.NewGraphChanges(changes) => changes.involvedPostIds
      case _ => Set.empty[PostId]
    }(breakOut)

    scribe.info("Sending subs for " + involvedPostIds)
    db.notifications.getSubscriptions(involvedPostIds).foreach { subscriptions =>
      val expiredSubscriptions = subscriptions.par.filter { s =>
        val notification = new Notification(s.endpointUrl, urlsafebase64(s.p256dh), urlsafebase64(s.auth), "content")
        Try(pushService.send(notification)) match {
          case Success(response) =>
            response.getStatusLine.getStatusCode match {
              case `successStatusCode` =>
                scribe.info(s"Send push notification: $response")
                false
              case statusCode if expiryStatusCodes.contains(statusCode) =>
                scribe.info(s"Cannot send push notification, is expired: $response")
                true
              case _ =>
                scribe.info(s"Cannot send push notification: $response")
                false
            }
          case Failure(t) =>
            scribe.error(s"Cannot send push notification, due to unexpected exception: $t")
            false
        }
      }

      if (expiredSubscriptions.nonEmpty) {
        db.notifications.delete(expiredSubscriptions.seq.toSet).onComplete { res =>
          scribe.info(s"Deleted expired subscriptions ($expiredSubscriptions): $res")
        }
      }
    }
  }
}
