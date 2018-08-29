package wust.slack

import akka.actor.ActorSystem
import slack.api.{HistoryChunk, SlackApiClient}
import slack.models.{Channel, ChannelCreated, HistoryMessage, Message, SubRefMessage}
import wust.api.ApiEvent.NewGraphChanges
import wust.graph.GraphChanges
import wust.slack.Data._

import scala.concurrent.{ExecutionContext, Future}

object SlackSeeder {

  type GetHistory = (String, Option[String], Option[String], Option[Int], Option[Int]) => Future[HistoryChunk]

  private def getChannelChunks(getHistory: GetHistory, channelId: SlackChannelId, recLatest: Option[String] = None, hasMore: Boolean = true)(implicit system: ActorSystem, ec: ExecutionContext): Future[Seq[HistoryChunk]] = {

    scribe.info("----- CALL -----")
    getHistory(channelId, recLatest, None, None, Some(1000)).flatMap{ currChunk =>
      scribe.info(s"#chunk messages: ${currChunk.messages.size}, lastMessage: ${currChunk.messages.last}")
      if(currChunk.has_more) {
        val lastMessage = currChunk.messages.last
        getChannelChunks(getHistory, channelId, Some(lastMessage.ts)).map(chunks => chunks :+ currChunk)
      } else
        Future.successful(Seq(currChunk))
    }
  }

  private def getHistoryData(slackClient: SlackApiClient, getHistory: GetHistory)(implicit system: ActorSystem, ec: ExecutionContext): Future[Seq[(Channel, Seq[HistoryChunk])]] = {
    scribe.info("----- CALL -----")
    slackClient.listChannels().flatMap(channels =>
      Future.sequence(
        channels.map( channel =>
          getChannelChunks(getHistory, channel.id).map(chunks => channel -> chunks)
        )
      )
    )
  }

  def getChannelsData(slackClient: SlackApiClient)(implicit system: ActorSystem, ec: ExecutionContext): Future[Seq[(Channel, Seq[HistoryChunk])]] = {
    scribe.info("----- CALL -----")
    getHistoryData(slackClient, slackClient.getChannelHistory)
  }

  def getGroupData(slackClient: SlackApiClient)(implicit system: ActorSystem, ec: ExecutionContext): Future[Seq[(Channel, Seq[HistoryChunk])]] = {
    getHistoryData(slackClient, slackClient.getGroupHistory)
  }

  def getImData(slackClient: SlackApiClient)(implicit system: ActorSystem, ec: ExecutionContext): Future[Seq[(Channel, Seq[HistoryChunk])]] = {
    getHistoryData(slackClient, slackClient.getImHistory)

  }

  def getMpimData(slackClient: SlackApiClient)(implicit system: ActorSystem, ec: ExecutionContext): Future[Seq[(Channel, Seq[HistoryChunk])]] = {
    getHistoryData(slackClient, slackClient.getMpimHistory)
  }

//  def historyDataToWust(slackApiClient: SlackApiClient, slackEventMapper: SlackEventMapper, teamId: SlackTeamId) = {
//    getChannelsData(slackApiClient).flatMap(channels =>
//      Future.sequence(channels.map {
//        case (channel: Channel, history: Seq[HistoryChunk]) =>
//
//          //TODO: Only differs here for channel, group, im and mpim data to wust
//
//      }).map(_.flatten)
//    )
//  }

  def channelDataToWust(slackApiClient: SlackApiClient, slackEventMapper: SlackEventMapper, persistenceAdapter: PersistenceAdapter, teamId: SlackTeamId)(implicit system: ActorSystem, ec: ExecutionContext): Future[Seq[GraphChanges]] = {
    scribe.info("----- CALL -----")

    def createWustMessages(channel: Channel, history: Seq[HistoryChunk]) = {
      scribe.info("----- CALL -----")
      Future.sequence(history.flatMap(_.messages.collect {

        case message: HistoryMessage =>
          slackEventMapper.createMessage(Message(ts = message.ts, channel = channel.id, user = message.user, text = message.text, channel_type = "channel", None, None, None, None), teamId).map {
            case Right(messageGc) =>
              messageGc
            case Left(s)          =>
              scribe.error(s)
              List.empty[GraphChanges]
          }

      })).map(_.flatten)
    }


//    def createWustMessages(channel: Channel, history: Seq[HistoryChunk]) = {
//      def createMessagesGC= Future.sequence(history.flatMap(_.messages.collect {
//        case message: HistoryMessage =>
//          val createMessage = Message(ts = message.ts, channel = channel.id, user = message.user, text = message.text, channel_type = "channel", None, None, None, None)
//          EventComposer.createMessage(createMessage, teamId).value
//      }))
//
//      val res = wustReceiver.push(List(changes.gc), Some(changes.user))
//
//      createMessagesGC.map(_.flatten).map()
//    }

    getChannelsData(slackApiClient).flatMap(channels =>
      Future.sequence(channels.map {
        case (channel: Channel, history: Seq[HistoryChunk]) =>

          val channelCreation = persistenceAdapter.channelExistsByNameAndTeam(teamId, channel.name).flatMap(exists =>
            if(exists) {
              Future.successful(Right(Seq.empty[GraphChanges]))
            } else {
              slackEventMapper.createChannel(ChannelCreated(channel), teamId)
            }
          )

          channelCreation.flatMap {
            case Right(gc) =>
              createWustMessages(channel, history)
            case Left(s) =>
              scribe.error(s)
              Future.successful(Seq.empty[GraphChanges])
          }

      }).map(_.flatten)
    )
  }

  def groupDataToWust(slackApiClient: SlackApiClient, slackEventMapper: SlackEventMapper, teamId: SlackTeamId)(implicit system: ActorSystem, ec: ExecutionContext) = {
    ???
  }

  def imDataToWust(slackApiClient: SlackApiClient, slackEventMapper: SlackEventMapper, teamId: SlackTeamId)(implicit system: ActorSystem, ec: ExecutionContext) = {
    ???
  }

  def mpimDataToWust(slackApiClient: SlackApiClient, slackEventMapper: SlackEventMapper, teamId: SlackTeamId)(implicit system: ActorSystem, ec: ExecutionContext) = {
    ???
  }
  
}