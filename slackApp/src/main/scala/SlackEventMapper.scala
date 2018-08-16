package wust.slack

import cats.data.{EitherT, OptionT}
import slack.api.SlackApiClient
import slack.models.MessageSubtypes._
import slack.models._
import wust.graph.GraphChanges
import wust.ids.{EpochMilli, NodeData, NodeId}
import wust.sdk.EventMapper
import wust.slack.Data.{Message_Mapping, Channel_Mapping, WustUserData}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class SlackEventMapper(persistenceAdapter: PersistenceAdapter, wustReceiver: WustReceiver, slackClient: SlackApiClient)(implicit ec: ExecutionContext) {
  import cats.implicits._

  def toEpochMilli(str: String) = EpochMilli((str.toDouble * 1000).toLong)

  def createMessage(createdMessage: Message): Future[Either[String, List[GraphChanges]]] = {

    // Use persistenceAdapter.getMessageNodeByContent(mesage.text) ?
    if(createdMessage.bot_id.isEmpty && (createdMessage.user != "USLACKBOT" || createdMessage.channel_type != "channel")) {
      val graphChanges: OptionT[Future, (NodeId, GraphChanges, WustUserData)] = for {
        wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(createdMessage.user, wustReceiver.client))
        wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getOrCreateChannelNode(createdMessage.channel, Constants.slackNode.id, wustReceiver, slackClient))
      } yield {
        val changes: (NodeId, GraphChanges) = EventMapper.createMessageInWust(
          NodeData.Markdown(createdMessage.text),
          wustUserData.wustUserId,
          toEpochMilli(createdMessage.ts),
          wustChannelNodeId
        )
        (changes._1, changes._2, wustUserData)
      }

      val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not create message").flatMapF { changes =>
        val res = wustReceiver.push(List(changes._2), Some(changes._3))
        res.foreach {
          case Right(_) => persistenceAdapter.storeMessageMapping(Message_Mapping(Some(createdMessage.channel), Some(createdMessage.ts), slack_deleted_flag = false, createdMessage.text, changes._1))
          case _        => scribe.error(s"Could not apply changes to wust: $changes")
        }
        res
      }

      applyChanges.value.onComplete {
        case Success(request) => scribe.info("Successfully created message")
        case Failure(ex)      => scribe.error("Error creating message: ", ex)
      }

      applyChanges.value

    } else {
      Future.successful(Right(List.empty[GraphChanges]))
    }

  }

  def changeMessage(changedMessage: MessageChanged): Future[Either[String, List[GraphChanges]]] = {

    persistenceAdapter.isSlackMessageUpToDate(changedMessage.channel, changedMessage.previous_message.ts, changedMessage.message.text).flatMap(upToDate =>
      if(!upToDate) {
        val graphChanges: OptionT[Future, (NodeId, GraphChanges, WustUserData)] = for {
          wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(changedMessage.message.user, wustReceiver.client))
          wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getOrCreateChannelNode(changedMessage.channel, Constants.slackNode.id, wustReceiver, slackClient))
          changes <- OptionT[Future, (NodeId, GraphChanges)](persistenceAdapter.getMessageNodeByChannelAndTimestamp(changedMessage.channel, changedMessage.previous_message.ts).map {
            case Some(existingNodeId: NodeId) =>
              Some((existingNodeId, EventMapper.editMessageContentInWust(
                existingNodeId,
                NodeData.Markdown(changedMessage.message.text),
              )))
            case None                             =>
              Some(EventMapper.createMessageInWust(
                NodeData.Markdown(changedMessage.message.text),
                wustUserData.wustUserId,
                toEpochMilli(changedMessage.previous_message.ts),
                wustChannelNodeId
              ))
            case n                                =>
              scribe.error(s"The node id does not corresponds to a content node: $n")
              None
          })
        } yield {
          (changes._1, changes._2, wustUserData)
        }

        val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not change message").flatMapF { changes =>
          val res = wustReceiver.push(List(changes._2), Some(changes._3))
          res.foreach {
            case Right(_) => persistenceAdapter.updateMessageMapping(Message_Mapping(Some(changedMessage.channel), Some(changedMessage.previous_message.ts), slack_deleted_flag = false, changedMessage.message.text, changes._1))
            case _        => scribe.error(s"Could not apply changes to wust: $changes")
          }
          res
        }

        applyChanges.value.onComplete {
          case Success(request) => scribe.info("Successfully changed message")
          case Failure(ex)      => scribe.error("Error changing message: ", ex)
        }

        applyChanges.value

      } else {
        Future.successful(Right(List.empty[GraphChanges]))
      }
    )
  }

  def deleteMessage(deletedMessage: MessageDeleted): Future[Either[String, List[GraphChanges]]] = {
    persistenceAdapter.isSlackMessageDeleted(deletedMessage.channel, deletedMessage.previous_message.ts).flatMap { deleted =>
      if(!deleted) {
        val graphChanges: OptionT[Future, GraphChanges] = for {
          _ <- OptionT[Future, Boolean](persistenceAdapter.deleteMessage(deletedMessage.channel, deletedMessage.previous_message.ts).map(Some(_)))
          nodeId <- OptionT[Future, NodeId](persistenceAdapter.getMessageNodeByChannelAndTimestamp(deletedMessage.channel, deletedMessage.previous_message.ts))
          wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNodeById(deletedMessage.channel))
        } yield {
          EventMapper.deleteMessageInWust(
            nodeId,
            wustChannelNodeId
          )
        }

        val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not delete message").flatMapF { changes =>
          wustReceiver.push(List(changes), None)
        }

        applyChanges.value.onComplete {
          case Success(change) => scribe.info(s"Deleted message: $change")
          case Failure(ex)      => scribe.error("Error deleting message: ", ex)
        }

        applyChanges.value
      } else {
        Future.successful(Right(List.empty[GraphChanges]))
      }
    }

  }

  def renameChannel(messageWithSubtype: MessageWithSubtype, channelNameMessage: ChannelNameMessage): Future[Either[String, List[GraphChanges]]] = {
    persistenceAdapter.isSlackChannelUpToDateElseGetNode(messageWithSubtype.channel, channelNameMessage.name).flatMap {
      case Some(nodeId) =>

        val graphChanges: OptionT[Future, (NodeId, GraphChanges, WustUserData)] = for {
          wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(messageWithSubtype.user, wustReceiver.client))
        } yield {
          val changes: GraphChanges = EventMapper.editChannelInWust(
            nodeId,
            NodeData.Markdown(channelNameMessage.name),
          )
          (nodeId, changes, wustUserData)
        }

        graphChanges.value.flatMap {
          case Some(gc) => persistenceAdapter.updateChannelMapping(Channel_Mapping(Some(messageWithSubtype.channel), channelNameMessage.name, slack_deleted_flag = false, nodeId))
          case None     => Future.successful(false)
        }.onComplete {
          case Success(_)  => scribe.info("Could not store channel mapping")
          case Failure(ex) => scribe.error("Could not create channel in channel mapping", ex)
        }

        val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not rename channel").flatMapF {
          changes =>
            val res = wustReceiver.push(List(changes._2), Some(changes._3))
            res.foreach {
              case Right(_) => scribe.info(s"Created renaming slack channel in wust: ${ channelNameMessage.name }")
              case _        => scribe.error(s"Could not apply changes to wust: $changes")
            }
            res
        }

        applyChanges.value.onComplete {
          case Success(res) => scribe.info(s"Renamed channel: $res")
          case Failure(ex)      => scribe.error("Error renaming channel: ", ex)
        }

        applyChanges.value

      case None =>
        scribe.info("Channel already up to date")
        Future.successful(Right(List.empty[GraphChanges]))
    }

  }

  def createChannel(createdChannel: ChannelCreated): Future[Either[String, List[GraphChanges]]] = {
    persistenceAdapter.getChannelNodeByName(createdChannel.channel.name).flatMap {
      case None =>

        val graphChanges: OptionT[Future, (NodeId, GraphChanges, WustUserData)] = for {
          wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(createdChannel.channel.creator.get, wustReceiver.client))
        } yield {
          val changes: (NodeId, GraphChanges) = EventMapper.createChannelInWust(
            NodeData.Markdown(createdChannel.channel.name),
            wustUserData.wustUserId,
            EpochMilli(createdChannel.channel.created),
            Constants.slackNode.id
          )
          (changes._1, changes._2, wustUserData)
        }

        graphChanges.value.flatMap {
          case Some(gc) => persistenceAdapter.storeChannelMapping(Channel_Mapping(Some(createdChannel.channel.id), createdChannel.channel.name, slack_deleted_flag = false, gc._1))
          case None     => Future.successful(false)
        }.onComplete {
          case Success(_)  => scribe.info("Created new channel mapping for channel")
          case Failure(ex) => scribe.error("Could not create channel in channel mapping", ex)
        }

        val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not create channel").flatMapF { changes =>
          val res = wustReceiver.push(List(changes._2), Some(changes._3))
          res.foreach {
            case Right(_) => scribe.info(s"Created new slack channel in wust: ${ createdChannel.channel.name }")
            case _        => scribe.error(s"Could not apply changes to wust: $changes")
          }
          res
        }

        applyChanges.value.onComplete {
          case Success(request) => scribe.info("Created new channel")
          case Failure(ex)      => scribe.error("Error creating channel: ", ex)
        }

        applyChanges.value

      case Some(channelId) =>
        Future.successful(Right(List.empty[GraphChanges]))

    }

  }


  def archiveChannel(archivedChannel: ChannelArchive): Future[Either[String, List[GraphChanges]]] = {
    persistenceAdapter.isSlackChannelDeleted(archivedChannel.channel).flatMap { deleted =>
      if(!deleted) {
        val graphChanges: OptionT[Future, GraphChanges] = for {
          wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNodeById(archivedChannel.channel))
          true <- OptionT[Future, Boolean](persistenceAdapter.deleteChannel(archivedChannel.channel).map(Some(_)))
        } yield {
          EventMapper.deleteChannelInWust(
            wustChannelNodeId,
            Constants.slackNode.id
          )
        }

        val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not archive channel").flatMapF { changes =>
          wustReceiver.push(List(changes), None)
        }

        applyChanges.value.onComplete {
          case Success(request) => scribe.info("Archieved channel")
          case Failure(ex)      => scribe.error("Error archiving channel: ", ex)
        }

        applyChanges.value
      } else {
        Future.successful(Right(List.empty[GraphChanges]))
      }
    }

  }

  // Currently same as delete, c&p
  def deleteChannel(deletedChannel: ChannelDeleted): Future[Either[String, List[GraphChanges]]] = {
    archiveChannel(ChannelArchive(deletedChannel.channel, "unknown")) //TODO: get user somehow if possible
  }

  def unarchiveChannel(unarchivedChannel: ChannelUnarchive): Future[Either[String, List[GraphChanges]]] = {
    persistenceAdapter.isSlackChannelDeleted(unarchivedChannel.channel).flatMap { deleted =>
      if(deleted) {
        val graphChanges: OptionT[Future, GraphChanges] = for {
          wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNodeById(unarchivedChannel.channel))
          true <- OptionT[Future, Boolean](persistenceAdapter.unDeleteChannel(unarchivedChannel.channel).map(Some(_)))
        } yield {
          EventMapper.unDeleteChannelInWust(
            wustChannelNodeId,
            Constants.slackNode.id
          )
        }

        val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not undelete channel").flatMapF { changes =>
          wustReceiver.push(List(changes), None)
        }

        applyChanges.value.onComplete {
          case Success(request) => scribe.info("Unarchieved channel")
          case Failure(ex)      => scribe.error("Error unarchiving channel: ", ex)
        }

        applyChanges.value
      } else {
        Future.successful(Right(List.empty[GraphChanges]))
      }
    }

  }

  def matchSlackEventStructureEvent(slackEventStructure: SlackEventStructure): Future[Either[String, List[GraphChanges]]] = slackEventStructure.event match {

    case e: Hello =>
      scribe.info("hello")
      Future.successful(Left("Not implemented"))

    case e: Message =>
      scribe.info(s"Event => message: ${ e.toString }")
      createMessage(e)

    case e: MessageChanged =>
      scribe.info(s"Event => message changed: ${ e.toString }")
      changeMessage(e)

    case e: MessageDeleted =>
      scribe.info(s"Event => message deleted: ${ e.toString }")
      deleteMessage(e)

    case e: BotMessage         =>
      scribe.info(s"Event => bot message: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: MessageWithSubtype =>
      scribe.info(s"Event => message with subtype: ${ e.toString }")

      e.messageSubType match {
        case channelMessage: ChannelNameMessage =>
          scribe.info("Event => channel name message")
          renameChannel(e, channelMessage)

        case fileShareMessage: FileShareMessage =>
          scribe.info("Event => file share message")
          Future.successful(Left("Not implemented"))

        case meMessage: MeMessage =>
          scribe.info("Event => me message")
          Future.successful(Left("Not implemented"))

        case s: UnhandledSubtype =>
          scribe.info(s"Event => message with unknown subtype: $s")
          Future.successful(Left("Not implemented"))

      }

    case e: ReactionAdded   =>
      scribe.info(s"Event => reaction added: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: ReactionRemoved =>
      scribe.info(s"Event => reaction removed: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: UserTyping =>
      scribe.info(s"Event => user typing: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: ChannelMarked  =>
      scribe.info(s"Event => channel marked: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: ChannelCreated =>
      scribe.info(s"Event => channel created: ${ e.toString }")
      createChannel(e)

    case e: ChannelJoined =>
      scribe.info(s"Event => channel joined: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: ChannelLeft   =>
      scribe.info(s"Event => channel left: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: ChannelDeleted =>
      scribe.info(s"Event => channel deleted: ${ e.toString }")
      deleteChannel(e)

    case e: ChannelRename =>
      scribe.info(s"Event => channel renamed: ${ e.toString }")
    // See ChannelNameMessage == MessageWithSubType => channel_name
    // Use created field from here in ChannelNameMessage?
      Future.successful(Left("Not implemented"))

    case e: ChannelArchive =>
      scribe.info(s"Event => channel archived: ${ e.toString }")
      archiveChannel(e)

    case e: ChannelUnarchive =>
      scribe.info(s"Event => channel unarchived: ${ e.toString }")
      unarchiveChannel(e)

    case e: ChannelHistoryChanged =>
      scribe.info(s"Event => channel history changed: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: ImCreated        =>
      scribe.info(s"Event => im: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: ImOpened         =>
      scribe.info(s"Event => im: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: ImClose          =>
      scribe.info(s"Event => im: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: ImMarked         =>
      scribe.info(s"Event => im: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: ImHistoryChanged =>
      scribe.info(s"Event => im: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: MpImJoined =>
      scribe.info(s"Event => mp Im: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: MpImOpen   =>
      scribe.info(s"Event => mp Im: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: MpImClose  =>
      scribe.info(s"Event => mp Im: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: GroupJoined         =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupLeft           =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupOpen           =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupClose          =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupArchive        =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupUnarchive      =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupRename         =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupMarked         =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: GroupHistoryChanged =>
      scribe.info(s"Event => group: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: FileCreated        =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FileShared         =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FileUnshared       =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FilePublic         =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FilePrivate        =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FileChange         =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FileDeleted        =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FileCommentAdded   =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FileCommentEdited  =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: FileCommentDeleted =>
      scribe.info(s"Event => file: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: PinAdded   =>
      scribe.info(s"Event => pin: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: PinRemoved =>
      scribe.info(s"Event => pin: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: PresenceChange       =>
      scribe.info(s"Event => presence: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: ManualPresenceChange =>
      scribe.info(s"Event => presence: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: PrefChange =>
      scribe.info(s"Event => pref: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: UserChange =>
      scribe.info(s"Event => user: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: TeamJoin =>
      scribe.info(s"Event => team: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: StarAdded   =>
      scribe.info(s"Event => star: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: StarRemoved =>
      scribe.info(s"Event => star: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: EmojiChanged =>
      scribe.info(s"Event => emoji: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: CommandsChanged =>
      scribe.info(s"Event => commands: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: TeamPlanChanged  =>
      scribe.info(s"Event => team: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: TeamPrefChanged  =>
      scribe.info(s"Event => team: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: TeamRename       =>
      scribe.info(s"Event => team: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: TeamDomainChange =>
      scribe.info(s"Event => team: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: BotAdded   =>
      scribe.info(s"Event => bot: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: BotChanged =>
      scribe.info(s"Event => bot: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: AccountsChanged =>
      scribe.info(s"Event => account: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: TeamMigrationStarted =>
      scribe.info(s"Event => team: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: ReconnectUrl =>
      scribe.info(s"Event => reconnect: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: Reply =>
      scribe.info(s"Event => reply: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: AppsChanged     =>
      scribe.info(s"Event => apps: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: AppsUninstalled =>
      scribe.info(s"Event => apps: ${ e.toString }")
      Future.successful(Left("Not implemented"))
    case e: AppsInstalled   =>
      scribe.info(s"Event => apps: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: DesktopNotification =>
      scribe.info(s"Event => desktop: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: DndUpdatedUser =>
      scribe.info(s"Event => dnd: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case e: MemberJoined =>
      scribe.info(s"Event => member: ${ e.toString }")
      Future.successful(Left("Not implemented"))

    case unknown =>
      scribe.info(s"unmatched SlackEvent: ${ unknown.toString }")
      Future.successful(Left("Not implemented"))
  }
}
