package wust.slack

import wust.graph.Node._
import wust.graph._
import wust.ids._
import wust.slack.Data._
import cats.data.OptionT
import cats.implicits._
import slack.api.{ApiError, SlackApiClient}
import slack.models._
import akka.actor.ActorSystem
import monix.execution.Scheduler

import scala.collection.Set
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait GraphChangeEvent
case class WustEventMapper(slackAppToken: String, persistenceAdapter: PersistenceAdapter)(
    implicit system: ActorSystem, scheduler: Scheduler, ec: ExecutionContext
  ) {

  def filterDeleteEvents(gc: GraphChanges) = {
    (gc.addEdges ++ gc.delEdges).filter {
      case Edge.Parent(_, EdgeData.Parent(Some(_)), _) => true
      case _ => false
    }
  }

  def filterCreateThreadEvent(gc: GraphChanges) = ???

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

  def getAuthorClient(userId: UserId) = {

    val slackEventUser = persistenceAdapter.getSlackUserByWustId(userId)

    slackEventUser.onComplete {
      case Success(_) => scribe.info(s"Successfully got event user")
      case Failure(ex)   => scribe.error("Error getting user: ", ex)
    }

    val slackUserToken: Future[String] = slackEventUser.map({
      case Some(u) => u.slackUserToken
      case _       => None
    }).map(_.getOrElse(slackAppToken))

    slackUserToken.foreach { _ =>
      scribe.info(s"using token of SlackApp")
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
        slackMessage <- OptionT[Future, Message_Mapping](persistenceAdapter.getSlackMessageByWustId(nodeId))
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
        client.apiClient.deleteChat(message.channelId, message.ts, client.isUser)
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




    /***************/
    /* Add channel */
    /***************/
    case class SlackCreateChannel(channelName: String, teamNode: NodeId) extends GraphChangeEvent

    def generateSlackCreateChannel(persistenceAdapter: PersistenceAdapter, node: Node, edge: Edge) = {
      val channelMapping = Channel_Mapping(None, node.str, slack_deleted_flag = false, node.id, edge.targetId)
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
        }).map(c => Channel_Mapping(Some(c._1), c._2, slack_deleted_flag = false, wustNode.id, channel.teamNode))
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
          m <- client.apiClient.postChatMessage(channelId = message.channelId, text = message.text, asUser = client.isUser).map(ts =>
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
        true <- OptionT[Future, Boolean](persistenceAdapter.updateChannelMapping(Channel_Mapping(slackChannelId, channelNode.str, slack_deleted_flag = false, channelNode.id, channelMapping.team_wust_id)).map(Some(_)))
      } yield SlackRenameChannel(slackChannelId.getOrElse(""), channelNode.str)

    }

    def applyRenameChannel(persistenceAdapter: PersistenceAdapter, client: SlackClient, channel: SlackRenameChannel) = {
        client.apiClient.renameChannel(channel.channelId, channel.channelName)
    }

    def generateSlackUpdateMessage(persistenceAdapter: PersistenceAdapter, messageNode: Node) = {
      val updates: OptionT[Future, Option[SlackUpdateMessage]] = for {
        m <- OptionT[Future, Message_Mapping](persistenceAdapter.getSlackMessageByWustId(messageNode.id))
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
      client.apiClient.updateChatMessage(message.channelId, message.ts, message.text, client.isUser).map(_ => true)
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
