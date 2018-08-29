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

  // Store
  def storeOrUpdateUserAuthData(userMapping: User_Mapping): Future[Boolean] = ???
  def storeOrUpdateMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = ???
  def storeOrUpdateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean] = ???
  def storeOrUpdateTeamMapping(teamMapping: Team_Mapping): Future[Boolean] = ???


  // Update
  def updateMessageMapping(messageMapping: Message_Mapping): Future[Boolean] = ???
  def updateChannelMapping(channelMapping: Channel_Mapping): Future[Boolean] = ???


  // Delete
  def deleteChannelBySlackId(channelId: SlackChannelId): Future[Boolean] = ???
  def unDeleteChannelBySlackId(channelId: SlackChannelId): Future[Boolean] = ???
  def deleteMessageBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = ???


  // Queries
  // Query Wust NodeId by Slack Id
  def getTeamNodeBySlackId(teamId: SlackTeamId): Future[Option[NodeId]] = ???
  def getChannelNodeBySlackId(channelId: SlackChannelId): Future[Option[NodeId]] = ???
  def getMessageNodeBySlackIdData(channel: SlackChannelId, timestamp: SlackTimestamp): Future[Option[NodeId]] = ???


  // Query Slack Id by Wust NodeId
  def getSlackChannelByWustId(nodeId: NodeId): Future[Option[SlackChannelId]] = {
    val m = Map( TestConstants.channelNodeId -> "SlackChannelId")
    Future.successful(m.get(nodeId))
  }


  // Query Data by Slack Id


  // Query Data by Wust Id
  def getWustUserBySlackUserId(slackUser: SlackUserId): Future[Option[WustUserData]] = ???
  def getSlackUserDataByWustId(userId: UserId): Future[Option[SlackUserData]] = ???
  def getChannelMappingByWustId(nodeId: NodeId): Future[Option[Channel_Mapping]] = ???
  def getMessageMappingByWustId(nodeId: NodeId): Future[Option[Message_Mapping]] = {
    val m = Map(
      TestConstants.messageNodeId -> Message_Mapping(None, None, None, false, "message", TestConstants.messageNodeId, TestConstants.channelNodeId),
      TestConstants.threadNodeId -> Message_Mapping(None, None, None, false, "thread", TestConstants.messageNodeId, TestConstants.channelNodeId)
    )
    Future.successful(m.get(nodeId))
  }


  // Guards
  def channelExistsByNameAndTeam(teamId: SlackTeamId, channelName: String): Future[Boolean] = ???
  def isChannelDeletedBySlackId(channelId: String): Future[Boolean] = ???
  def isChannelUpToDateBySlackDataElseGetNodes(channelId: String, name: String): Future[Option[(NodeId, NodeId)]] = ???
  def isMessageDeletedBySlackIdData(channelId: SlackChannelId, timestamp: SlackTimestamp): Future[Boolean] = ???
  def isMessageUpToDateBySlackData(channel: String, timestamp: String, text: String): Future[Boolean] = ???


  // Boolean Guards / Filter by wust id
  def teamExistsByWustId(nodeId: NodeId): Future[Boolean] = Future.successful(nodeId == TestConstants.workspaceId)
  def channelExistsByWustId(nodeId: NodeId): Future[Boolean] = Future.successful(nodeId == TestConstants.channelNodeId)
  def threadExistsByWustId(nodeId: NodeId): Future[Boolean] = Future.successful(nodeId == TestConstants.threadNodeId)
  def messageExistsByWustId(nodeId: NodeId): Future[Boolean] = Future.successful(nodeId == TestConstants.messageNodeI
  )
}

object TestConstants {
  val workspaceId: NodeId = NodeId.fromBase58String("5R73FK2PwrEU6Kt1dEUzkw")
  val channelNodeId: NodeId = NodeId.fromBase58String("5R73E84pCdVisswaNFr47x")
  val threadNodeId: NodeId = NodeId.fromBase58String("5RMARqY8EPnGmKG9LbPmQv")
  val messageNodeId: NodeId = NodeId.fromBase58String("5R73FK2PwrEU6Kt1dEUzkJ")

  val userId: UserId = UserId.fromBase58String("5R5TZsZJeL3enTMmq8Jwmg")
  val userChannelNodeId: NodeId = NodeId.fromBase58String("5RLXr4V6DNSF1hzwFqiBYn")

  val userNode = Node.User(
    TestConstants.userId,
    NodeData.User("testuser", false, 0, userChannelNodeId),
    NodeMeta(NodeAccess.Restricted)
  )
  def authorEdge(nodeId: NodeId) = Edge.Author(
    userId,
    EdgeData.Author(EpochMilli.now),
    nodeId
  )

  def messageNode(message: String): Node =  Node.Content(
      TestConstants.messageNodeId,
      NodeData.Markdown(message),
      NodeMeta(NodeAccess.Inherited),
    )
  def threadNode(message: String): Node =  Node.Content(
    TestConstants.threadNodeId,
    NodeData.Markdown(message),
    NodeMeta(NodeAccess.Inherited),
  )
  def channelNode(channel: String): Node =  Node.Content(
    TestConstants.channelNodeId,
    NodeData.Markdown(channel),
    NodeMeta(NodeAccess.Inherited),
  )
  val workspaceNode: Node = Node.Content(
    TestConstants.channelNodeId,
    NodeData.Markdown("workspace"),
    NodeMeta(NodeAccess.Restricted),
  )

  def messageChannelEdge(delete: Boolean = false): Edge.Parent = Edge.Parent(
      TestConstants.messageNodeId,
      EdgeData.Parent(if(delete) Some(EpochMilli.now) else None),
      TestConstants.channelNodeId
    )
  def messageThreadEdge(delete: Boolean = false): Edge.Parent = Edge.Parent(
    TestConstants.messageNodeId,
    EdgeData.Parent(if(delete) Some(EpochMilli.now) else None),
    TestConstants.threadNodeId
  )
  def channelWorkspaceEdge(delete: Boolean = false): Edge.Parent = Edge.Parent(
    TestConstants.channelNodeId,
    EdgeData.Parent(if(delete) Some(EpochMilli.now) else None),
    TestConstants.workspaceId
  )

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

//  "use correct user for events" in {
//    val mapper: WustEventMapper = getMapper()
//
//    // TODO
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
    val filterCreateThread = mapper.filterCreateThreadEvents(createMessage)
    val filterCreateChannel = mapper.filterCreateChannelEvents(createMessage)
    val filterDelete = mapper.filterDeleteEvents(createMessage)
    val filterUpdate = mapper.filterUpdateEvents(createMessage)
    val filterUnDelete = mapper.filterUndeleteEvents(createMessage)

    filterCreateMessage.map(_.nonEmpty shouldBe true)
    filterCreateThread.map(_.nonEmpty shouldBe false)
    filterCreateChannel.map(_.nonEmpty shouldBe false)
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe false
    filterUnDelete.nonEmpty shouldBe false

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
          TestConstants.workspaceId,
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
    val filterCreateThread = mapper.filterCreateThreadEvents(createChannel)
    val filterCreateChannel = mapper.filterCreateChannelEvents(createChannel)
    val filterDelete = mapper.filterDeleteEvents(createChannel)
    val filterUpdate = mapper.filterUpdateEvents(createChannel)
    val filterUnDelete = mapper.filterUndeleteEvents(createChannel)

    filterCreateMessage.map(_.nonEmpty shouldBe false)
    filterCreateThread.map(_.nonEmpty shouldBe false)
    filterCreateChannel.map(_.nonEmpty shouldBe true)
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe false
    filterUnDelete.nonEmpty shouldBe false

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
    val filterCreateThread = mapper.filterCreateThreadEvents(deleteMessage)
    val filterCreateChannel = mapper.filterCreateChannelEvents(deleteMessage)
    val filterDelete = mapper.filterDeleteEvents(deleteMessage)
    val filterUpdate = mapper.filterUpdateEvents(deleteMessage)
    val filterUnDelete = mapper.filterUndeleteEvents(deleteMessage)

    filterCreateMessage.map(_.nonEmpty shouldBe false)
    filterCreateThread.map(_.nonEmpty shouldBe false)
    filterCreateChannel.map(_.nonEmpty shouldBe false)
    filterDelete.nonEmpty shouldBe true
    filterUpdate.nonEmpty shouldBe false
    filterUnDelete.nonEmpty shouldBe false

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
          TestConstants.workspaceId,
        ),
      ),
      delEdges = Set.empty[Edge]
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(deleteChannel)
    val filterCreateThread = mapper.filterCreateThreadEvents(deleteChannel)
    val filterCreateChannel = mapper.filterCreateChannelEvents(deleteChannel)
    val filterDelete = mapper.filterDeleteEvents(deleteChannel)
    val filterUpdate = mapper.filterUpdateEvents(deleteChannel)
    val filterUnDelete = mapper.filterUndeleteEvents(deleteChannel)

    filterCreateMessage.map(_.nonEmpty shouldBe false)
    filterCreateThread.map(_.nonEmpty shouldBe false)
    filterCreateChannel.map(_.nonEmpty shouldBe false)
    filterDelete.nonEmpty shouldBe true
    filterUpdate.nonEmpty shouldBe false
    filterUnDelete.nonEmpty shouldBe false

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
    val filterCreateThread = mapper.filterCreateThreadEvents(updateMessage)
    val filterCreateChannel = mapper.filterCreateChannelEvents(updateMessage)
    val filterDelete = mapper.filterDeleteEvents(updateMessage)
    val filterUpdate = mapper.filterUpdateEvents(updateMessage)
    val filterUnDelete = mapper.filterUndeleteEvents(updateMessage)

    filterCreateMessage.map(_.nonEmpty shouldBe false)
    filterCreateThread.map(_.nonEmpty shouldBe false)
    filterCreateChannel.map(_.nonEmpty shouldBe false)
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe true
    filterUnDelete.nonEmpty shouldBe false

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
    val filterCreateThread = mapper.filterCreateThreadEvents(renameChannel)
    val filterCreateChannel = mapper.filterCreateChannelEvents(renameChannel)
    val filterDelete = mapper.filterDeleteEvents(renameChannel)
    val filterUpdate = mapper.filterUpdateEvents(renameChannel)
    val filterUnDelete = mapper.filterUndeleteEvents(renameChannel)

    filterCreateMessage.map(_.nonEmpty shouldBe false)
    filterCreateThread.map(_.nonEmpty shouldBe false)
    filterCreateChannel.map(_.nonEmpty shouldBe false)
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe true
    filterUnDelete.nonEmpty shouldBe false

  }

  "detect: undelete channel event" in {
    val mapper: WustEventMapper = getMapper()

//    List(ForPublic(5RBLjXEbmu16SNUjyee7Bf,GraphChanges(Set(User(5RBLjXEbmu16SNUjyee7Bf,User(j,false,0,5RBLjXF3CLH6vX1GJ1AS8g),NodeMeta(Level(Restricted)))),Set(Parent(5RGHCTvXSd1cKniWc3xc25,Parent(deletedAt = 2018-08-23 15:08:54),5RGHCHpdWsK4X6NpzbCVMK)),Set())))

    val undeleteChannel = GraphChanges(
      addNodes = Set(
        Node.User(
          TestConstants.userId,
          NodeData.User("j",false,0,NodeId.fromBase58String("5RBLjXF3CLH6vX1GJ1AS8g")),
          NodeMeta(NodeAccess.Restricted)
        )
      ),
      addEdges = Set(
        Edge.Parent(
          NodeId.fromBase58String("5RGHCTvXSd1cKniWc3xc25"),
          EdgeData.Parent(None),
          NodeId.fromBase58String("5RGHCHpdWsK4X6NpzbCVMK")
        ),
      )
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(undeleteChannel)
    val filterCreateThread = mapper.filterCreateThreadEvents(undeleteChannel)
    val filterCreateChannel = mapper.filterCreateChannelEvents(undeleteChannel)
    val filterDelete = mapper.filterDeleteEvents(undeleteChannel)
    val filterUpdate = mapper.filterUpdateEvents(undeleteChannel)
    val filterUnDelete = mapper.filterUndeleteEvents(undeleteChannel)

    filterCreateMessage.map(_.nonEmpty shouldBe false)
    filterCreateThread.map(_.nonEmpty shouldBe false)
    filterCreateChannel.map(_.nonEmpty shouldBe false)
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe false
    filterUnDelete.nonEmpty shouldBe true

  }

  "detect: create thread event" in {
    val mapper: WustEventMapper = getMapper()

    // List(ForPublic(5RLXr4Uyrm7Bn2iLM4dzLV,GraphChanges(Set(User(5RLXr4Uyrm7Bn2iLM4dzLV,User(j,false,0,5RLXr4V6DNSF1hzwFqiBYn),NodeMeta(Level(Restricted))), Content(5RMARqY8EPnGmKG9LbPmQv,Markdown(createThreadMessageEvent),NodeMeta(Inherited))),Set(Parent(5RMARqY8EPnGmKG9LbPmQv,Parent,5RMARVYwEuiRsT7SYXbL2t), Author(5RLXr4Uyrm7Bn2iLM4dzLV,Author(2018-08-29 11:14:15),5RMARqY8EPnGmKG9LbPmQv)),Set())))

    val createThread = GraphChanges(
      Set(
        TestConstants.userNode,
        TestConstants.messageNode("createThreadMessageEvent"),
      ),
      Set(
        TestConstants.messageThreadEdge(),
        TestConstants.authorEdge(TestConstants.userNode.id),
      )
    )

    val filterCreateMessage = mapper.filterCreateMessageEvents(createThread)
    val filterCreateThread = mapper.filterCreateThreadEvents(createThread)
    val filterCreateChannel = mapper.filterCreateChannelEvents(createThread)
    val filterDelete = mapper.filterDeleteEvents(createThread)
    val filterUpdate = mapper.filterUpdateEvents(createThread)
    val filterUnDelete = mapper.filterUndeleteEvents(createThread)

    filterCreateMessage.map(_.nonEmpty shouldBe false)
    filterCreateThread.map(_.nonEmpty shouldBe true)
    filterCreateChannel.map(_.nonEmpty shouldBe false)
    filterDelete.nonEmpty shouldBe false
    filterUpdate.nonEmpty shouldBe false
    filterUnDelete.nonEmpty shouldBe false

  }

}
