package wust.backend

import wust.api._
import wust.db.Db
import wust.ids._
import covenant.ws.api.EventDistributor
import mycelium.server.NotifiableClient
import nl.martijndwars.webpush._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.collection.breakOut
import scala.concurrent.duration._
import scala.collection.parallel.ExecutionContextTaskSupport
import java.security.{PrivateKey, PublicKey, Security}

import wust.backend.config.PushNotificationConfig

import scala.util.{Failure, Success, Try}

class HashSetEventDistributorWithPush(db: Db, pushConfig: Option[PushNotificationConfig])(implicit ec: ExecutionContext) extends EventDistributor[ApiEvent, State] {

  private val subscribers = mutable.HashSet.empty[NotifiableClient[ApiEvent, State]]

  //TODO: somewhere else?
  pushConfig.foreach { _ =>
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
  }

  private def base64UrlSafe(s:String) = s.replace("/", "_").replace("+", "-")

  private val pushService = pushConfig.map(c => new PushService(base64UrlSafe(c.keys.publicKey), base64UrlSafe(c.keys.privateKey), c.subject)) //TODO: expiry?

  def subscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers -= client
  }

  //TODO write this in a less unit style, compose tasks.
  // origin is optional, since http clients can also trigger events
  override def publish(events: List[ApiEvent], origin: Option[NotifiableClient[ApiEvent, State]]): Unit = if (events.nonEmpty) {
    scribe.info(s"Event distributor (${subscribers.size} clients): $events from $origin")

    val involvedPostIds: Set[PostId] = events.flatMap {
      case ApiEvent.NewGraphChanges(changes) => changes.involvedPostIds
      case _ => Set.empty[PostId]
    }(breakOut)

    db.notifications.notifiedUsers(involvedPostIds).onComplete {
      
      case Success(notifiedUsers) =>
        val eventsByUser: Map[UserId, List[ApiEvent]] = notifiedUsers.mapValues { postIds => 
          events.map{
            case ApiEvent.NewGraphChanges(changes) => ApiEvent.NewGraphChanges(changes.filter(postIds.toSet))
            case other => other
          }
        }

        subscribers.foreach { client =>
          if (origin.fold(true)(_ != client))
            client.notify(stateFut => stateFut.map{ state =>
              eventsByUser.getOrElse(state.auth.user.id, List.empty[ApiEvent])
            })
        }

        distributeNotifications(eventsByUser)

      case Failure(t) => scribe.warn(s"Failed get notified users for events ($events)", t)
    }
  }

  private def distributeNotifications(notifiedUsers: Map[UserId, List[ApiEvent]]): Unit = pushService.foreach { pushService =>
    // see https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
    val expiryStatusCodes = Set(404, 410)
    val successStatusCode = 201

    if (notifiedUsers.nonEmpty) {
      val notifiedUsersGraphChanges = notifiedUsers
        .mapValues(_.collect { case ApiEvent.NewGraphChanges(changes) if changes.addPosts.nonEmpty =>
          changes.addPosts.map(_.content.externalString.trim).mkString(" | ") }.mkString(" | "))
        .filter(_._2.nonEmpty)

      db.notifications.getSubscriptions(notifiedUsersGraphChanges.keySet).foreach { subscriptions =>
        val parallelSubscriptions = subscriptions.par
        parallelSubscriptions.tasksupport = new ExecutionContextTaskSupport(ec)

        val expiredSubscriptions = parallelSubscriptions.filter { s =>
          val payload = notifiedUsersGraphChanges(s.userId)
          val notification = new Notification(s.endpointUrl, base64UrlSafe(s.p256dh), base64UrlSafe(s.auth), payload.toString)
          //TODO sendAsync? really .par?
          Try(pushService.send(notification)) match {
            case Success(response) =>
              response.getStatusLine.getStatusCode match {
                case `successStatusCode` =>
                  scribe.info(s"Successfully sent push notification")
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
}
