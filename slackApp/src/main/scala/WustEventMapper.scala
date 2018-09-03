package wust.slack

import wust.graph.Node._
import wust.graph._
import wust.ids._
import wust.slack.Data._
import wust.util.collection._
import cats.data.OptionT
import cats.implicits._
import slack.api.{ApiError, SlackApiClient}
import slack.models._
import akka.actor.ActorSystem
import monix.eval.Task
import monix.execution.Scheduler

import scala.collection.breakOut
import scala.collection.Set
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait GraphChangeEvent

object FilterEvents {

  // AST for GraphChanges to Slack Events
  sealed abstract class SlackEvent
  case class Delete(l: Level) extends SlackEvent
  case class Archive(l: Level) extends SlackEvent
  case class UnArchive(l: Level) extends SlackEvent
  case class Create(l: Level) extends SlackEvent
  case class Update(l: Level) extends SlackEvent

  case class PartialCreateByNode(node: Node) extends SlackEvent
  case class PartialCreateByEdge(edge: Edge) extends SlackEvent

  sealed trait WustEvent
  case class AddNode(node: Node) extends WustEvent
  case class AddEdge(edge: Edge, node: Option[Node]) extends WustEvent
  case class DelEdge(edge: Edge) extends WustEvent

  sealed trait Level
  case class Channel(channel: Channel_Mapping) extends Level
  case class Group(group: Channel_Mapping) extends Level
  case class Thread(thread: Message_Mapping) extends Level
  case class Message(message: Message_Mapping) extends Level
  case class Team(team: Team_Mapping) extends Level

  def separateGraphChanges(graphChanges: GraphChanges) = {
    val nodeMap = graphChanges.addNodes.by(_.id)

    graphChanges.addNodes.map(n => AddNode(n)) ++
    graphChanges.addEdges.map(e => AddEdge(e, nodeMap.get(e.sourceId))) ++
    graphChanges.delEdges.map(e => DelEdge(e))
  }

  def levelSeparator(persistenceAdapter: PersistenceAdapter, nodeId: NodeId): Task[Option[Level]] = {
    persistenceAdapter.getMessageMappingByWustId(nodeId).flatMap {
      case Some(m) =>
        Task.pure(m.slack_thread_ts match {
          case Some(_) => Some(Thread(m))
          case _        => Some(Message(m))
        })
      case _       =>
        persistenceAdapter.getChannelMappingByWustId(nodeId).flatMap {
          case Some(c) =>
            Task.pure(
              if(c.is_private) Some(Group(c))
              else Some(Channel(c))
            )
          case _ =>
            persistenceAdapter.getTeamMappingByWustId(nodeId).map {
              case Some(t) => Some(Team(t))
              case _ => None
            }
        }
    }
  }

  def transformGraphChanges(persistenceAdapter: PersistenceAdapter, w: WustEvent): Task[Option[SlackEvent]] = {
    w match {
      case DelEdge(e) =>
        e match {
          case e @ Edge.Parent(_, _, _) =>
            levelSeparator(persistenceAdapter, e.sourceId).map(_.map(Delete))
          case _ => Task.pure(None)
        }
      case AddEdge(e, n) =>
        e match {
          case Edge.Parent(sourceId, _, targetId) =>
            levelSeparator(persistenceAdapter, targetId).flatMap {
              case Some(Team(_)) =>       // Parent is a team => channel or group
                persistenceAdapter.getChannelMappingByWustId(sourceId).map {

                  case Some(c) => // node is known in some way
                    if(c.team_wust_id == targetId) { // mapping already known, do nothing (idempotence)
                      None
                    } else { // a new mapping has to be created, so a node is linked to different slack channels
                      val isGroup = n match {
                        case Some(node) =>
                          node.meta.accessLevel == NodeAccess.Restricted // decide whether this is a group or channel by node access
                        case None =>
                          c.is_private // fallback when no node is present
                      }
                      if(isGroup) Some(Create(Group(c)))
                      else Some(Create(Channel(c)))
                    }

                  case None => // node is unknown
                    n match {
                      case Some(node) =>
                        if(node.meta.accessLevel == NodeAccess.Restricted)
                          Some(Create(Group(Channel_Mapping(None, node.str, true, false, node.id, targetId))))
                        else
                          Some(Create(Channel(Channel_Mapping(None, node.str, false, false, node.id, targetId))))

                      case None =>
                        Some(PartialCreateByEdge(e)) // |NodeCase| -> Request node from backend
                    }

                }

              case Some(_: Channel) | Some(_: Group) =>    // Parent is a channel => message or thread
                levelSeparator(persistenceAdapter, sourceId).map {
                  case Some(Thread(m)) =>      // update group
                    if(m.channel_wust_id == targetId){
                      None
                    } else {
                      //TODO: ???
                      Some(Create(Message(m.copy(slack_channel_id = None, slack_message_ts = None, slack_thread_ts = None)))
                    }

                  case Some(Message(m)) =>    // update channel

                  case _ => // insert
                    None
                }

              case Some(Thread(_)) =>     // Parent is a thread => message
                levelSeparator(persistenceAdapter, sourceId).map {
                  case Some(Message(m)) =>      // update message
                    Some(UpdateMessage(m))

                  case _ => // insert message
                    None
                }

              case _ =>
                Task.pure(None)
            }
          case _                      => Task.pure(None)
        }

      case AddNode(n) =>
        n match {
          case Node.Content(id, _, _) =>
            levelSeparator(persistenceAdapter, id).map {
              case Some(l) => // Node is known, therefore this is an update
                Some(Update(l))

              case _ =>
//                Some(PartialCreateByNode(n)) // Node is unknown, so we have to create something somewhere
                None // Forget this case, it will be handled with a request in the AddEdge pipeline (see |NodeCase|)

            }
        }
    }
    ???
  }






































  private def combineLevels[A](messageLevel: Task[Option[A]], channelLevel: Task[Option[A]]) = {
    messageLevel.flatMap {
      case Some(m) => Task.pure(Some(m))
      case _ => channelLevel
    }
  }

  private def filterWrapper[A, B](graphChanges: GraphChanges)(f: GraphChanges => Set[A])(m: A => Task[Option[B]])(c: A => Task[Option[B]]) = {
    Task.sequence(
      f(graphChanges).map(e =>
          combineLevels[B](m(e), c(e))
          )
        ).map(_.flatten)
  }

  /**
   * Filter events that triggers an archive on slack
   *
   * Conditions (Cond):
   * 1.) Parent edge of add set includes a timestamp in EdgeData
   * 2.) Source node of edge must exist in on the corresponding mapping level
   *
   * Procedure:
   * 1.) Filter all parent edges that include a timestamp in EdgeData (Cond 1)
   * 2.) Filter messages and threads using message mapping (Cond 2)
   * 3.) If it is neir a thread nor a message, filter group or channel using the channel mapping (Cond 2)
   */
  private def filterArchiveEvents(graphChanges: GraphChanges)(implicit persistenceAdapter: PersistenceAdapter): Task[Set[Archive]] = {

    def messageLevel(edge: Edge) = persistenceAdapter.getMessageMappingByWustId(edge.sourceId).map {
      case Some(m) if !m.is_deleted => m.slack_thread_ts match {
        case Some(_) => Some(ArchiveThread(m))
        case _        => Some(ArchiveMessage(m))
      }
        case _                     => None
    }

    def channelLevel(edge: Edge) = persistenceAdapter.getChannelMappingByWustId(edge.sourceId).map {
      case Some(c) if !c.is_archived =>
        if(c.is_private)  Some(ArchiveGroup(c))
        else              Some(ArchiveChannel(c))
      case _       => None
    }

    def edges(graphChanges: GraphChanges) = {
      graphChanges.addEdges.collect {
        case e @ Edge.Parent(_, EdgeData.Parent(Some(_)), _) => e
      }
    }

    filterWrapper[Edge.Parent, Archive](graphChanges)(edges)(messageLevel)(channelLevel)

  }

  def archiveMessage(archives: Set[Archive])(implicit persistenceAdapter: PersistenceAdapter): Set[ArchiveMessage] = archives.collect { case d @ ArchiveMessage(_) => d }
  def archiveThread(archives: Set[Archive])(implicit persistenceAdapter: PersistenceAdapter):  Set[ArchiveThread]  = archives.collect { case d @ ArchiveThread(_) => d }
  def archiveGroup(archives: Set[Archive])(implicit persistenceAdapter: PersistenceAdapter):   Set[ArchiveGroup]   = archives.collect { case d @ ArchiveGroup(_) => d }
  def archiveChannel(archives: Set[Archive])(implicit persistenceAdapter: PersistenceAdapter): Set[ArchiveChannel] = archives.collect { case d @ ArchiveChannel(_) => d }

  /**
   * Filter events that triggers a delete on slack
   *
   * Conditions (Cond):
   * 1.) Parent edge of the delete set
   * 2.) Source node of edge must exist in on the corresponding mapping level
   *
   * Procedure:
   * 1.) Filter all parent edges of the delete set (Cond 1)
   * 2.) Filter messages and threads using message mapping (Cond 2)
   * 3.) If it is neither a thread nor a message, filter group or channel using the channel mapping (Cond 2)
   */
  private def filterDeleteEvents(graphChanges: GraphChanges)(implicit persistenceAdapter: PersistenceAdapter): Task[Set[Delete]] = {

    def edges(graphChanges: GraphChanges) = {
      graphChanges.delEdges.collect {
        case e @ Edge.Parent(_, _, _) => e
      }
    }

    def messageLevel(edge: Edge) = persistenceAdapter.getMessageMappingByWustId(edge.sourceId).map {
      case Some(m) if !m.is_deleted => m.slack_thread_ts match {
        case Some(_) => Some(DeleteThread(m))
        case _        => Some(DeleteMessage(m))
      }
        case _       => None
    }

    def channelLevel(edge: Edge) = persistenceAdapter.getChannelMappingByWustId(edge.sourceId).map {
      case Some(c) if !c.is_archived =>
        if(c.is_private)  Some(DeleteGroup(c))
        else              Some(DeleteChannel(c))
      case _       => None
    }

    filterWrapper[Edge.Parent, Delete](graphChanges)(edges)(messageLevel)(channelLevel)

  }

  def archiveMessage(archives: Set[Delete])(implicit persistenceAdapter: PersistenceAdapter): Set[DeleteMessage] = archives.collect { case d @ DeleteMessage(_) => d }
  def archiveThread(archives: Set[Delete])(implicit persistenceAdapter: PersistenceAdapter):  Set[DeleteThread]  = archives.collect { case d @ DeleteThread(_) => d }
  def archiveGroup(archives: Set[Delete])(implicit persistenceAdapter: PersistenceAdapter):   Set[DeleteGroup]   = archives.collect { case d @ DeleteGroup(_) => d }
  def archiveChannel(archives: Set[Delete])(implicit persistenceAdapter: PersistenceAdapter): Set[DeleteChannel] = archives.collect { case d @ DeleteChannel(_) => d }

  /**
   * Filter events that triggers a unarchive on slack
   *
   * Conditions (Cond):
   * 1.) Parent edge in add set without a timestamp in EdgeData
   * 2.) Source node of edge must exist in on the corresponding mapping level
   * 3.) Mapping must include a archived flag (set to true)
   *
   * Procedure:
   * 1.) Filter all parent edges that do not include a timestamp in EdgeData (Cond 1)
   * 2.) Filter messages and threads using message mapping (Cond 2, 3)
   * 3.) If it is neither a thread nor a message, filter group or channel using the channel mapping (Cond 2, 3)
   */
  private def filterUnArchiveEvents(gc: GraphChanges)(implicit persistenceAdapter: PersistenceAdapter) = {

    def edges(graphChanges: GraphChanges) = {
      graphChanges.addEdges.collect {
        case e @ Edge.Parent(_, EdgeData.Parent(None), _) => e
      }
    }

    def messageLevel(edge: Edge) = persistenceAdapter.getMessageMappingByWustId(edge.sourceId).map {
      case Some(m) if m.is_deleted => m.slack_thread_ts match {
        case Some(_) => Some(UnArchiveThread(m))
        case _        => Some(UnArchiveMessage(m))
      }
        case _       => None
    }

    def channelLevel(edge: Edge) = persistenceAdapter.getChannelMappingByWustId(edge.sourceId).map {
      case Some(c) if c.is_archived =>
        if(c.is_private) Some(UnArchiveGroup(c))
        else Some(UnArchiveChannel(c))
      case _       => None
    }

    filterWrapper[Edge.Parent, UnArchive](gc)(edges)(messageLevel)(channelLevel)
  }

  def unDeleteMessage(unArchives: Set[UnArchive]): Set[UnArchiveMessage] = unArchives.collect { case d @ UnArchiveMessage(_) => d }
  def unDeleteThread(unArchives: Set[UnArchive]):  Set[UnArchiveThread]  = unArchives.collect { case d @ UnArchiveThread(_) => d }
  def unDeleteGroup(unArchives: Set[UnArchive]):   Set[UnArchiveGroup]   = unArchives.collect { case d @ UnArchiveGroup(_) => d }
  def unDeleteChannel(unArchives: Set[UnArchive]): Set[UnArchiveChannel] = unArchives.collect { case d @ UnArchiveChannel(_) => d }


  /**
    * Filter events that triggers a create on slack
    *
    * Conditions (Cond):
    * 1) Parent edge in add set without a timestamp in EdgeData
    * 2) Target node id of edge must exist in on the corresponding parent mapping level as id
    * 2.1) Message => target id in channel mapping
    * 2.2) Thread => target id in message mapping
    * 2.3) Channel => target id in team mapping
    * 3) Source node id of edge must NOT exist in on the corresponding mapping level as id
    * 3.1) Message => source id in message mapping
    * 3.2) Thread => source id in message mapping
    * 3.3) Channel => source id in channel mapping
    * 4) Source node id of edge must be contained in graph change or known in wust core (API call)
    *
    * Procedure:
    * 1.) Filter all parent edges that do not include a timestamp in EdgeData (Cond 1)
    * 2.) Filter messages and threads in the channel mapping using the target node id (Cond 2)
    * 3.) If it is neither a thread nor a message, filter group or channel in the workspace mapping using the target node id (Cond 2)
    * 4.)
    * 3.1) For this all channel mappings should be outer joined on workspace_mappings
    * 4.)
    */

















  //  def filterUndeleteEvents(gc: GraphChanges) = {
  //    if(gc.addNodes.collect{case n @ Node.Content(_,_,_) => n}.isEmpty){
  //      gc.addEdges.collect { case e @ Edge.Parent(_, EdgeData.Parent(None), _) => e}
  //    } else
  //        Set.empty[Edge]
  //  }

  def filterCreateThreadEvents(gc: GraphChanges) = {
    Future.sequence(for {
      node <- gc.addNodes
      edge <- gc.addEdges.filter {
        case Edge.Parent(childId, EdgeData.Parent(None), _) => if(childId == node.id) true else false
        case _                                                     => false
      }
  } yield {
    persistenceAdapter.getMessageMappingByWustId(edge.targetId).map(b =>
        if(b.isDefined) {
          scribe.info(s"detected create thread event: ($node, $edge)")
          Some((node, edge))
        } else {
          None
        }
        )
  }).map(_.flatten)
  }

  def filterCreateMessageEvents(gc: GraphChanges) = {
    Future.sequence(for {
      node <- gc.addNodes
      edge <- gc.addEdges.filter {
        case Edge.Parent(childId, EdgeData.Parent(None), _) => if(childId == node.id) true else false
        case _                                                     => false
      }
  } yield {
    persistenceAdapter.getSlackChannelByWustId(edge.targetId).map(b =>
        if(b.nonEmpty) {
          scribe.info(s"detected create message event: ($node, $edge)")
          Some((node, edge))
        } else {
          None
        }
        )
  }).map(_.flatten)
  }

  def filterCreateChannelEvents(gc: GraphChanges) = {
    // Nodes with matching edge whose parent is the workspace node
    Future.sequence(for {
      node <- gc.addNodes
      edge <- gc.addEdges.filter {
        case Edge.Parent(childId, EdgeData.Parent(None), _) =>
          if(childId == node.id) true else false
        case _ => false
      }
      } yield {
        persistenceAdapter.teamExistsByWustId(edge.targetId).map(b =>
            if(b) {
              scribe.info(s"detected create channel event: ($node, $edge)")
              Some((node, edge))
            } else {
              None
            }
            )
      }).map(_.flatten)
  }

  // Assume not aggregated GraphChanges
  def filterUpdateEvents(gc: GraphChanges) = {
    // Only node with no matching edge
    val edges = (gc.addEdges ++ gc.delEdges).filter {
      case Edge.Parent(_, _, _) => true
      case _ => false
    }

    val nodes = gc.addNodes.filter {
      case n: Node.Content => true
      case _ => false
    }

    if(edges.isEmpty){
      scribe.info(s"detected update message or rename channel event: ($nodes)")
      nodes
    } else {
      Set.empty[Node]
    }

  }

}

case class WustEventMapper(slackAppToken: String, persistenceAdapter: PersistenceAdapter)(
    implicit system: ActorSystem, scheduler: Scheduler, ec: ExecutionContext
  ) {

  def getAuthorClient(userId: UserId) = {

    val slackEventUser = persistenceAdapter.getSlackUserDataByWustId(userId)

    slackEventUser.onComplete {
      case Success(_) => scribe.info(s"Successfully got event user")
      case Failure(ex)   => scribe.error("Error getting user: ", ex)
    }

    val slackUserToken: Future[String] = slackEventUser.map({
      case Some(u) =>
        scribe.info(s"using SlackUser token")
        u.slackUserToken
      case _       =>
        scribe.info(s"using SlackApp token")
         None
    }).map(_.getOrElse(slackAppToken))

    slackUserToken.foreach { _ =>
    }

    for {
      isUser <- slackUserToken.map(_ != slackAppToken)
      token <- slackUserToken
    } yield SlackClient(token, isUser)
  }

  def computeMapping(userId: UserId, gc: GraphChanges) = {
    /**************/
    /* Meta stuff */
    /**************/

  val eventSlackClient = getAuthorClient(userId)

    /*****************************/
    /* Delete channel or message */
    /*****************************/

    case class SlackDeleteMessage(channelId: SlackChannelId, ts: SlackTimestamp, asUser: Option[Boolean] = None) extends GraphChangeEvent
    case class SlackDeleteChannel(channelId: SlackChannelId) extends GraphChangeEvent

    def generateSlackDeleteChannel(persistenceAdapter: PersistenceAdapter, nodeId: NodeId) = {
      for {
        c <- OptionT[Future, SlackChannelId](persistenceAdapter.getSlackChannelByWustId(nodeId))
      } yield {
        SlackDeleteChannel(c)
      }
    }

    def generateSlackDeleteMessage(persistenceAdapter: PersistenceAdapter, nodeId: NodeId, parentId: NodeId) = {

      val deletes: OptionT[Future, Option[SlackDeleteMessage]] = for {
        slackMessage <- OptionT[Future, Message_Mapping](persistenceAdapter.getMessageMappingByWustId(nodeId))
//        slackChannel <- OptionT[Future, String](persistenceAdapter.getSlackChannelId(parentId))
      } yield {
        if(slackMessage.slack_channel_id.isDefined && slackMessage.slack_message_ts.isDefined) {
          Some(SlackDeleteMessage(slackMessage.slack_channel_id.get, slackMessage.slack_message_ts.get))
        } else {
          scribe.error(s"Can not delete message with insufficient data (missing slack ids)")
          None
        }
      }

//      deletes.filter{_.isDefined}.map(_.get)
      deletes.collect{case Some(d) => d}
    }

    def applyDeleteChannel(persistenceAdapter: PersistenceAdapter, client: SlackClient, message: SlackDeleteChannel) = {
       //TODO: delete channel - not possible during time of writing
      persistenceAdapter.deleteChannelBySlackId(message.channelId).flatMap(_ =>
        client.apiClient.archiveChannel(message.channelId)
      )
    }

    def applyDeleteMessage(persistenceAdapter: PersistenceAdapter, client: SlackClient, message: SlackDeleteMessage) = {
      persistenceAdapter.deleteMessageBySlackIdData(message.channelId, message.ts).flatMap(_ =>
        client.apiClient.deleteChat(message.channelId, message.ts, Some(client.isUser))
      )
    }

    def deleteEvents(persistenceAdapter: PersistenceAdapter, client: SlackClient) = Future.sequence(filterDeleteEvents(gc).map { e =>

      persistenceAdapter.teamExistsByWustId(e.targetId).flatMap(b =>
        if(b) {
          generateSlackDeleteChannel(persistenceAdapter, e.sourceId).value.flatMap {
            case Some(c) => applyDeleteChannel(persistenceAdapter, client, c)
            case _ => Future.successful(false)
          }
          //        } else if(e.targetId == ) {
          // TODO: Threads
          //          ???
        } else {
          generateSlackDeleteMessage(persistenceAdapter, e.sourceId, e.targetId).value.flatMap {
            case Some(m) => applyDeleteMessage(persistenceAdapter, client, m)
            case _ => Future.successful(false)
          }
        }
      )
    })

    eventSlackClient.flatMap(client => deleteEvents(persistenceAdapter, client))
      .onComplete {
        case Success(deleteChanges) => scribe.info(s"Successfully applied delete events: $deleteChanges")
        case Failure(ex)            => scribe.error("Could not apply delete events: ", ex)
      }


    /********************/
    /* Undelete channel */
    /********************/

    case class SlackUnarchiveChannel(channelId: SlackChannelId) extends GraphChangeEvent

    def generateSlackUnarchiveChannel(persistenceAdapter: PersistenceAdapter, nodeId: NodeId) = {
      for {
        c <- OptionT[Future, SlackChannelId](persistenceAdapter.getSlackChannelByWustId(nodeId))
      } yield {
        SlackUnarchiveChannel(c)
      }
    }

    def applyUnarchiveChannel(persistenceAdapter: PersistenceAdapter, client: SlackClient, channel: SlackUnarchiveChannel) = {
      //TODO: delete channel - not possible during time of writing
      persistenceAdapter.unDeleteChannelBySlackId(channel.channelId).flatMap(_ =>
        client.apiClient.unarchiveChannel(channel.channelId)
      )
    }

    def unArchiveEvents(persistenceAdapter: PersistenceAdapter, client: SlackClient) = Future.sequence(filterUndeleteEvents(gc).map( e =>
      persistenceAdapter.teamExistsByWustId(e.targetId).flatMap(b =>
        if(b) {
          generateSlackUnarchiveChannel(persistenceAdapter, e.sourceId).value.flatMap {
            case Some(c) => applyUnarchiveChannel(persistenceAdapter, client, c)
            case _       => Future.successful(false)
          }
        } else {
          Future.successful(false)
        }
      )
    ))

    eventSlackClient.flatMap(client => unArchiveEvents(persistenceAdapter, client))
      .onComplete {
        case Success(unArchiveChanges) =>
          if(unArchiveChanges.forall(_ == true))
            scribe.info(s"Successfully applied unarchive events: $unArchiveChanges")
          else
            scribe.info(s"Some events were not successfully executed: $unArchiveChanges")
        case Failure(ex)            => scribe.error("Could not apply unarchive events: ", ex)
      }

    /***************/
    /* Add channel */
    /***************/
    case class SlackCreateChannel(channelName: String, teamNode: NodeId) extends GraphChangeEvent

    def generateSlackCreateChannel(persistenceAdapter: PersistenceAdapter, node: Node, edge: Edge) = {
      val channelMapping = Channel_Mapping(None, node.str, is_archived = false, node.id, edge.targetId)
      for{
        true <- persistenceAdapter.storeOrUpdateChannelMapping(channelMapping)
      } yield {
        SlackCreateChannel(node.str, edge.targetId)
      }
    }

    // TODO: name normalization
    def applyCreateChannel(persistenceAdapter: PersistenceAdapter, client: SlackClient, channel: SlackCreateChannel, wustNode: Node) = {
      for {
        t <- (wustNode.meta.accessLevel match {
          case NodeAccess.Restricted => // Create Group (private)
            client.apiClient.createGroup(channel.channelName).map(g => g.id -> g.name)
          case _ => // Create Channel (public)
            client.apiClient.createChannel(channel.channelName).map(c => c.id -> c.name)
        }).map(c => Channel_Mapping(Some(c._1), c._2, is_archived = false, wustNode.id, channel.teamNode))
        true <- persistenceAdapter.updateChannelMapping(t)
      } yield {
        true
      }
    }

    def createChannelEvents(persistenceAdapter: PersistenceAdapter, client: SlackClient) = filterCreateChannelEvents(gc).flatMap(f => Future.sequence(f.map { t =>
      val node = t._1
      val edge = t._2
      generateSlackCreateChannel(persistenceAdapter, node, edge).flatMap(c =>
        applyCreateChannel(persistenceAdapter, client, c, node)
      )
    }))

    eventSlackClient.flatMap(client => createChannelEvents(persistenceAdapter, client))
      .onComplete {
        case Success(createChanges) => scribe.info(s"Successfully created channel: $createChanges")
        case Failure(ex)            => scribe.error("Could not apply create channel: ", ex)
      }


    /***************/
    /* Add message */
    /***************/
    case class SlackCreateMessage(channelId: SlackChannelId, text: String, username: Option[String] = None, asUser: Option[Boolean] = None,
      parse: Option[String] = None, linkNames: Option[String] = None, attachments: Option[Seq[Attachment]] = None,
      unfurlLinks: Option[Boolean] = None, unfurlMedia: Option[Boolean] = None, iconUrl: Option[String] = None,
      iconEmoji: Option[String] = None, replaceOriginal: Option[Boolean] = None,
      deleteOriginal: Option[Boolean] = None, threadTs: Option[String] = None, channelNode: NodeId) extends GraphChangeEvent

    def generateSlackCreateMessage(persistenceAdapter: PersistenceAdapter, node: Node, edge: Edge) = {

      val channelNodeId = edge.targetId
      val messageMapping = Message_Mapping(None, None, None, slack_deleted_flag = false, node.str, node.id, channelNodeId)
      for {
        true <- OptionT[Future, Boolean](persistenceAdapter.storeOrUpdateMessageMapping(messageMapping).map(Some(_)))
        slackChannelId <- OptionT[Future, SlackChannelId](persistenceAdapter.getSlackChannelByWustId(channelNodeId))
      } yield SlackCreateMessage(channelId = slackChannelId, text = node.str, channelNode = channelNodeId)
    }

    def applyCreateMessage(persistenceAdapter: PersistenceAdapter, client: SlackClient, message: SlackCreateMessage, wustId: NodeId, retryNumber: Int = 0): Future[Boolean] = {

        val f = for {
          m <- client.apiClient.postChatMessage(channelId = message.channelId, text = message.text, asUser = Some(client.isUser)).map(ts =>
            Message_Mapping(Some(message.channelId), Some(ts), message.threadTs, slack_deleted_flag = false, message.text, wustId, message.channelNode))
          true <- persistenceAdapter.updateMessageMapping(m)
        } yield {
          true
        }
      f.recoverWith {
        case ApiError(e) if e == "not_in_channel" && retryNumber < 1 =>
          def a = client.apiClient.joinChannel(message.channelId).flatMap(_ => applyCreateMessage(persistenceAdapter, client, message, wustId, 1): Future[Boolean])
          a
        case e: Throwable                                            => Future.failed(e)
      }

    }
    // Nodes with matching edge whose parent is not the workspace node
    def createMessageEvents(persistenceAdapter: PersistenceAdapter, client: SlackClient) = {
      val res = filterCreateMessageEvents(gc).flatMap(f => Future.sequence(f.map { t =>
        val node = t._1
        val edge = t._2

        (for {
          m <- generateSlackCreateMessage(persistenceAdapter, node, edge)
          true <- OptionT[Future, Boolean](applyCreateMessage(persistenceAdapter, client, m, node.id).map(Some(_)))
        } yield {
          true
        }).value
      }))

      res.map(_.flatten)
    }

    eventSlackClient.flatMap(client => createMessageEvents(persistenceAdapter, client))
      .onComplete {
        case Success(createChanges) => scribe.info(s"Successfully created message: $createChanges")
        case Failure(ex)            => scribe.error("Could not apply create message: ", ex)
      }


    /*****************************/
    /* Update channel or message */
    /*****************************/

    case class SlackRenameChannel(channelId: SlackChannelId, channelName: String) extends GraphChangeEvent
    case class SlackUpdateMessage(channelId: SlackChannelId, ts: SlackTimestamp, text: String, asUser: Option[Boolean] = None) extends GraphChangeEvent

    def generateSlackRenameChannel(persistenceAdapter: PersistenceAdapter, channelNode: Node) = {
      for {
        channelMapping <- OptionT[Future, Channel_Mapping](persistenceAdapter.getChannelMappingByWustId(channelNode.id))
        slackChannelId = channelMapping.slack_channel_id
        true <- OptionT[Future, Boolean](persistenceAdapter.updateChannelMapping(Channel_Mapping(slackChannelId, channelNode.str, is_archived = false, channelNode.id, channelMapping.team_wust_id)).map(Some(_)))
      } yield SlackRenameChannel(slackChannelId.getOrElse(""), channelNode.str)

    }

    def applyRenameChannel(persistenceAdapter: PersistenceAdapter, client: SlackClient, channel: SlackRenameChannel) = {
        client.apiClient.renameChannel(channel.channelId, channel.channelName)
    }

    def generateSlackUpdateMessage(persistenceAdapter: PersistenceAdapter, messageNode: Node) = {
      val updates: OptionT[Future, Option[SlackUpdateMessage]] = for {
        m <- OptionT[Future, Message_Mapping](persistenceAdapter.getMessageMappingByWustId(messageNode.id))
        true <- OptionT[Future, Boolean](persistenceAdapter.updateMessageMapping(m.copy(slack_message_text = messageNode.str)).map(Some(_)))
      } yield {
        if(m.slack_message_ts.isDefined && m.slack_channel_id.isDefined)
          Some(SlackUpdateMessage(m.slack_channel_id.get, m.slack_message_ts.get, messageNode.str))
        else
          None
      }

      updates.collect{case Some(c) => c}
    }

    def applyUpdateMessage(persistenceAdapter: PersistenceAdapter, client: SlackClient, message: SlackUpdateMessage) = {
      client.apiClient.updateChatMessage(message.channelId, message.ts, message.text, Some(client.isUser)).map(_ => true)
    }

    def updateEvents(persistenceAdapter: PersistenceAdapter, client: SlackClient) = {
      def res = filterUpdateEvents(gc).map { node =>
        def update = persistenceAdapter.getSlackChannelByWustId(node.id).map {
          case Some(_) => // slack channel
            generateSlackRenameChannel(persistenceAdapter, node).map { c =>
              applyRenameChannel(persistenceAdapter, client, c)
            }

          case None => // slack message
          generateSlackUpdateMessage(persistenceAdapter, node).map { m =>
            applyUpdateMessage(persistenceAdapter, client, m)
          }

        }

        update.flatMap(_.value).flatMap {
          case Some(f) => f.map(Some(_))
          case None => Future.successful(None)
        }
      }

      Future.sequence(res).map(_.flatten)
    }

    eventSlackClient.flatMap(client => updateEvents(persistenceAdapter, client))
      .onComplete {
        case Success(updateChanges) => scribe.info(s"Successfully renamed channel or updated message: $updateChanges")
        case Failure(ex)            => scribe.error("Could not rename channel or update message: ", ex)
      }

  }
}
