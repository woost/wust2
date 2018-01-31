package wust.slack

import slack.SlackUtil
import slack.models._
import slack.rtm.SlackRtmClient

import wust.sdk._
import wust.api._
import wust.ids._
import wust.graph._
import mycelium.client._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Constants {
  //TODO
  val slackId = "wust-slack"
}

case class ExchangeMessage(content: String)

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(msg: ExchangeMessage, author: UserId): Result[Post]
}

class WustReceiver(client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(msg: ExchangeMessage, author: UserId) = {
    println(s"new message: msg")
    val post = Post(PostId.fresh, msg.content, author)
    val connection = Connection(post.id, Label.parent, Constants.slackId)

    val changes = List(GraphChanges(addPosts = Set(post), addConnections = Set(connection)))
    client.api.changeGraph(changes).map { success =>
      if (success) Right(post)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {
  type Result[T] = Either[String, T]

  val wustUser = UserId("wust-slack")

  def run(config: WustConfig, slack: SlackClient)(implicit ec: ExecutionContext): Future[Result[WustReceiver]] = {
    implicit val system = ActorSystem("wust")
    implicit val materializer = ActorMaterializer()

    val location = s"ws://${config.host}:8080/ws"
    val handler = new IncidentHandler[ApiEvent] {
      override def onConnect(): Unit = println(s"Connected to websocket")
      override def onClose(): Unit = println(s"Websocket connection closed")
      override def onEvents(events: Seq[ApiEvent]): Unit = {
        println(s"Got events: $events")
        val changes = events collect { case ApiEvent.NewGraphChanges(changes) => changes }
        val posts = changes.flatMap(_.addPosts)
        posts.map(p => ExchangeMessage(p.content)).foreach { msg =>
          slack.send(msg).foreach { success =>
            println(s"Send message success: $success")
          }
        }
      }
    }
    val client = AkkaWustClient(location, handler).sendWith(SendType.NowOrFail, 30 seconds)

    val res = for {
      loggedIn <- client.auth.login(config.user, config.password)
      if loggedIn
      changed <- client.api.changeGraph(List(GraphChanges(addPosts = Set(Post(Constants.slackId, "wust-slack", wustUser)))))
      if changed
      graph <- client.api.getGraph(Page.Root)
    } yield Right(new WustReceiver(client))

    res recover { case e =>
      system.terminate()
      Left(e.getMessage)
    }
  }
}


class SlackClient(client: SlackRtmClient)(implicit ec: ExecutionContext) {

  def send(msg: ExchangeMessage): Future[Boolean] = {
    val channelId = client.state.getChannelIdForName("general").get //TODO
    val text = msg.content

    client
      .sendMessage(channelId, text)
      .map(_ => true)
      .recover { case NonFatal(_) => false }
  }

  def run(receiver: MessageReceiver): Unit = {
    val selfId = client.state.self.id
    client.onEvent {
      case e: Message =>
        println(s"Got message from '${e.user}' in channel '${e.channel}': ${e.text}")

        def respond(msg: String) = client.sendMessage(e.channel, s"<@${e.user}>: $msg")

        val mentionedIds = SlackUtil.extractMentionedIds(e.text)
        if(mentionedIds.contains(selfId)) {
          val message = ExchangeMessage(e.text)
          receiver.push(message, WustReceiver.wustUser) foreach {
            case Left(error) => respond(s"Failed to sync with wust: $error")
            case Right(post) => respond(s"Created post: $post")
          }
        }


      case e => println(s"ignored event: $e")
    }
  }
}

object SlackClient {
  def apply(accessToken: String)(implicit ec: ExecutionContext): SlackClient = {
    implicit val system = ActorSystem("slack")
    val client = SlackRtmClient(accessToken)
    new SlackClient(client)
  }
}

object App extends scala.App {
  import scala.concurrent.ExecutionContext.Implicits.global

  Config.load match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      val client = SlackClient(config.accessToken)
      WustReceiver.run(config.wust, client).foreach {
        case Right(receiver) => client.run(receiver)
        case Left(err) => println(s"Cannot connect to Wust: $err")
      }
  }
}
