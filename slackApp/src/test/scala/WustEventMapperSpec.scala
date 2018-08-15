package wust.slack

import akka.actor.ActorSystem
import org.scalatest._
import slack.api.SlackApiClient
import wust.graph.{Edge, GraphChanges, Node, NodeMeta}
import wust.ids._
import wust.sdk.WustClient
import wust.slack.Data._

import scala.concurrent.Future

case object MockAdapter extends PersistenceAdapter {

  implicit val system: ActorSystem = ActorSystem("slack-mock-adapter")
  import monix.execution.Scheduler.Implicits.global

  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean] = ???
  def storeMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = ???
  def storeTeamMapping(teamMapping: Channel_Mapping): Future[Boolean] = ???

  def updateMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = ???
  def updateTeamMapping(teamMapping: Channel_Mapping): Future[Boolean] = ???

  def getTeamMappingBySlackName(channelName: String): Future[Option[Channel_Mapping]] = ???
  def getTeamMappingByWustId(nodeId: NodeId): Future[Option[Channel_Mapping]] = ???

  def getOrCreateWustUser(slackUser: SlackUserId, wustClient: WustClient): Future[Option[WustUserData]] = ???
  def getOrCreateSlackUser(wustUser: SlackUserId): Future[Option[SlackUserData]] = ???

  def getChannelNodeById(channelId: SlackChannelId): Future[Option[NodeId]] = ???
  def getChannelNodeByName(channelName: String): Future[Option[NodeId]] = ???
  def getOrCreateChannelNode(channel: SlackChannelId, slackNode: NodeId, wustReceiver: WustReceiver, slackClient: SlackApiClient): Future[Option[NodeId]] = ???

  def getSlackChannelId(nodeId: NodeId): Future[Option[SlackChannelId]] = ???
  def getSlackMessage(nodeId: NodeId): Future[Option[Message_Mapping]] = ???
  def getSlackUser(userId: UserId): Future[Option[SlackUserData]] = ???

  def getMessageNodeByChannelAndTimestamp(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = ???
  def getMessageNodeByContent(text: String): Future[Option[NodeId]] = ???

  def getSlackChannelOfMessageNodeId(nodeId: NodeId): Future[Option[NodeId]] = ???


  def deleteMessage(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = ???
  def deleteChannel(teamId: SlackChannelId): Future[Boolean] = ???
  def unDeleteChannel(teamId: SlackChannelId): Future[Boolean] = ???



  // Guards
  def isSlackMessageDeleted(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = ???
  def isSlackMessageUpToDate(channel: String, timestamp: String, text: String): Future[Boolean] = ???
  def isSlackChannelDeleted(teamId: String): Future[Boolean] = ???
  def isSlackChannelUpToDate(teamId: String, name: String): Future[Boolean] = ???
  def isSlackChannelUpToDateElseGetNode(teamId: String, name: String): Future[Option[NodeId]] = ???
}

object TestConstants {
  val workspaceId: NodeId = Constants.slackNode.id
  val messageNodeId: NodeId = NodeId.fromBase58String("5R73FK2PwrEU6Kt1dEUzkJ")
  val channelNodeId: NodeId = NodeId.fromBase58String("5R73E84pCdVisswaNFr47x")
  val userId: UserId = UserId.fromBase58String("5R5TZsZJeL3enTMmq8Jwmg")
}

class WustEventMapperSpec extends FreeSpec with EitherValues with Matchers {

  implicit val system: ActorSystem = ActorSystem("slack-test-wust-event")
  import monix.execution.Scheduler.Implicits.global


  def getMapper(path: String = "wust.slack.test") = {
    WustEventMapper("", MockAdapter)
  }

//  "load config" in {
//    val config = Config.load("wust.slack.test")
//    config should be ('right)
//  }

  "detect: create message event" in {
    val mapper: WustEventMapper = getMapper()

//     List(GraphChanges(Set(Content(5R73FK2PwrEU6Kt1dEUzkJ,Markdown(createMessageEvent),NodeMeta(Inherited))),Set(Parent(5R73FK2PwrEU6Kt1dEUzkJ,Parent,5R73E84pCdVisswaNFr47x), Author(5R5TZsZJeL3enTMmq8Jwmg,Author(2018-08-12 14:03:14),5R73FK2PwrEU6Kt1dEUzkJ)),Set()))
    val createMessage = GraphChanges(
      addNodes = Set(
        Node.Content(
          TestConstants.messageNodeId,
          NodeData.Markdown("createMessageEvent"),
          NodeMeta(NodeAccess.Inherited),
        )
      ),
      addEdges = Set(
        Edge.Parent(
          TestConstants.messageNodeId,
          EdgeData.Parent(None),
          TestConstants.channelNodeId
        ),
        Edge.Author(
          TestConstants.userId,
          EdgeData.Author(EpochMilli.now),
          TestConstants.messageNodeId
        ),
      ),
      delEdges = Set.empty[Edge]
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(createMessage)
    val filterCreateChannel = mapper.filterCreateChannelEvents(createMessage)
    val filterDelete = mapper.filterDeleteEvents(createMessage)
    val filterUpdate = mapper.filterUpdateEvents(createMessage)

    filterCreateMessage.nonEmpty shouldBe true
    filterCreateChannel.nonEmpty shouldBe false
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe false

  }

  "detect: create channel event" in {
    val mapper: WustEventMapper = getMapper()

    // List(GraphChanges(Set(Content(5R73E84pCdVisswaNFr47x,Markdown(createChannelEvent),NodeMeta(Inherited))),Set(Parent(5R73E84pCdVisswaNFr47x,Parent,5R28qFeQj1Ny6tM9b7BXis), Author(5R5TZsZJeL3enTMmq8Jwmg,Author(2018-08-12 14:02:38),5R73E84pCdVisswaNFr47x)),Set()))
    val createChannel = GraphChanges(
      addNodes = Set(
        Node.Content(
          TestConstants.channelNodeId,
          NodeData.Markdown("createChannelEvent"),
          NodeMeta(NodeAccess.Inherited),
        )
      ),
      addEdges = Set(
        Edge.Parent(
          TestConstants.channelNodeId,
          EdgeData.Parent(None),
          NodeId.fromBase58String("5R28qFeQj1Ny6tM9b7BXis"),
        ),
        Edge.Author(
          TestConstants.userId,
          EdgeData.Author(EpochMilli.now),
          TestConstants.channelNodeId,
        )
      ),
      delEdges = Set.empty[Edge]
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(createChannel)
    val filterCreateChannel = mapper.filterCreateChannelEvents(createChannel)
    val filterDelete = mapper.filterDeleteEvents(createChannel)
    val filterUpdate = mapper.filterUpdateEvents(createChannel)

    filterCreateMessage.nonEmpty shouldBe false
    filterCreateChannel.nonEmpty shouldBe true
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe false

  }

  "detect: delete message event" in {
    val mapper: WustEventMapper = getMapper()

    // List(GraphChanges(Set(),Set(Parent(5R73FK2PwrEU6Kt1dEUzkJ,Parent(deletedAt = 2018-08-12 14:08:32),5R73E84pCdVisswaNFr47x)),Set()))
    val deleteMessage = GraphChanges(
      addNodes = Set.empty[Node],
      addEdges = Set(
        Edge.Parent(
          TestConstants.messageNodeId,
          EdgeData.Parent(Some(EpochMilli.now)),
          TestConstants.channelNodeId,
        ),
      ),
      delEdges = Set.empty[Edge]
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(deleteMessage)
    val filterCreateChannel = mapper.filterCreateChannelEvents(deleteMessage)
    val filterDelete = mapper.filterDeleteEvents(deleteMessage)
    val filterUpdate = mapper.filterUpdateEvents(deleteMessage)

    filterCreateMessage.nonEmpty shouldBe false
    filterCreateChannel.nonEmpty shouldBe false
    filterDelete.nonEmpty shouldBe true
    filterUpdate.nonEmpty shouldBe false

  }

  "detect: delete channel event" in {
    val mapper: WustEventMapper = getMapper()

    // List(GraphChanges(Set(),Set(Parent(5R73E84pCdVisswaNFr47x,Parent(deletedAt = 2018-08-12 14:09:27),5R28qFeQj1Ny6tM9b7BXis)),Set()))
    val deleteChannel = GraphChanges(
      addNodes = Set.empty[Node],
      addEdges = Set(
        Edge.Parent(
          TestConstants.channelNodeId,
          EdgeData.Parent(Some(EpochMilli.now)),
          NodeId.fromBase58String("5R28qFeQj1Ny6tM9b7BXis"),
        ),
      ),
      delEdges = Set.empty[Edge]
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(deleteChannel)
    val filterCreateChannel = mapper.filterCreateChannelEvents(deleteChannel)
    val filterDelete = mapper.filterDeleteEvents(deleteChannel)
    val filterUpdate = mapper.filterUpdateEvents(deleteChannel)

    filterCreateMessage.nonEmpty shouldBe false
    filterCreateChannel.nonEmpty shouldBe false
    filterDelete.nonEmpty shouldBe true
    filterUpdate.nonEmpty shouldBe false

  }

  "detect: update message event" in {
    val mapper: WustEventMapper = getMapper()

    // List(GraphChanges(Set(Content(5R73FK2PwrEU6Kt1dEUzkJ,Markdown(updateMessageEvent),NodeMeta(Inherited))),Set(Author(5R5TZsZJeL3enTMmq8Jwmg,Author(2018-08-12 14:04:59),5R73FK2PwrEU6Kt1dEUzkJ)),Set()))
    val updateMessage = GraphChanges(
      addNodes = Set(
        Node.Content(
          TestConstants.messageNodeId,
          NodeData.Markdown("updateMessageEvent"),
          NodeMeta(NodeAccess.Inherited),
        )
      ),
      addEdges = Set(
        Edge.Author(
          TestConstants.userId,
          EdgeData.Author(EpochMilli.now),
          TestConstants.messageNodeId
        ),
      ),
      delEdges = Set.empty[Edge]
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(updateMessage)
    val filterCreateChannel = mapper.filterCreateChannelEvents(updateMessage)
    val filterDelete = mapper.filterDeleteEvents(updateMessage)
    val filterUpdate = mapper.filterUpdateEvents(updateMessage)

    filterCreateMessage.nonEmpty shouldBe false
    filterCreateChannel.nonEmpty shouldBe false
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe true

  }

  "detect: rename channel event" in {
    val mapper: WustEventMapper = getMapper()

    // List(GraphChanges(Set(Content(5R73E84pCdVisswaNFr47x,Markdown(updateChannelEvent),NodeMeta(Inherited))),Set(Author(5R5TZsZJeL3enTMmq8Jwmg,Author(2018-08-12 14:07:30),5R73E84pCdVisswaNFr47x)),Set())))
    val renameChannel = GraphChanges(
      addNodes = Set(
        Node.Content(
          TestConstants.channelNodeId,
          NodeData.Markdown("updateChannelEvent"),
          NodeMeta(NodeAccess.Inherited),
        )
      ),
      addEdges = Set(
        Edge.Author(
          TestConstants.userId,
          EdgeData.Author(EpochMilli.now),
          TestConstants.channelNodeId,
        ),
      ),
      delEdges = Set.empty[Edge]
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(renameChannel)
    val filterCreateChannel = mapper.filterCreateChannelEvents(renameChannel)
    val filterDelete = mapper.filterDeleteEvents(renameChannel)
    val filterUpdate = mapper.filterUpdateEvents(renameChannel)

    filterCreateMessage.nonEmpty shouldBe false
    filterCreateChannel.nonEmpty shouldBe false
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe true

  }

}
