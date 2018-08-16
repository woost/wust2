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

  def filterCreateMessageEvents(gc: GraphChanges) = {
    // Nodes with matching edge whose parent is not the workspace node
    for {
      node <- gc.addNodes
      edge <- gc.addEdges.filter {
        case Edge.Parent(childId, EdgeData.Parent(None), parentId) => if(childId == node.id && parentId != Constants.slackNode.id) true else false
        case _                                                     => false
      }
    } yield {
      scribe.info(s"detected create message event: ($node, $edge)")
      (node, edge)
    }
  }

  def filterCreateChannelEvents(gc: GraphChanges) = {
    // Nodes with matching edge whose parent is the workspace node
    for {
      node <- gc.addNodes
      edge <- gc.addEdges.filter {
        case Edge.Parent(childId, EdgeData.Parent(None), parentId) => if(childId == node.id && parentId == Constants.slackNode.id) true else false
        case _ => false
      }
    } yield {
      scribe.info(s"detected create channel event: ($node, $edge)")
      (node, edge)
    }
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

  def getAuthorClient(gc: GraphChanges) = {

    def getUserNode(nodes: Set[Node]) = {
      nodes.flatMap {
        case Node.User(id, _, _) => Some(id)
        case _ => None
      }.headOption
    }

    // TODO: Not possible to tell who created / deleted edges
    val authorUser = gc.addEdges.flatMap {
      case Edge.Author(userId, _, _) => Some(userId)
      case _                         => None
    }.headOption

    val wustEventUser = authorUser match {
      case Some(u) => Some(u)
      case _       => getUserNode(gc.addNodes)
    }

    val slackEventUser = wustEventUser match {
      case Some(u) => persistenceAdapter.getSlackUser(u)
      case _       => Future.successful(None)
    }

    slackEventUser.onComplete {
      case Success(user) => scribe.info(s"event user = $user")
      case Failure(ex)   => scribe.error("Error getting user: ", ex)
    }

    val slackUserToken: Future[String] = slackEventUser.map({
      case Some(u) => u.slackUserToken
      case _       => None
    }).map(_.getOrElse(slackAppToken))

    val isSlackAppUser = slackUserToken.map(t => if(t == slackAppToken) true else false)

    slackUserToken.foreach { _ =>
      scribe.info(s"using token of SlackApp = $isSlackAppUser")
    }

    for {
      isBot <- isSlackAppUser
      token <- slackUserToken
    } yield SlackClient(token, isBot)
  }

  def computeMapping(gc: GraphChanges) = {
    /**************/
    /* Meta stuff */
    /**************/

  val eventSlackClient = getAuthorClient(gc)

    /*****************************/
    /* Delete channel or message */
    /*****************************/

    case class SlackDeleteMessage(channelId: String, ts: String, asUser: Option[Boolean] = None) extends GraphChangeEvent
    case class SlackDeleteChannel(channelId: String) extends GraphChangeEvent

    def generateSlackDeleteChannel(persistenceAdapter: PersistenceAdapter, nodeId: NodeId) = {
      for {
        c <- OptionT[Future, String](persistenceAdapter.getSlackChannelId(nodeId))
      } yield {
        SlackDeleteChannel(c)
      }
    }

    def generateSlackDeleteMessage(persistenceAdapter: PersistenceAdapter, nodeId: NodeId, parentId: NodeId) = {

      val deletes: OptionT[Future, Option[SlackDeleteMessage]] = for {
        slackMessage <- OptionT[Future, Message_Mapping](persistenceAdapter.getSlackMessage(nodeId))
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
      persistenceAdapter.deleteChannel(message.channelId).flatMap(_ =>
        client.apiClient.archiveChannel(message.channelId)
      )
    }

    def applyDeleteMessage(persistenceAdapter: PersistenceAdapter, client: SlackClient, message: SlackDeleteMessage) = {
      persistenceAdapter.deleteMessage(message.channelId, message.ts).flatMap(_ =>
        client.apiClient.deleteChat(message.channelId, message.ts, client.isUser)
      )
    }

    def deleteEvents(persistenceAdapter: PersistenceAdapter, client: SlackClient) = Future.sequence(filterDeleteEvents(gc).map { e =>

      if(e.targetId == Constants.slackNode.id) {
        generateSlackDeleteChannel(persistenceAdapter, e.sourceId).value.flatMap {
          case Some(c) => applyDeleteChannel(persistenceAdapter, client, c)
          case _ => Future.successful(false)
        }
      } else {
        generateSlackDeleteMessage(persistenceAdapter, e.sourceId, e.targetId).value.flatMap {
          case Some(m) => applyDeleteMessage(persistenceAdapter, client, m)
          case _ => Future.successful(false)
        }
      }

    })

    eventSlackClient.flatMap(client => deleteEvents(persistenceAdapter, client))
      .onComplete {
        case Success(deleteChanges) => scribe.info(s"Successfully applied delete events: $deleteChanges")
        case Failure(ex)            => scribe.error("Could not apply delete events: ", ex)
      }




    /***************/
    /* Add channel */
    /***************/
    case class SlackCreateChannel(channelName: String) extends GraphChangeEvent

    def generateSlackCreateChannel(persistenceAdapter: PersistenceAdapter, node: Node, edge: Edge) = {
      val teamMapping = Team_Mapping(None, node.str, slack_deleted_flag = false, node.id)
      for{
        true <- persistenceAdapter.storeTeamMapping(teamMapping)
      } yield {
        SlackCreateChannel(node.str)
      }
    }

    // TODO: normalization
    def applyCreateChannel(persistenceAdapter: PersistenceAdapter, client: SlackClient, channel: SlackCreateChannel, wustId: NodeId) = {
      for{
        t <- client.apiClient.createChannel(channel.channelName).map(c => Team_Mapping(Some(c.id), c.name, slack_deleted_flag = false, wustId))
        true <- persistenceAdapter.updateTeamMapping(t)
      } yield {
        true
      }
    }

    def createChannelEvents(persistenceAdapter: PersistenceAdapter, client: SlackClient) = Future.sequence(filterCreateChannelEvents(gc).map { t =>
      val node = t._1
      val edge = t._2
      generateSlackCreateChannel(persistenceAdapter, node, edge).flatMap(c =>
        applyCreateChannel(persistenceAdapter, client, c, node.id)
      )
    })

    eventSlackClient.flatMap(client => createChannelEvents(persistenceAdapter, client))
      .onComplete {
        case Success(createChanges) => scribe.info(s"Successfully created channel: $createChanges")
        case Failure(ex)            => scribe.error("Could not apply create channel: ", ex)
      }


    /***************/
    /* Add message */
    /***************/
    case class SlackCreateMessage(channelId: String, text: String, username: Option[String] = None, asUser: Option[Boolean] = None,
      parse: Option[String] = None, linkNames: Option[String] = None, attachments: Option[Seq[Attachment]] = None,
      unfurlLinks: Option[Boolean] = None, unfurlMedia: Option[Boolean] = None, iconUrl: Option[String] = None,
      iconEmoji: Option[String] = None, replaceOriginal: Option[Boolean] = None,
      deleteOriginal: Option[Boolean] = None, threadTs: Option[String] = None) extends GraphChangeEvent

    def generateSlackCreateMessage(persistenceAdapter: PersistenceAdapter, node: Node, edge: Edge) = {

      val channelNodeId = edge.targetId
      val messageMapping = Message_Mapping(None, None, slack_deleted_flag = false, node.str, node.id)
      for {
        true <- OptionT[Future, Boolean](persistenceAdapter.storeMessageMapping(messageMapping).map(Some(_)))
        slackChannelId <- OptionT[Future, String](persistenceAdapter.getSlackChannelId(channelNodeId))
      } yield SlackCreateMessage(channelId = slackChannelId, text = node.str)
    }

    def applyCreateMessage(persistenceAdapter: PersistenceAdapter, client: SlackClient, message: SlackCreateMessage, wustId: NodeId, retryNumber: Int = 0): Future[Boolean] = {

        val f = for {
          m <- client.apiClient.postChatMessage(channelId = message.channelId, text = message.text, asUser = client.isUser).map(ts =>
            Message_Mapping(Some(message.channelId), Some(ts), slack_deleted_flag = false, message.text, wustId))
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
      val res = filterCreateMessageEvents(gc).map { t =>
        val node = t._1
        val edge = t._2

        (for {
          m <- generateSlackCreateMessage(persistenceAdapter, node, edge)
          true <- OptionT[Future, Boolean](applyCreateMessage(persistenceAdapter, client, m, node.id).map(Some(_)))
        } yield {
          true
        }).value
      }

      Future.sequence(res).map(_.flatten)
    }

    eventSlackClient.flatMap(client => createMessageEvents(persistenceAdapter, client))
      .onComplete {
        case Success(createChanges) => scribe.info(s"Successfully created message: $createChanges")
        case Failure(ex)            => scribe.error("Could not apply create message: ", ex)
      }


    /*****************************/
    /* Update channel or message */
    /*****************************/

    case class SlackRenameChannel(channelId: String, channelName: String) extends GraphChangeEvent
    case class SlackUpdateMessage(channelId: String, ts: String, text: String, asUser: Option[Boolean] = None) extends GraphChangeEvent

    def generateSlackRenameChannel(persistenceAdapter: PersistenceAdapter, channelNode: Node) = {
      for {
        slackChannelId <- OptionT[Future, String](persistenceAdapter.getSlackChannelId(channelNode.id))
        true <- OptionT[Future, Boolean](persistenceAdapter.updateTeamMapping(Team_Mapping(Some(slackChannelId), channelNode.str, slack_deleted_flag = false, channelNode.id)).map(Some(_)))
      } yield SlackRenameChannel(slackChannelId, channelNode.str)

    }

    def applyRenameChannel(persistenceAdapter: PersistenceAdapter, client: SlackClient, channel: SlackRenameChannel) = {
        client.apiClient.renameChannel(channel.channelId, channel.channelName)
    }

    def generateSlackUpdateMessage(persistenceAdapter: PersistenceAdapter, messageNode: Node) = {
      val updates: OptionT[Future, Option[SlackUpdateMessage]] = for {
        m <- OptionT[Future, Message_Mapping](persistenceAdapter.getSlackMessage(messageNode.id))
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
        def update = persistenceAdapter.getSlackChannelId(node.id).map {
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














    //     val slackCreateChannel: Set[SlackCreateChannel] = gc.addEdges.flatMap {
    //       case Edge.Parent(childId, EdgeData.Parent(None), Constants.slackNode.id) => //Change this later to workspace node
    //         gc.addNodes.filter(_.id == childId).map(node => SlackCreateChannel(node.str)).headOption
    //       case _                                                                   => None
    //     }
    //
    //     case class SlackRenameChannel(channelId: String, channelName: String)
    //     val slackRenameChannel = gc.addNodes.filter(_.id != gc.addEdges.flatMap {
    //       case Edge.Parent(childId, EdgeData.Parent(None), Constants.slackNode.id) =>
    //         Some(childId)
    //       case _                                                                   => None
    //     }).flatMap(node =>
    //
    //     def a = persistenceAdapter.getSlackChannelId(node.id).flatMap {
    //       case Some(slackId) => Some(SlackRenameChannel(slackId, node.str))
    //       case _             => None
    //     }
    //
    //     a
    //     )

    //        val slackCreateMessage = gc.add


    //     case class SlackUpdateMessage(channelId: String, ts: String, text: String, asUser: Option[Boolean] = None)
    //
    //     // (wustMessageNode, slackChannelId) : Future[Set[(NodeId, Option[SlackChannelId, SlackTimestamp])]]
    //     val addOrChangeMessage = Future.sequence(gc.addEdges.flatMap {
    //       case Edge.Parent(childId, EdgeData.Parent(None), parentId) =>
    //         if(parentId != Constants.slackNode.id) Some(getSlackChannelAndTs(childId))
    //         else None // this would be a channel and not a message
    //       case Edge.Author(_, EdgeData.Author(_), nodeId)            =>
    //         Some(getSlackChannelAndTs(nodeId))
    //       case _                                                     => None
    //     })



























    // def getSlackChannelAndTs(childId: NodeId): Future[(NodeId, Option[(persistenceAdapter.SlackChannelId, persistenceAdapter.SlackTimestamp)])] = {
    //   persistenceAdapter.getSlackMessage(childId).flatMap {
    //     case Some(m) =>
    //       persistenceAdapter.getSlackChannelId(m.wust_id).map {
    //         case Some(slackChannelId) => childId -> Some(slackChannelId -> m.slack_message_ts.get)
    //         case _                    => childId -> None
    //       }
    //     case _       => Future.successful(childId -> None)
    //   }
    // }

    // def getSlackChannelByParent(parentId: NodeId, childId: NodeId): Future[(NodeId, Option[persistenceAdapter.SlackChannelId])] = {
    //   persistenceAdapter.getSlackChannelId(parentId).map {
    //     case Some(slackChannelId) => childId -> Some(slackChannelId)
    //     case _                    => childId -> None
    //   }
    // }

    // def getSlackChannelByAuthor(nodeId: NodeId): Future[(NodeId, Option[persistenceAdapter.SlackChannelId])] = {
    //   persistenceAdapter.getSlackChannelOfMessageNodeId(nodeId).flatMap {
    //     case Some(parentId) => persistenceAdapter.getSlackChannelId(parentId).map {
    //       case Some(slackChannelId) => nodeId -> Some(slackChannelId)
    //       case _                    => nodeId -> None
    //     }
    //     case _              => Future.successful(nodeId -> None)
    //   }
    // }

    // def getSlackChannelByNode(nodeId: NodeId): Future[(NodeId, Option[persistenceAdapter.SlackChannelId])] = persistenceAdapter.getSlackChannelId(nodeId).map {
    //   case Some(slackId) => nodeId -> Some(slackId)
    //   case _             => nodeId -> None
    // }
    // //        val addChannelEvents = gc.addEdges.flatMap {
    // //          case Edge.Parent(childId, EdgeData.Parent(None), Constants.slackNode.id) =>
    // //            Some(childId -> Constants.slackNode.id)
    // //          case _                                                                   => None
    // //        }

    // // (wustChannelNode, slackChannelId) : Future[Set[(NodeId, Option[SlackChannelId])]]
    // //        val addOrChangeChannel = Future.sequence(gc.addEdges.flatMap {
    // //          case Edge.Parent(childId, EdgeData.Parent(None), Constants.slackNode.id) => //Change this later to workspace node
    // //            Some(getSlackChannelByNode(childId))
    // //          case Edge.Author(_, EdgeData.Author(_), nodeId) =>
    // //            Some(getSlackChannelByNode(nodeId))
    // //          case _ => None
    // //        })

    // //        persistenceAdapter.containsMessageNode()
    // //        persistenceAdapter.containsChannelNode()

    // //        val addMessageEvents: Future[collection.Set[(NodeId, persistenceAdapter.SlackChannelId)]] = Future.sequence(gc.addEdges.map {
    // //          case Edge.Parent(childId, EdgeData.Parent(None), parentId) =>
    // //            getSlackChannelByParent(parentId, childId)
    // //          case Edge.Author(_, EdgeData.Author(_), nodeId) =>
    // //            getSlackChannelByAuthor(nodeId)
    // //          case _                                                     => Future.successful(None)
    // //        }).map(_.flatten)


    // def applyChangeChannelEvents(teamMapping: Team_Mapping) = {
    //   eventSlackClient.flatMap(_.renameChannel(teamMapping.slack_team_id.get, teamMapping.slack_team_name)).map(_ => teamMapping)
    // }

    // def applyCreateChannelEvents(teamMapping: Team_Mapping) = {
    //   eventSlackClient.flatMap(_.createChannel(teamMapping.slack_team_name)).map(c => Team_Mapping(Some(c.id), c.name, false, teamMapping.wust_id))
    // }


    // def applyChangeMessageEvents(messageMapping: Message_Mapping) = {
    //   eventSlackClient.flatMap(_.updateChatMessage(messageMapping.slack_channel_id.get, messageMapping.slack_message_ts.get, messageMapping.slack_message_text, Some(true))).map(m => Message_Mapping(Some(m.channel), Some(m.ts), false, m.text, messageMapping.wust_id))
    // }

    // def applyCreateMessageEvents(messageMapping: Message_Mapping) = {
    //   eventSlackClient.flatMap(_.postChatMessage(channelId = messageMapping.slack_channel_id.get, text = messageMapping.slack_message_text, asUser = Some(true))).map(ts => messageMapping.copy(slack_message_ts = Some(ts)))
    // }


    // // Enrich when somenthing is added in wust and not modified
    // //        def enrichedAddChannelEvents: collection.Set[(NodeId, NodeId, String)] = addChannelEvents.flatMap { changes =>
    // //          gc.addNodes.filter(_.id == changes._1).map(n => (changes._1, changes._2, n.str))
    // //        }

    // //        def enrichedAddMessageEvents: Future[collection.Set[(NodeId, persistenceAdapter.SlackChannelId, String)]] = addMessageEvents.map(_.flatMap { changes =>
    // //          gc.addNodes.filter(_.id == changes._1).map(n => (changes._1, changes._2, n.str))
    // //        })

    // //        val changeOrAddChannelEvents = Future.sequence(enrichedAddChannelEvents.map { channel =>
    // //          persistenceAdapter.getSlackChannelId(channel._1).flatMap {
    // //            case Some(slackChannelId) => // Update existing channel
    // //              applyChangeChannelEvents(channel._1 -> slackChannelId)
    // //            case _                    => // Create new channel
    // //              persistenceAdapter.storeTeamMapping(Team_Mapping(None, channel._3, false, channel._1))
    // //                .flatMap(_ => applyCreateChannelEvents(channel._1))
    // //          }
    // //        }).map(_.flatten)

    // //        val changeOrAddChannelEvents = Future.sequence(enrichedAddChannelEvents.map { channel =>
    // //          persistenceAdapter.getSlackChannelId(channel._1).flatMap {
    // //            case Some(slackChannelId) => // Update existing channel
    // //              applyChangeChannelEvents(channel._1 -> slackChannelId)
    // //            case _                    => // Create new channel
    // //              persistenceAdapter.storeTeamMapping(Team_Mapping(None, channel._3, false, channel._1))
    // //                .flatMap(_ => applyCreateChannelEvents(channel._1))
    // //          }
    // //        }).map(_.flatten)

    // def changeOrAddChannelEvents = addOrChangeChannel.flatMap(f => Future.sequence(f.flatMap(channelEvent =>
    //   channelEvent._2 match {
    //     case Some(slackId) => // Change event
    //       val nodeChange = gc.addNodes.filter(_.id == channelEvent._1)
    //       if(nodeChange.nonEmpty) {
    //         nodeChange.map { channelNode =>
    //           val t = Team_Mapping(Some(slackId), channelNode.str, false, channelNode.id)
    //           applyChangeChannelEvents(t)
    //         }
    //       } else { // Only an edge added but no data in db -> more sophisticated handling necessary
    //         Set(persistenceAdapter.getTeamMappingByWustId(channelEvent._1).flatMap {
    //           case Some(t) =>
    //             scribe.error(s"Only an edge added but no data in db -> more sophisticated handling necessary: $t")
    //             Future.successful(t)
    //         })
    //       }

    //     case None => // Add event -> enrich
    //       gc.addNodes.filter(_.id == channelEvent._1).map { channelNode =>
    //         val t = Team_Mapping(None, channelNode.str, false, channelNode.id)
    //         persistenceAdapter.storeTeamMapping(t)
    //           .flatMap(_ => applyCreateChannelEvents(t))
    //       }
    //   }
    // )))

    // def changeOrAddMessageEvents = addOrChangeMessage.flatMap(f => Future.sequence(f.flatMap(messageEvent =>
    //   messageEvent._2 match {
    //     case Some(slackData) => // Change message event
    //       val nodeChange = gc.addNodes.filter(_.id == messageEvent._1)
    //       nodeChange.map { messageNode =>
    //         val m = Message_Mapping(Some(slackData._1), Some(slackData._2), false, messageNode.str, messageNode.id)
    //         persistenceAdapter.updateMessageMapping(m)
    //         applyChangeMessageEvents(m)
    //       }

    //     case None => // Add event -> enrich
    //       gc.addNodes.filter(_.id == messageEvent._1).map { messageNode =>
    //         val m = Message_Mapping(None, None, false, messageNode.str, messageNode.id)
    //         persistenceAdapter.storeMessageMapping(m)
    //           .flatMap(_ => applyCreateMessageEvents(m))
    //       }
    //   }
    // )))


    // //        val changeOrAddMessageEvents = enrichedAddMessageEvents.flatMap(events =>
    // //          Future.sequence(events.map { message =>
    // //            persistenceAdapter.getSlackMessage(message._1).flatMap {
    // //              case Some(slackMessage) => // Update message
    // //                applyChangeMessageEvents(message._1 -> slackMessage)
    // //              case _                  => // Create message
    // //                persistenceAdapter.storeMessageMapping(Message_Mapping(None, None, false, message._3, message._1))
    // //                  .flatMap(_ => applyCreateMessageEvents(message._1, message._2))
    // //            }
    // //          })
    // //        ).map(_.flatten)

    // // First persist intermediate but identifiable mapping
    // changeOrAddChannelEvents.map(_.map(t =>
    //   persistenceAdapter.updateTeamMapping(t)
    // )).onComplete {
    //   case Success(mapping) => scribe.info(s"Successfully added or updated team mapping: $mapping")
    //   case Failure(ex)      => scribe.error("Could not add or update team mapping: ", ex)
    // }

    // changeOrAddMessageEvents.map(_.map(m =>
    //   persistenceAdapter.updateMessageMapping(m)
    // )).onComplete {
    //   case Success(mapping) => scribe.info(s"Successfully added or updated message mapping: $mapping")
    //   case Failure(ex)      => scribe.error("Could not add or update message mapping: ", ex)
    // }

    // changeOrAddChannelEvents.onComplete {
    //   case Success(changes) => scribe.info(s"Successfully created or updated slack channel: $changes")
    //   case Failure(ex)      => scribe.error("Could not create or update slack channel: ", ex)
    // }

    // changeOrAddMessageEvents.onComplete {
    //   case Success(changes) => scribe.info(s"Successfully created or updated slack message: $changes")
    //   case Failure(ex)      => scribe.error("Could not create or update slack message: ", ex)
    // }

  }
}
