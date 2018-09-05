package wust.slack

import akka.actor.ActorSystem
import cats.data.OptionT
import slack.models.MessageSubtypes.ChannelNameMessage
import wust.api.Authentication
import wust.graph.{GraphChanges, Node, NodeMeta}
import wust.ids.{EpochMilli, NodeAccess, NodeData, NodeId}
import wust.sdk.EventToGraphChangeMapper
import wust.sdk.EventToGraphChangeMapper.CreationResult
import wust.slack.Data._
import slack.models._
import monix.eval.Task

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class ComposingResult(nodeId: NodeId, gc: GraphChanges, user: WustUserData, parentId: NodeId)
case class GCResult(gc: GraphChanges, user: WustUserData)

object EventComposer {

  import cats.implicits._

  private def toEpochMilli(str: String) = EpochMilli((str.toDouble * 1000).toLong)

  private def getOrCreateWustUser(slackUser: SlackUserId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistor: PersistenceAdapter, wustReceiver: WustReceiver): Task[Option[WustUserData]] = {

    Task.eval {
      val existingUser = persistor.getWustUserBySlackUserId(slackUser)
      //    val user: Task[Option[WustUserData]] =
      existingUser.flatMap {
        case Some(u) => Task.pure(Some(u))
        case None    =>
          // 1. Create new implicit user
          // 2. Store user mapping

          val wustUser = OptionT(wustReceiver.client.auth.createImplicitUserForApp()).map(implicitUser =>
            WustUserData(implicitUser.user.id, implicitUser.token)
          ).value

          val storeUser = wustUser.flatMap {
            case Some(newUser) =>
              persistor.storeOrUpdateUserAuthData(User_Mapping(slackUser, newUser.wustUserId, None, newUser.wustUserToken))
            case None          => Task.pure(false)
          }

          for {
            u <- wustUser
            true <- storeUser
          } yield u

        //        wustUser.onComplete {
        //          case Failure(ex) =>
        //            scribe.error("Error creating user mapping during creation of a wust user: ", ex)
        //          case Success(userOpt) => userOpt.foreach( newUser =>
        //            persistor.storeOrUpdateUserAuthData(User_Mapping(slackUser, newUser.wustUserId, None, newUser.wustUserToken))
        //          )
        //        }

        //        wustUser
      }
    }

//    user.onComplete {
//      case Success(userOpt) => if(userOpt.isEmpty) scribe.error("Could not get or create user")
//      case Failure(userOpt) => scribe.error(s"Could not communicate with backend to get or create user $userOpt")
//    }

//    user
  }

  private def getOrCreateChannelNode(channel: SlackChannelId, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistor: PersistenceAdapter, wustReceiver: WustReceiver, slackClient: SlackClient): Task[Option[NodeId]] = {
    val existingChannelNode = persistor.getChannelNodeBySlackId(channel)
    existingChannelNode.flatMap {
      case Some(c) => Task.pure(Some(c))
      case None =>
        // TODO:
        // 1. Get channel name by id (Slack API call)
        // 2. Create with NodeData = name (Wust API call)
        // 3. Create channel mapping (DB)

        import SlackClient.TaskImplicits._
        val channelName = slackClient.apiClient.getChannelInfo(channel).map(ci => ci.name)
        val newChannelNode = channelName.map(name =>
          Node.Content(NodeId.fresh, NodeData.PlainText(name), NodeMeta(NodeAccess.ReadWrite))
        )

        newChannelNode.onComplete {
          case Success(node) =>
            persistor.getTeamNodeBySlackId(teamId).foreach {
              case Some(teamNodeId) =>
                wustReceiver.push(List(GraphChanges.addNodeWithParent(node, teamNodeId)), None).onComplete {
                  case Failure(ex) => scribe.error("Could not create channel node in wust: ", ex)
                  case _ =>
                }
                persistor.storeOrUpdateChannelMapping(Channel_Mapping(Some(channel), node.str, false, false, node.id, teamNodeId)).onComplete {
                  case Failure(ex) => scribe.error("Could not create channel mapping in slack app: ", ex)
                  case _ =>
                }
              case _            => Task.pure(None)
            }
          case Failure(ex) =>
            scribe.error("Could not request channel info: ", ex)
        }

        newChannelNode.map(n => Some(n.id))
    }
  }

  def createMessage(createdMessage: Message, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver, slackClient: SlackClient): OptionT[Task, ComposingResult] = for {
    wustUserData <- OptionT[Task, WustUserData](getOrCreateWustUser(createdMessage.user))
    wustChannelNodeId <- OptionT[Task, NodeId](getOrCreateChannelNode(createdMessage.channel, teamId))
  } yield {
    val changes: CreationResult = EventToGraphChangeMapper.createMessageInWust(
      NodeData.Markdown(createdMessage.text),
      wustUserData.wustUserId,
      toEpochMilli(createdMessage.ts),
      wustChannelNodeId
    )
    ComposingResult(changes.nodeId, changes.graphChanges, wustUserData, wustChannelNodeId)
  }


  def changeMessage(changedMessage: MessageChanged, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver, slackClient: SlackClient): OptionT[Task, ComposingResult] = {
    for {
      wustUserData <- OptionT[Task, WustUserData](getOrCreateWustUser(changedMessage.message.user))
      wustChannelNodeId <- OptionT[Task, NodeId](getOrCreateChannelNode(changedMessage.channel, teamId))
      changes <- OptionT[Task, CreationResult](persistenceAdapter.getMessageNodeBySlackIdData(changedMessage.channel, changedMessage.previous_message.ts).map {
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

  def deleteMessage(deletedMessage: MessageDeleted)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter): OptionT[Task, GraphChanges] = {
    for {
      _ <- OptionT[Task, Boolean](persistenceAdapter.deleteMessageBySlackIdData(deletedMessage.channel, deletedMessage.previous_message.ts).map(Some(_)))
      nodeId <- OptionT[Task, NodeId](persistenceAdapter.getMessageNodeBySlackIdData(deletedMessage.channel, deletedMessage.previous_message.ts))
      wustChannelNodeId <- OptionT[Task, NodeId](persistenceAdapter.getChannelNodeBySlackId(deletedMessage.channel))
    } yield {
      EventToGraphChangeMapper.deleteMessageInWust(
        nodeId,
        wustChannelNodeId
      )
    }
  }

  def createChannel(createdChannel: ChannelCreated, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Task, ComposingResult] = {
    for {
      wustUserData <- OptionT[Task, WustUserData](getOrCreateWustUser(createdChannel.channel.creator.get))
      teamNodeId <- OptionT[Task, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
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

  def renameChannel(messageWithSubtype: MessageWithSubtype, channelNameMessage: ChannelNameMessage, childId: NodeId, parentId: NodeId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Task, ComposingResult] = {
    for {
      wustUserData <- OptionT[Task, WustUserData](getOrCreateWustUser(messageWithSubtype.user))
    } yield {
      val changes: GraphChanges = EventToGraphChangeMapper.editChannelInWust(
        childId,
        NodeData.PlainText(channelNameMessage.name),
      )
      ComposingResult(childId, changes, wustUserData, parentId)
    }

  }

  def archiveChannel(archivedChannel: ChannelArchive, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Task, GCResult] = {
    for {
      wustUserData <- OptionT[Task, WustUserData](getOrCreateWustUser(archivedChannel.user))
      teamNodeId <- OptionT[Task, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
      wustChannelNodeId <- OptionT[Task, NodeId](persistenceAdapter.getChannelNodeBySlackId(archivedChannel.channel))
      true <- OptionT[Task, Boolean](persistenceAdapter.deleteChannelBySlackId(archivedChannel.channel).map(Some(_)))
    } yield {
      val changes: GraphChanges = EventToGraphChangeMapper.archiveChannelInWust(
        wustChannelNodeId,
        teamNodeId,
        EpochMilli.now
      )
      GCResult(changes, wustUserData)
    }
  }

  def deleteChannel(deletedChannel: ChannelDeleted, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Task, GraphChanges] = {
    for {
      teamNodeId <- OptionT[Task, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
      wustChannelNodeId <- OptionT[Task, NodeId](persistenceAdapter.getChannelNodeBySlackId(deletedChannel.channel))
      true <- OptionT[Task, Boolean](persistenceAdapter.deleteChannelBySlackId(deletedChannel.channel).map(Some(_)))
    } yield {
      EventToGraphChangeMapper.deleteChannelInWust(
        wustChannelNodeId,
        teamNodeId,
      )
    }
  }

  def unArchiveChannel(unarchivedChannel: ChannelUnarchive, teamId: SlackTeamId)(implicit ec: scala.concurrent.ExecutionContext, system: ActorSystem, persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver): OptionT[Task, GCResult] = {
    for {
      wustUserData <- OptionT[Task, WustUserData](getOrCreateWustUser(unarchivedChannel.user))
      teamNodeId <- OptionT[Task, NodeId](persistenceAdapter.getTeamNodeBySlackId(teamId))
      wustChannelNodeId <- OptionT[Task, NodeId](persistenceAdapter.getChannelNodeBySlackId(unarchivedChannel.channel))
      true <- OptionT[Task, Boolean](persistenceAdapter.unDeleteChannelBySlackId(unarchivedChannel.channel).map(Some(_)))
    } yield {
      val changes = EventToGraphChangeMapper.unArchiveChannelInWust(
        wustChannelNodeId,
        teamNodeId,
      )
      GCResult(changes, wustUserData)
    }
  }

}
