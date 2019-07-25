package wust.core

import akka.stream.ActorMaterializer
import covenant.ws.api.EventDistributor
import mycelium.server.NotifiableClient
import wust.api.ApiEvent.NewGraphChanges
import wust.api._
import wust.core.config.ServerConfig
import wust.core.pushnotifications.{PushClients, PushData, PushedClient, WebPushService}
import wust.db.Data.NotifiedUsersRow
import wust.db.{Data, Db}
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.util.macros.InlineList

import scala.collection.JavaConverters._
import scala.collection.parallel.ExecutionContextTaskSupport
import scala.collection.parallel.ParSeq
import scala.collection.{breakOut, mutable}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import DbConversions._

final case class NewNodeNotification(node: Node, parent: Node) {
  def description = s"New ${node.role}"
}
final case class NotificationData(userId: UserId, notifiedNodes: collection.Seq[NewNodeNotification], subscribedNodeId: NodeId, subscribedNodeContent: String)

//TODO adhere to TOO MANY REQUESTS Retry-after header: https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
class HashSetEventDistributorWithPush(db: Db, serverConfig: ServerConfig, pushClients: Option[PushClients])(implicit ec: ExecutionContext) extends EventDistributor[ApiEvent, State] {

  private val subscribers = mutable.HashSet.empty[NotifiableClient[ApiEvent, State]]
  private val webPushService = pushClients.flatMap(_.webPushService)
  private val pushedClient = pushClients.flatMap(_.pushedClient)

  def subscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers += client
  }

  def unsubscribe(client: NotifiableClient[ApiEvent, State]): Unit = {
    subscribers -= client
  }

  // origin is optional, since http clients can also trigger events
  override def publish(
    events: List[ApiEvent],
    origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = if (events.nonEmpty) Future {
    scribe.info(s"Event distributor (${ subscribers.size } clients): $events from $origin.")

    val groupedEvents = events.collect { case a: ApiEvent.NewGraphChanges => a }.groupBy(_.user)
    val replacements = events.collect { case a: ApiEvent.ReplaceNode => a }
    groupedEvents.foreach { case (user, changes) =>
      val mergedChanges = changes.foldLeft(GraphChanges.empty)(_ merge _.changes)
      // filter out read edges - we do not want to notify clients about read edges because there would be too many updates in these clients, which makes the ui too slow.
      // TODO: removing read-edges is a workaround
      val filteredChanges = mergedChanges.copy(
        addEdges = mergedChanges.addEdges.filterNot(_.isInstanceOf[Edge.Read]),
        delEdges = mergedChanges.delEdges.filterNot(_.isInstanceOf[Edge.Read])
      )
      publishPerAuthor(user, filteredChanges, origin)
    }

    publishReplacements(replacements, origin)
  }

  //TODO do not send replacements to everybody, this leaks information. We use replacenodes primarily for merging users.
  // checking whether a user is of interest for a client would need to check whether two users are somehow in one workspace together.
  private def publishReplacements(
    replacements: List[ApiEvent.ReplaceNode],
    origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = {
    // send out notifications to websocket subscribers
    if (replacements.nonEmpty) subscribers.foreach { client =>
      if (origin.fold(true)(_ != client)) client.notify(_ => Future.successful(replacements))
    }
  }

  private def publishPerAuthor(
    author: Node.User,
    graphChanges: GraphChanges,
    origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = {
    // send out notifications to websocket subscribers
    if (graphChanges.nonEmpty) subscribers.foreach { client =>
      if (origin.fold(true)(_ != client)) client.notify(state =>
        state.flatMap(getWebsocketNotifications(author, graphChanges))
      )
    }

    //TODO: do not calcualte notified nodes twice, get notified users, then subscriptions/clients...
    // send out push notifications
    if (graphChanges.nonEmpty) {
      val addNodesByNodeId: Map[NodeId, Node] = graphChanges.addNodes.collect {
        case node: Node.Content if InlineList.contains(NodeRole.Message, NodeRole.Project)(node.role) => node.id -> node
      }(breakOut)
      (for {
        parentNodesByChildId <- parentNodesByChildId(graphChanges)
        notifications <- db.notifications.notifiedUsersByNodes(addNodesByNodeId.keys.toList)
        notificationsWithParent = {
          val notificationsWithParent = mutable.ArrayBuffer[NotificationData]()
          notifications.foreach { n =>
            val newNodeNotifications = mutable.ArrayBuffer[NewNodeNotification]()
            val newNotifiedNodes = n.notifiedNodes.foreach { nodeId =>
              parentNodesByChildId.get(nodeId).foreach { parents =>
                parents.foreach { parent =>
                  newNodeNotifications += NewNodeNotification(addNodesByNodeId(nodeId), parent)
                }
              }
            }
            if (newNodeNotifications.nonEmpty) {
              val data = NotificationData(n.userId, newNodeNotifications, n.subscribedNodeId, n.subscribedNodeContent)
              notificationsWithParent += data
            }
          }

          notificationsWithParent
        }
      } yield {
        webPushService.foreach { webPushService =>
          sendWebPushNotifications(webPushService, author, notificationsWithParent)
        }
        pushedClient.foreach { pushedClient =>
          sendPushedNotifications(pushedClient, author, notificationsWithParent)
        }

        ()
      }).onComplete {
        case Success(()) =>
        case Failure(t) =>
          scribe.error("Cannot send out push notifications", t)
      }
    }
  }

  private def parentNodesByChildId(graphChanges: GraphChanges): Future[collection.Map[NodeId, collection.Seq[Node]]] = {
    val childIdsByParentId = mutable.HashMap[NodeId, mutable.ArrayBuffer[NodeId]]()
    graphChanges.addEdges.foreach {
      case e: Edge.Child =>
        val buf = childIdsByParentId.getOrElseUpdate(e.parentId, mutable.ArrayBuffer[NodeId]())
        buf += e.childId
      case _ => ()
    }

    if(childIdsByParentId.nonEmpty) {
      db.node.get(childIdsByParentId.keySet).map { parentNodes =>
        val parentNodesByChildId = mutable.HashMap[NodeId, mutable.ArrayBuffer[Node]]()
        parentNodes.foreach {
          // only take relavant parent roles
          case parentNode if InlineList.contains(NodeRole.Message, NodeRole.Task, NodeRole.Project, NodeRole.Note)(parentNode.role) =>
            childIdsByParentId(parentNode.id).foreach { childId =>
              val buf = parentNodesByChildId.getOrElseUpdate(childId, mutable.ArrayBuffer[Node]())
              buf += forClient(parentNode)
            }
          case _ => ()
        }

        parentNodesByChildId
      }
    } else Future.successful(Map.empty[NodeId, Seq[Node]])
  }

  private def getWebsocketNotifications(author: Node.User, graphChanges: GraphChanges)(state: State): Future[List[ApiEvent]] = {
    state.auth.fold(Future.successful(List.empty[ApiEvent])) { auth =>
      db.notifications.updateNodesForConnectedUser(auth.user.id, graphChanges.involvedNodeIds)
        .map { permittedNodeIds =>
          val filteredChanges = graphChanges.filterCheck(permittedNodeIds.toSet, {
            case e: Edge.User    => List(e.sourceId)
            case e: Edge.Content => List(e.sourceId, e.targetId)
          })

          // we send replacements without a check, because they are normally only about users
          // TODO: actually check whether we have access to the replaced nodes
          if(filteredChanges.isEmpty) Nil
          else NewGraphChanges.forPrivate(author, filteredChanges) :: Nil
        }
    }
  }

  private def deleteWebPushSubscriptions(subscriptions: Seq[Data.WebPushSubscription]): Unit = if(subscriptions.nonEmpty) {
    db.ctx.transaction { implicit ec =>
      db.notifications.delete(subscriptions)
    }.onComplete {
      case Success(res) =>
        scribe.info(s"Deleted web push subscriptions ($subscriptions): $res.")
      case Failure(res) =>
        scribe.error(s"Failed to delete web push subscriptions ($subscriptions), due to exception: $res.")
    }
  }

  private def deleteOAuthClients(subscriptions: Seq[Data.OAuthClient]): Unit = if(subscriptions.nonEmpty) {
    db.ctx.transaction { implicit ec =>
      db.oAuthClients.delete(subscriptions)
    }.onComplete {
      case Success(res) =>
        scribe.info(s"Deleted oauth client subscriptions ($subscriptions): $res.")
      case Failure(res) =>
        scribe.error(s"Failed to delete oauth client subscriptions ($subscriptions), due to exception: $res.")
    }
  }

  private def sendWebPushNotifications(
    pushService: WebPushService,
    author: Node.User,
    notifications: Seq[NotificationData]): Unit = {

    // see https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
    val expiryStatusCodes = Set(
      404, 410, // expired
    )
    val invalidHeaderCodes = Set(
      400, 401, // invalid auth headers
    )
    val mismatchSenderIdCode = 403
    val tooManyRequestsCode = 429
    val payloadTooLargeCode = 413
    val successStatusCode = 201

    //TODO really .par?
    val parallelNotifications = notifications.par
    parallelNotifications.tasksupport = new ExecutionContextTaskSupport(ec)

    val expiredSubscriptions: ParSeq[Seq[Future[Seq[Data.WebPushSubscription]]]] = parallelNotifications.map {
      case NotificationData(userId, notifiedNodes, subscribedNodeId, subscribedNodeContent) if userId != author.id =>
        notifiedNodes.map { notifiedNode =>

          val pushData = PushData(
            username = author.name,
            content = notifiedNode.node.data.str.trim,
            nodeId = notifiedNode.node.id.toBase58,
            subscribedId = subscribedNodeId.toBase58,
            subscribedContent = subscribedNodeContent,
            parentId = Some(notifiedNode.parent.id.toBase58),
            parentContent = Some(notifiedNode.parent.data.str),
            epoch = EpochMilli.now,
            description = notifiedNode.description
          )

          db.notifications.getSubscription(userId).flatMap { subscriptions =>
            val futureSeq = subscriptions.map { subscription =>
              pushService.send(subscription, pushData).transform {
                case Success(response) =>
                  response.getStatusLine.getStatusCode match {
                    case `successStatusCode`                                   =>
                      Success(Nil)
                    case statusCode if expiryStatusCodes.contains(statusCode)  =>
                      scribe.info(s"Subscription expired. Deleting subscription.")
                      Success(List(subscription))
                    case statusCode if invalidHeaderCodes.contains(statusCode) =>
                      scribe.error(s"Invalid headers. Deleting subscription.")
                      Success(List(subscription))
                    case `mismatchSenderIdCode`                                =>
                      scribe.error(s"Mismatch sender id error. Deleting subscription.")
                      Success(List(subscription))
                    case `tooManyRequestsCode`                                 =>
                      scribe.error(s"Too many requests.")
                      Success(Nil)
                    case `payloadTooLargeCode`                                 =>
                      scribe.error(s"Payload too large.")
                      Success(Nil)
                    case _                                                     =>
                      val body = new java.util.Scanner(response.getEntity.getContent).asScala.mkString
                      scribe.error(s"Unexpected success code: $response body: $body.")
                      Success(Nil)
                  }
                case Failure(t)        =>
                  scribe.error(s"Cannot send push notification, due to unexpected exception: $t.")
                  Success(Nil)
              }
            }

            Future.sequence(futureSeq).map(_.flatten)
          }
        }
      case _ => List.empty[Future[List[Data.WebPushSubscription]]]
    }

    Future.sequence(expiredSubscriptions.seq.flatten)
      .map(_.flatten)
      .foreach(deleteWebPushSubscriptions)
  }

  private def sendPushedNotifications(
    pushedClient: PushedClient,
    author: Node.User,
    notifications: Seq[NotificationData]): Unit = {

    val expiryStatusCodes = Set(
      404, 410, // expired
    )
    val tooManyRequestsCode = 429
    val payloadTooLargeCode = 413
    val successStatusCode = 200

    //TODO really .par?
    val parallelNotifications = notifications.par
    parallelNotifications.tasksupport = new ExecutionContextTaskSupport(ec)

    val expiredSubscriptions: ParSeq[Seq[Future[Seq[Data.OAuthClient]]]] = parallelNotifications.map {
      case NotificationData(userId, notifiedNodes, subscribedNodeId, subscribedNodeContent) if userId != author.id =>
        notifiedNodes.map { notifiedNode =>

          val content = s"${ if (author.name.isEmpty) "Unregistered User" else author.name } in ${ StringOps.trimToMaxLength(notifiedNode.parent.data.str, 50)}: ${ StringOps.trimToMaxLength(notifiedNode.node.data.str.trim, 250) }"
          val contentUrl = s"https://${ serverConfig.host }/#page=${ notifiedNode.node.id.toBase58 }"

          db.oAuthClients.get(userId, OAuthClientService.Pushed).flatMap { subscriptions =>
            val futureSeq = subscriptions.map { subscription =>
              pushedClient.sendPush(subscription.accessToken, content = content, url = contentUrl).transform {
                case Success(response) =>
                  response match {
                    case `successStatusCode`                                  =>
                      Success(Nil)
                    case statusCode if expiryStatusCodes.contains(statusCode) =>
                      scribe.info(s"Subscription expired. Deleting subscription.")
                      Success(List(subscription))
                    case `tooManyRequestsCode`                                =>
                      scribe.error(s"Too many requests.")
                      Success(Nil)
                    case `payloadTooLargeCode`                                =>
                      scribe.error(s"Payload too large.")
                      Success(Nil)
                    case _                                                    =>
                      scribe.error(s"Unexpected success code: $response.")
                      Success(Nil)
                  }
                case Failure(t)        =>
                  scribe.error(s"Cannot send push notification, due to unexpected exception: $t.")
                  Success(Nil)
              }
            }

            Future.sequence(futureSeq).map(_.flatten)
          }
        }
      case _ => List.empty[Future[List[Data.OAuthClient]]]
    }

    Future.sequence(expiredSubscriptions.seq.flatten)
      .map(_.flatten)
      .foreach(deleteOAuthClients)
  }
}
