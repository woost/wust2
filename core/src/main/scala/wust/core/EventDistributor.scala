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
import wust.util.collection._
import com.vdurmont.emoji.EmojiParser

import scala.collection.JavaConverters._
import scala.collection.parallel.ExecutionContextTaskSupport
import scala.collection.parallel.ParSeq
import scala.collection.{breakOut, mutable}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import DbConversions._

sealed trait NotifiedKind
object NotifiedKind {
  case object NewNode extends NotifiedKind
  case object NewMention extends NotifiedKind
  case object NewAssigned extends NotifiedKind
  case object NewInvite extends NotifiedKind
}
final case class NotifiedNode(node: Node.Content, parent: Option[Node.Content], kind: NotifiedKind) {
  val nodeContent = EmojiParser.parseToText(node.data.str.trim)
  val parentContent = parent.map(n => EmojiParser.parseToText(n.data.str.trim))

  def description = kind match {
    case NotifiedKind.NewNode => s"New ${node.role}"
    case NotifiedKind.NewMention => s"Mentioned in ${node.role}"
    case NotifiedKind.NewAssigned => s"Assigned to ${node.role}"
    case NotifiedKind.NewInvite => s"Invited in ${node.role}"
  }
}
final case class NotificationData(userId: UserId, notifiedNodes: collection.Seq[NotifiedNode], subscribedNodeId: NodeId, subscribedNodeContent: String)

//TODO adhere to TOO MANY REQUESTS Retry-after header: https://developers.google.com/web/fundamentals/push-notifications/common-issues-and-reporting-bugs
class HashSetEventDistributorWithPush(db: Db, serverConfig: ServerConfig, pushClients: Option[PushClients])(implicit ec: ExecutionContext) extends EventDistributor[ApiEvent, State] {

  private val subscribers = mutable.HashSet[NotifiableClient[ApiEvent, State]]()
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

    val groupedEvents = events.groupByCollect[Node.User, GraphChanges]  { case a: ApiEvent.NewGraphChanges => (a.user, a.changes) }
    val replacements = events.collect { case a: ApiEvent.ReplaceNode => a }
    groupedEvents.foreach { case (user, changes) =>
      // merge all changes together for this user
      val mergedChanges = changes.foldLeft(GraphChanges.empty)(_ merge _)

      // the author might have changed his own name in this change
      val updatedUser = mergedChanges.addNodes.reverseIterator.collectFirst { case node: Node.User if node.id == user.id => node }

      // filter out read edges - we do not want to notify clients about read edges because there would be too many updates in these clients, which makes the ui too slow.
      // TODO: removing read-edges is a workaround
      val filteredChanges = mergedChanges.copy(
        addEdges = mergedChanges.addEdges.filterNot(_.isInstanceOf[Edge.Read]),
        delEdges = mergedChanges.delEdges.filterNot(_.isInstanceOf[Edge.Read])
      )

      publishPerAuthor(updatedUser.getOrElse(user), filteredChanges, origin)
    }

    publishReplacements(replacements, origin)
  }

  // we send replacements without a check, because they are normally only about users
  //TODO we should not send replacements to everybody, this leaks information. We use replacenodes primarily for merging users.
  // checking whether a user is of interest for a client would need to check whether two users are somehow in one workspace together.
  private def publishReplacements(
    replacements: List[ApiEvent.ReplaceNode],
    origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = {
    // send out notifications to websocket subscribers
    if (replacements.nonEmpty) subscribers.foreach { client =>
      if (origin.forall(_ != client)) client.notify(_ => Future.successful(replacements))
    }
  }

  private def publishPerAuthor(
    author: Node.User,
    graphChanges: GraphChanges,
    origin: Option[NotifiableClient[ApiEvent, State]]
  ): Unit = if (graphChanges.nonEmpty) {
    // send out notifications to websocket subscribers
    subscribers.foreach { client =>
      if (origin.forall(_ != client)) client.notify { state =>

        state.flatMap(getWebsocketNotifications(author, graphChanges))
      }
    }

    //TODO: do not calcualte notified nodes twice, get notified users, then subscriptions/clients...
    // send out push notifications

    val notifiedNodes = distinctBuilder[NodeId, Array]
    val allAddNodesByNodeId = mutable.HashMap[NodeId, Node.Content]()
    val unknownNodeIds = distinctBuilder[NodeId, Array]
    @inline def addPotentiallyUnknownNodeId(nodeId: NodeId) = if (!allAddNodesByNodeId.isDefinedAt(nodeId)) unknownNodeIds += nodeId

    val mentionsByNode = groupByBuilder[NodeId, UserId]
    val assignmentsByNode = groupByBuilder[NodeId, UserId]
    val invitesByNode = groupByBuilder[NodeId, UserId]
    val newNodes = mutable.HashSet[NodeId]()
    graphChanges.addNodes.foreach {
      case node: Node.Content =>
        allAddNodesByNodeId(node.id) = node
        if (InlineList.contains(NodeRole.Message, NodeRole.Project)(node.role)) {
          newNodes += node.id
          notifiedNodes += node.id
        }
      case _ => ()
    }

    // we just treat any mentioned id as a userid, we will not find a corresponding user for node mentions.
    graphChanges.addEdges.foreach {
      case e: Edge.Mention =>
        notifiedNodes += e.nodeId
        mentionsByNode += e.nodeId -> UserId(e.mentionedId)
        addPotentiallyUnknownNodeId(e.nodeId)
      case e: Edge.Assigned =>
        notifiedNodes += e.nodeId
        assignmentsByNode += e.nodeId -> e.userId
        addPotentiallyUnknownNodeId(e.nodeId)
      case e: Edge.Invite =>
        notifiedNodes += e.nodeId
        invitesByNode += e.nodeId -> e.userId
        addPotentiallyUnknownNodeId(e.nodeId)
      case _ =>
        ()
    }

    val parentIdsByChildId = graphChanges.addEdges.toSeq.groupByCollect[NodeId, NodeId] {
      case e: Edge.Child if notifiedNodes.contains(e.childId) =>
        addPotentiallyUnknownNodeId(e.parentId)
        e.childId -> e.parentId
    }

    val sendOutNotifications = for {
      nodeLookupFromDb <- lookupNodesByNodeId(unknownNodeIds.result)
      nodeLookup = nodeLookupFromDb ++ allAddNodesByNodeId
      notifications <- db.notifications.notifiedUsersByNodes(notifiedNodes.result())
      notificationsWithParent = {
        val notificationsWithParent = mutable.ArrayBuffer[NotificationData]()
        notifications.foreach { n =>
          val notifiedNodes = mutable.ArrayBuffer[NotifiedNode]()
          val newNotifiedNodes = n.notifiedNodes.foreach { nodeId =>
            //TODO: parent nodes need to be access-checked per notified user!!
            val parents = parentIdsByChildId.get(nodeId).map(_.flatMap(nodeLookup.get))
            val parent = parents.flatMap(_.headOption) //TODO: this parent is only the first and not checked for access!!!!!
            nodeLookup.get(nodeId).foreach { node =>
              assignmentsByNode.result.get(nodeId).collect { case users if users.contains(n.userId) => NotifiedKind.NewAssigned } orElse
                mentionsByNode.result.get(nodeId).collect { case users if users.contains(n.userId) => NotifiedKind.NewMention } orElse
                invitesByNode.result.get(nodeId).collect { case users if users.contains(n.userId) => NotifiedKind.NewMention } orElse
                (if (newNodes.contains(nodeId) && parent.nonEmpty) Some(NotifiedKind.NewNode) else None) foreach { kind =>
                  notifiedNodes += NotifiedNode(node, parent, kind)
                }
            }
          }
          if (notifiedNodes.nonEmpty) {
            val data = NotificationData(n.userId, notifiedNodes, n.subscribedNodeId, EmojiParser.parseToText(n.subscribedNodeContent))
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
    }

    sendOutNotifications.onComplete {
      case Success(()) => ()
      case Failure(t) => scribe.error("Cannot send out push notifications", t)
    }
  }

  private def lookupNodesByNodeId(nodes: collection.Seq[NodeId]): Future[collection.Map[NodeId, Node.Content]] = {
    if(nodes.nonEmpty) {
      db.node.get(nodes).map { nodes =>
        val map = mutable.HashMap[NodeId, Node.Content]()
        nodes.foreach { node =>
          // only take relavant parent roles
          if (InlineList.contains(NodeRole.Message, NodeRole.Task, NodeRole.Project, NodeRole.Note)(node.role)) {
            forClient(node) match {
              case node: Node.Content => map(node.id) = node
              case _ => ()
            }
          }
        }
        map
      }
    } else Future.successful(Map.empty[NodeId, Node.Content])
  }

  private def getWebsocketNotifications(author: Node.User, graphChanges: GraphChanges)(state: State): Future[List[ApiEvent]] = {
    state.auth.fold(Future.successful(List.empty[ApiEvent])) { auth =>
      db.notifications.updateNodesForConnectedUser(auth.user.id, graphChanges.involvedNodeIds.toSeq)
        .map { permittedNodeIds =>
          val filteredChanges = graphChanges.filterCheck(permittedNodeIds.toSet, {
            case e: Edge.User    => List(e.sourceId)
            case e: Edge.Content => List(e.sourceId, e.targetId)
          })

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
            content = notifiedNode.nodeContent,
            nodeId = notifiedNode.node.id.toBase58,
            subscribedId = subscribedNodeId.toBase58,
            subscribedContent = subscribedNodeContent,
            parentId = notifiedNode.parent.map(_.id.toBase58),
            parentContent = notifiedNode.parentContent,
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

          val content = s"${ if (author.name.isEmpty) "Unregistered User" else author.name } in ${ StringOps.trimToMaxLength(notifiedNode.parent.fold(subscribedNodeContent)(_.data.str), 50)} (${notifiedNode.description}): ${ StringOps.trimToMaxLength(notifiedNode.nodeContent, 250) }"
          val contentUrl = s"https://${ serverConfig.host }/#page=${ notifiedNode.parent.fold(subscribedNodeId)(_.id).toBase58 }"

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
