package wust.backend

import wust.api._
import wust.graph.{GraphChanges, Node}
import wust.db.{Data, Db}
import wust.ids._
import covenant.ws.api.EventDistributor
import mycelium.server.NotifiableClient

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.breakOut
import scala.collection.parallel.ExecutionContextTaskSupport
import wust.backend.config.PushNotificationConfig

import scala.util.{Failure, Success, Try}

class HashSetEventDistributorWithPush(db: Db, pushConfig: Option[PushNotificationConfig])(
    implicit ec: ExecutionContext
) extends EventDistributor[ApiEvent, State] {

  private val subscribers = mutable.HashSet.empty[NotifiableClient[ApiEvent, State]]
  private val pushService = pushConfig.map(PushService.apply)

  def subscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers -= client
  }

  //TODO write this in a less unit style, compose tasks.
  // origin is optional, since http clients can also trigger events
  override def publish(
      events: List[ApiEvent],
      origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = if (events.nonEmpty) {
    scribe.info(s"Event distributor (${subscribers.size} clients): $events from $origin")

    val (checkedNodeIdsList, uncheckedNodeIdsList) = events.map {
      case ApiEvent.NewGraphChanges(changes) =>
        val userIds: Set[UserId] = changes.addNodes.collect { case c: Node.User => c.id }(breakOut) //FIXME we cannot not check permission on users nodes as they currentl have no permissions, we just allow them for now.
        (changes.involvedNodeIds.toSet -- userIds, userIds)
      case _ => (Set.empty[NodeId], Set.empty[UserId])
    }.unzip
    val checkedNodeIds: Set[NodeId] = checkedNodeIdsList.toSet.flatten
    val uncheckedNodeIds: Set[UserId] = uncheckedNodeIdsList.toSet.flatten

    subscribers.foreach { client =>
      if (origin.fold(true)(_ != client))
        client.notify(stateFut =>
          stateFut.flatMap { state =>
            state.auth.fold(Future.successful(List.empty[ApiEvent])) { auth =>
              db.notifications.updateNodesForConnectedUser(auth.user.id, checkedNodeIds)
                .map(permittedNodeIds => events.map(eventFilter(uncheckedNodeIds ++ permittedNodeIds)))
            }
          }
        )
    }

    db.notifications.getAllSubscriptions().onComplete {
      case Success(subscriptions) => distributeNotifications(subscriptions, events, checkedNodeIds)
      case Failure(t) => scribe.warn(s"Failed to get webpush subscriptions", t)
    }
  }

  private def distributeNotifications(subscriptions: List[Data.WebPushSubscription], events: List[ApiEvent], nodeIds: Set[NodeId]): Unit = pushService.foreach { pushService =>
    // see https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
    val expiryStatusCodes = Set(404, 410)
    val successStatusCode = 201

    //TODO really .par?
    val parallelSubscriptions = subscriptions.par
    parallelSubscriptions.tasksupport = new ExecutionContextTaskSupport(ec)

    val expiredSubscriptions = parallelSubscriptions.map { s =>
      db.notifications.notifiedNodesForUser(s.userId, nodeIds).transformWith {
        case Success(permittedNodeIds) =>
          val filteredEvents = events.map(eventFilter(permittedNodeIds.toSet))
          val payload = filteredEvents.collect {
            case ApiEvent.NewGraphChanges(changes) if changes.addNodes.nonEmpty =>
              changes.addNodes.map(_.data.str.trim)
          }.flatten

          if (payload.isEmpty) {
            Future.successful(None)
          } else {
            pushService.send(s, payload.mkString(" | ")).transform {
              case Success(response) =>
                response.getStatusLine.getStatusCode match {
                  case `successStatusCode` =>
                    Success(None)
                  case statusCode if expiryStatusCodes.contains(statusCode) =>
                    Success(Some(s))
                  case _ =>
                    Success(None)
                }
              case Failure(t) =>
                scribe.error(s"Cannot send push notification, due to unexpected exception: $t")
                Success(None)
            }
          }

        case Failure(t) =>
          scribe.warn("Failed to query permitted node ids for events", t)
          Future.successful(None)
      }
    }

    Future.traverse(expiredSubscriptions.seq)(identity).foreach { expiredSubscriptions =>
      val flatExpiredSubscriptions = expiredSubscriptions.flatten
      if (flatExpiredSubscriptions.nonEmpty) {
        db.notifications.delete(flatExpiredSubscriptions.toSet).onComplete {
          case Success(res) =>
            scribe.info(s"Deleted expired subscriptions ($expiredSubscriptions): $res")
          case Failure(res) =>
            scribe.info(s"Failed to delete expired subscriptions ($expiredSubscriptions), due to exception: $res")
        }
      }
    }
  }

  private def eventFilter(allowedNodeIds: Set[NodeId]): ApiEvent => ApiEvent = {
    case ApiEvent.NewGraphChanges(changes) => ApiEvent.NewGraphChanges(changes.filter(allowedNodeIds))
    case other => other
  }
}
