package wust.slack

import akka.actor.ActorSystem
import cats.data.OptionT
import slack.api.SlackApiClient
import slack.models.MessageSubtypes.ChannelNameMessage
import wust.api.Authentication
import wust.graph.{GraphChanges, Node, NodeMeta}
import wust.ids._
import wust.sdk.EventToGraphChangeMapper
import wust.sdk.EventToGraphChangeMapper.CreationResult
import wust.slack.Data._
import slack.models._

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class ComposingResult(nodeId: NodeId, gc: GraphChanges, user: WustUserData, parentId: NodeId)
case class GCResult(gc: GraphChanges, user: WustUserData)

object EventComposer {

  import cats.implicits._

  private def toEpochMilli(str: String) = EpochMilli((str.toDouble * 1000).toLong)

  private def getOrCreateWustUser(slackUser: SlackUserId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistor: PersistenceAdapter, wustReceiver: WustReceiver): Future[Option[WustUserData]] = {

    val existingUser = persistor.getWustUserBySlackUserId(slackUser)
    val user: Future[Option[WustUserData]] = existingUser.flatMap {
      case Some(u) => Future.successful(Some(u))
      case None =>
        // 1. Create new implicit user
        // 2. Store user mapping

        val wustClient = wustReceiver.client

        val wustUser = OptionT[Future, Authentication.Verified](wustClient.auth.createImplicitUserForApp()).map( implicitUser =>
          WustUserData(implicitUser.user.id, implicitUser.token)
        ).value

        wustUser.onComplete {
          case Failure(ex) =>
            scribe.error("Error creating user mapping during creation of a wust user: ", ex)
          case Success(userOpt) => userOpt.foreach( newUser =>
            persistor.storeOrUpdateUserAuthData(User_Mapping(slackUser, newUser.wustUserId, None, newUser.wustUserToken))
          )
        }

        wustUser
    }

    user.onComplete {
      case Success(userOpt) => if(userOpt.isEmpty) scribe.error("Could not get or create user")
      case Failure(userOpt) => scribe.error(s"Could not communicate with backend to get or create user $userOpt")
    }

    user
  }

  private def getOrCreateChannelNode(channel: SlackChannelId, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistor: PersistenceAdapter, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]] = {
    val existingChannelNode = persistor.getChannelNodeBySlackId(channel)
    existingChannelNode.flatMap {
      case Some(c) => Future.successful(Some(c))
      case None =>
        // TODO:
        // 1. Get channel name by id (Slack API call)
        // 2. Create with NodeData = name (Wust API call)
        // 3. Create channel mapping (DB)

        val channelName = slackClient.getChannelInfo(channel).map(ci => ci.name)
        val newChannelNode = channelName.map(name =>
          Node.Content(NodeId.fresh, NodeData.PlainText(name), NodeRole.Message, NodeMeta(NodeAccess.ReadWrite))
        )

        newChannelNode.onComplete {
          case Success(node) =>
            persistor.getTeamNodeBySlackId(teamId).foreach {
              case Some(teamNodeId) =>
                wustReceiver.push(List(GraphChanges.addNodeWithParent(node, teamNodeId)), None).onComplete {
                  case Failure(ex) => scribe.error("Could not create channel node in wust: ", ex)
                  case _ =>
                }
                persistor.storeOrUpdateChannelMapping(Channel_Mapping(Some(channel), node.str, slack_deleted_flag = false, node.id, teamNodeId)).onComplete {
                  case Failure(ex) => scribe.error("Could not create channel mapping in slack app: ", ex)
                  case _ =>
                }
              case _            => Future.successful(None)
            }
          case Failure(ex) =>
            scribe.error("Could not request channel info: ", ex)
        }

        newChannelNode.map(n => Some(n.id))
    }
  }

  def createMessage(createdMessage: Message, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver, slackClient: SlackApiClient): OptionT[Future, ComposingResult] = for {
    wustUserData <- OptionT[Future, WustUserData](getOrCreateWustUser(createdMessage.user))
    wustChannelNodeId <- OptionT[Future, NodeId](getOrCreateChannelNode(createdMessage.channel, teamId))
  } yield {
    val changes: CreationResult = EventToGraphChangeMapper.createMessageInWust(
      NodeData.Markdown(createdMessage.text),
      wustUserData.wustUserId,
      toEpochMilli(createdMessage.ts),
      wustChannelNodeId
    )
    ComposingResult(changes.nodeId, changes.graphChanges, wustUserData, wustChannelNodeId)
  }


  def changeMessage(changedMessage: MessageChanged, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver, slackClient: SlackApiClient): OptionT[Future, ComposingResult] = {
    for {
      wustUserData <- OptionT[Future, WustUserData](getOrCreateWustUser(changedMessage.message.user))
      wustChannelNodeId <- OptionT[Future, NodeId](getOrCreateChannelNode(changedMessage.channel, teamId))
      changes <- OptionT[Future, CreationResult](persistenceAdapter.getMessageNodeBySlackIdData(changedMessage.channel, changedMessage.previous_message.ts).map {
        case Some(existingNodeId) =>
          Some(CreationResult(existingNodeId, EventToGraphChangeMapper.editMessageContentInWust(
            existingNodeId,
            NodeData.Markdown(changedMessage.message.text),
          )))
        case None                         =>
          Some(EventToGraphChangeMapper.createMessageInWust(
            NodeData.Markdown(changedMessage.message.text),
            wustUserData.wustUserId,
            toEpochMilli(changedMessage.previous_message.ts),
            wustChannelNodeId
          ))
        case n                            =>
          scribe.error(s"The node id does not corresponds to a content node: $n")
          None
      })
    } yield {
      ComposingResult(changes.nodeId, changes.graphChanges, wustUserData, wustChannelNodeId)
    }
  }

  def deleteMessage(deletedMessage: MessageDeleted)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter): OptionT[Future, GraphChanges] = {
    for {
      _ <- OptionT[Future, Boolean](persistenceAdapter.deleteMessageBySlackIdData(deletedMessage.channel, deletedMessage.previous_message.ts).map(Some(_)))
      nodeId <- OptionT[Future, NodeId](persistenceAdapter.getMessageNodeBySlackIdData(deletedMessage.channel, deletedMessage.previous_message.ts))
      wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNodeBySlackId(deletedMessage.channel))
    } yield {
      EventToGraphChangeMapper.deleteMessageInWust(
        nodeId,
        wustChannelNodeId
      )
    }
  }

  def createChannel(createdChannel: ChannelCreated, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Future, ComposingResult] = {
    for {
      wustUserData <- OptionT[Future, WustUserData](getOrCreateWustUser(createdChannel.channel.creator.get))
      teamNodeId <- OptionT[Future, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
    } yield {
      val changes: CreationResult = EventToGraphChangeMapper.createChannelInWust(
        NodeData.PlainText(createdChannel.channel.name),
        wustUserData.wustUserId,
        EpochMilli(createdChannel.channel.created),
        teamNodeId
      )
      ComposingResult(changes.nodeId, changes.graphChanges, wustUserData, teamNodeId)
    }
  }

  def renameChannel(messageWithSubtype: MessageWithSubtype, channelNameMessage: ChannelNameMessage, childId: NodeId, parentId: NodeId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Future, ComposingResult] = {
    for {
      wustUserData <- OptionT[Future, WustUserData](getOrCreateWustUser(messageWithSubtype.user))
    } yield {
      val changes: GraphChanges = EventToGraphChangeMapper.editChannelInWust(
        childId,
        NodeData.PlainText(channelNameMessage.name),
      )
      ComposingResult(childId, changes, wustUserData, parentId)
    }

  }

  def archiveChannel(archivedChannel: ChannelArchive, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Future, GCResult] = {
    for {
      wustUserData <- OptionT[Future, WustUserData](getOrCreateWustUser(archivedChannel.user))
      teamNodeId <- OptionT[Future, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
      wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNodeBySlackId(archivedChannel.channel))
      true <- OptionT[Future, Boolean](persistenceAdapter.deleteChannelBySlackId(archivedChannel.channel).map(Some(_)))
    } yield {
      val changes: GraphChanges = EventToGraphChangeMapper.archiveChannelInWust(
        wustChannelNodeId,
        teamNodeId,
        EpochMilli.now
      )
      GCResult(changes, wustUserData)
    }
  }

  def deleteChannel(deletedChannel: ChannelDeleted, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Future, GraphChanges] = {
    for {
      teamNodeId <- OptionT[Future, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
      wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNodeBySlackId(deletedChannel.channel))
      true <- OptionT[Future, Boolean](persistenceAdapter.deleteChannelBySlackId(deletedChannel.channel).map(Some(_)))
    } yield {
      EventToGraphChangeMapper.deleteChannelInWust(
        wustChannelNodeId,
        teamNodeId,
      )
    }
  }

  def unArchiveChannel(unarchivedChannel: ChannelUnarchive, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Future, GCResult] = {
    for {
      wustUserData <- OptionT[Future, WustUserData](getOrCreateWustUser(unarchivedChannel.user))
      teamNodeId <- OptionT[Future, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
      wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNodeBySlackId(unarchivedChannel.channel))
      true <- OptionT[Future, Boolean](persistenceAdapter.unDeleteChannelBySlackId(unarchivedChannel.channel).map(Some(_)))
    } yield {
      val changes = EventToGraphChangeMapper.unArchiveChannelInWust(
        wustChannelNodeId,
        teamNodeId,
      )
      GCResult(changes, wustUserData)
    }
  }

}
