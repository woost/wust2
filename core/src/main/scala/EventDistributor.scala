package wust.backend

import wust.api._
import wust.graph.{Edge, GraphChanges, Node}
import wust.db.{Data, Db}
import wust.ids._
import scala.collection.JavaConverters._
import covenant.ws.api.EventDistributor
import mycelium.server.NotifiableClient
import wust.api.ApiEvent.NewGraphChanges

import scala.collection.{breakOut, mutable}
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.parallel.ExecutionContextTaskSupport
import wust.backend.config.PushNotificationConfig
import wust.db.Data.RawPushData

import scala.collection.parallel.immutable.ParSeq
import scala.util.{Failure, Success}

//TODO adhere to TOO MANY REQUESTS Retry-after header: https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
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

  // origin is optional, since http clients can also trigger events
  override def publish(
    events: List[ApiEvent],
    origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = if (events.nonEmpty) Future {
    scribe.info(s"Event distributor (${ subscribers.size } clients): $events from $origin")

    val groupedEvents = events.collect { case a: ApiEvent.NewGraphChanges => a }.groupBy(_.user)
    groupedEvents.foreach { case (user, changes) =>
      publishPerAuthor(user, changes.foldLeft(GraphChanges.empty)(_ merge _.changes), origin)
    }
  }

  private def publishPerAuthor(
    author: Node.User,
    graphChanges: GraphChanges,
    origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = if (graphChanges.nonEmpty) {
    // send out notifications to websocket subscribers
    subscribers.foreach { client =>
      if (origin.fold(true)(_ != client)) client.notify(state =>
        state.flatMap(sendWebsocketNotifications(author, graphChanges))
      )
    }

    // send out push notifications
    pushService.foreach { pushService =>
      val addNodesByNodeId: Map[NodeId, Node] = graphChanges.addNodes.map(node => node.id -> node)(breakOut)
      for {
        parentNodeByChildId <- parentNodeByChildId(graphChanges)
        rawPushMeta <- determinePushPerUser(addNodesByNodeId.keys)
      } sendPushNotifications(pushService, author, addNodesByNodeId, parentNodeByChildId, rawPushMeta)
    }
  }

  private def determinePushPerUser(nodesOfInterest: Iterable[NodeId]): Future[List[RawPushData]] = {
    // since the push data is coming from the database, the contained nodes are already checked for permissions for each user
    db.notifications.notifyDataBySubscribedNodes(nodesOfInterest.toList)
  }

  private def parentNodeByChildId(graphChanges: GraphChanges): Future[Map[NodeId, Data.Node]] = {
    val childIdByParentId: Map[NodeId, NodeId] = graphChanges.addEdges.collect { case e: Edge.Parent => e.parentId -> e.childId }(breakOut)

    if(childIdByParentId.nonEmpty) {
      db.node.get(childIdByParentId.keySet).map(_.map(parentNode => childIdByParentId(parentNode.id) -> parentNode).toMap)
    } else Future.successful(Map.empty[NodeId, Data.Node])
  }

  private def sendWebsocketNotifications(author: Node.User, graphChanges: GraphChanges)(state: State): Future[List[ApiEvent]] = {
    state.auth.fold(Future.successful(List.empty[ApiEvent])) { auth =>
      db.notifications.updateNodesForConnectedUser(auth.user.id, graphChanges.involvedNodeIds.toSet)
        .map{ permittedNodeIds =>
          val filteredChanges = graphChanges.filterCheck(permittedNodeIds.toSet, {
            case e: Edge.Author => List(e.nodeId)
            case e: Edge.Member => List(e.nodeId)
            case e: Edge.Notify => List(e.nodeId)
            case e => List(e.sourceId, e.targetId)
          })
          if(filteredChanges.isEmpty) Nil
          else NewGraphChanges(author, filteredChanges) :: Nil
        }
    }
  }

  private def deleteExpiredSubscriptions(subscriptions: Seq[Data.WebPushSubscription]): Unit = if(subscriptions.nonEmpty) {
    db.notifications.delete(subscriptions).onComplete {
      case Success(res) =>
        scribe.info(s"Deleted expired subscriptions ($subscriptions): $res")
      case Failure(res) =>
        scribe.info(s"Failed to delete expired subscriptions ($subscriptions), due to exception: $res")
    }
  }

  private def sendPushNotifications(
    pushService: PushService,
    author: Node.User,
    addNodesByNodeId: Map[NodeId, Node],
    parentNodeByChildId: Map[NodeId, Data.Node],
    rawPushMeta: List[RawPushData]): Unit = {

    // see https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
    val expiryStatusCodes = Set(
      404, 410, // expired
    )
    val invalidHeaderCodes = Set(
      400, 401, // invalid auth headers
    )
    val tooManyRequestsCode = 429
    val payloadTooLargeCode = 413
    val successStatusCode = 201

    //TODO really .par?
    val parallelRawPushMeta = rawPushMeta.par
    parallelRawPushMeta.tasksupport = new ExecutionContextTaskSupport(ec)

    val expiredSubscriptions: ParSeq[List[Future[Option[Data.WebPushSubscription]]]] = parallelRawPushMeta.map {
      case RawPushData(subscription, notifiedNodes, subscribedNodeId, subscribedNodeContent) if subscription.userId != author.id =>
        notifiedNodes.map { nodeId =>
          val node = addNodesByNodeId(nodeId)

          val pushData = PushData(author.name,
            node.data.str.trim,
            node.id.toBase58,
            subscribedNodeId.toBase58,
            subscribedNodeContent,
            parentNodeByChildId.get(nodeId).map(_.id.toBase58),
            parentNodeByChildId.get(nodeId).map(_.data.str),
          )

          pushService.send(subscription, pushData).transform {
            case Success(response) =>
              response.getStatusLine.getStatusCode match {
                case `successStatusCode`                                   =>
                  Success(None)
                case statusCode if expiryStatusCodes.contains(statusCode)  =>
                  scribe.info(s"Subscription expired")
                  Success(Some(subscription))
                case statusCode if invalidHeaderCodes.contains(statusCode) =>
                  scribe.error(s"Invalid headers")
                  Success(None)
                case `tooManyRequestsCode`                                 =>
                  scribe.error(s"Too many requests.")
                  Success(None)
                case `payloadTooLargeCode`                                 =>
                  scribe.error(s"Payload too large.")
                  Success(None)
                case _                                                     =>
                  val body = new java.util.Scanner(response.getEntity.getContent).asScala.mkString
                  scribe.error(s"Unexpected success code: $response body: $body")
                  Success(None)
              }
            case Failure(t)        =>
              scribe.error(s"Cannot send push notification, due to unexpected exception: $t")
              Success(None)
          }
        }
      case _ => List.empty[Future[Option[Data.WebPushSubscription]]]
    }

    Future.sequence(expiredSubscriptions.seq.flatten)
      .map(_.flatten)
      .foreach(deleteExpiredSubscriptions)
  }

}
