package wust.backend

import wust.api._
import wust.db.Db
import wust.ids._
import mycelium.server.NotifiableClient
import nl.martijndwars.webpush._

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Failure
import scala.collection.breakOut
import scala.concurrent.duration._
import java.security.{Security, PublicKey, PrivateKey}
import scala.util.{Try, Success, Failure}

class EventDistributor(db: Db) {
  val subscribers = mutable.HashSet.empty[NotifiableClient[ApiEvent]]

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

  private val vapidPublicKey: String = "BDP21xA-AA6MyDK30zySyHYf78CimGpsv6svUm0dJaRgAjonSDeTlmE111Vj84jRdTKcLojrr5NtMlthXkpY-q0"
  private val vapidPrivateKey: String = "or76yI5iDE-S9gWkVU2g0JuHyq4OD_AtwHTHefkoo3k"
  private val subject: String = "cornerman@unseen.is"

  private val pushService = new PushService(vapidPublicKey, vapidPrivateKey, subject) //TODO: expiry?

  def subscribe(client: NotifiableClient[ApiEvent]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[ApiEvent]): Unit = {
    subscribers -= client
  }

  //TODO is this async? it should be. we do not want to block a running request for sending out events.
  def publish(origin: NotifiableClient[ApiEvent], events: List[ApiEvent.Public])(implicit ec: ExecutionContext): Unit = if (events.nonEmpty) {
    scribe.info(s"Backend events (${subscribers.size - 1} clients): $events")

    subscribers.foreach { client =>
      if (client != origin) client.notify(events)
    }

    distributeNotifications(events)
  }

  //TODO
  private def urlsafebase64(s:String) = s.replace("/", "_").replace("+", "-")
  private def distributeNotifications(events: List[ApiEvent.Public])(implicit ec: ExecutionContext): Unit = {
    val involvedPostIds: Set[PostId] = events.flatMap {
      case ApiEvent.NewGraphChanges(changes) => changes.involvedPostIds
      case _ => Set.empty[PostId]
    }(breakOut)

    scribe.info("Sending subs for " + involvedPostIds)
    db.notifications.getSubscriptions(involvedPostIds).foreach { subscriptions =>
      subscriptions.par.foreach { s =>
        val notification = new Notification(s.endpointUrl, urlsafebase64(s.p256dh), urlsafebase64(s.auth), "content")
        Try(pushService.send(notification)) match {
          case Success(response) => scribe.info(s"Send push subscription: $response")
          case Failure(t) => scribe.error(s"Cannot send push notification: $t")
        }
      }
    }
  }
}
