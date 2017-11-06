package wust.slack

import slack.SlackUtil
import slack.models._
import slack.rtm.SlackRtmClient
import akka.actor.ActorSystem
import autowire._
import boopickle.Default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import wust.api._
import wust.ids._
import wust.graph._
import wust.graph._
import wust.framework._

import scala.util.{Success, Failure}
import scala.util.control.NonFatal

object Constants {
  //TODO
  val slackId = "wust-slack"
}

case class ExchangeMessage(title: String)

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(msg: ExchangeMessage): Result[Post]
}

class WustReceiver(client: WustClient) extends MessageReceiver {
  import cool.graph.cuid.Cuid

  def push(msg: ExchangeMessage) = {
    println(s"new message: msg")
    val id = PostId(Cuid.createCuid())
    val post = Post(id, msg.title)
    val containment = Containment(Constants.slackId, id)

    val changes = List(GraphChanges(addPosts = Set(post), addContainments = Set(containment)))
    client.api.changeGraph(changes).call().map { success =>
      if (success) Right(post)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {
  type Result[T] = Either[String, T]

  def run(config: WustConfig, slack: SlackClient): Future[Result[WustReceiver]] = {
    implicit val system = ActorSystem("wust")

    val location = s"ws://${config.host}:8080/ws"
    val handler = new ApiIncidentHandler {
      override def onConnect(isReconnect: Boolean): Unit = println(s"Connected to websocket")
      override def onEvents(events: Seq[ApiEvent]): Unit = {
        println(s"Got events: $events")
        val changes = events collect { case NewGraphChanges(changes) => changes }
        val posts = changes.flatMap(_.addPosts)
        posts.map(p => ExchangeMessage(p.title)).foreach { msg =>
          slack.send(msg).foreach { success =>
            println(s"Send message success: $success")
          }
        }
      }
    }
    val client = WustClient(location, handler)

    val res = for {
      loggedIn <- client.auth.login(config.user, config.password).call()
      if loggedIn
      changed <- client.api.changeGraph(List(GraphChanges(addPosts = Set(Post(Constants.slackId, "wust-slack"))))).call()
      if changed
      graph <- client.api.getGraph(Page.Root).call()
    } yield Right(new WustReceiver(client))

    res recover { case e =>
      client.stop()
      system.shutdown()
      Left(e.getMessage)
    }
  }
}


class SlackClient(client: SlackRtmClient) {

  def send(msg: ExchangeMessage): Future[Boolean] = {
    val channelId = client.state.getChannelIdForName("general").get //TODO
    val text = msg.title

    client
      .sendMessage(channelId, text)
      .map(_ => true)
      .recover { case NonFatal(_) => false }
  }

  def run(receiver: MessageReceiver) = {
    val selfId = client.state.self.id
    client.onEvent {
      case e: Message =>
        println(s"Got message from '${e.user}' in channel '${e.channel}': ${e.text}")

        def respond(msg: String) = client.sendMessage(e.channel, s"<@${e.user}>: $msg")

        val mentionedIds = SlackUtil.extractMentionedIds(e.text)
        if(mentionedIds.contains(selfId)) {
          val message = ExchangeMessage(e.text)
          receiver.push(message) foreach {
            case Left(error) => respond(s"Failed to sync with wust: $error")
            case Right(post) => respond(s"Created post: $post")
          }
        }


      case e => println(s"ignored event: $e")
    }
  }
}

object SlackClient {
  def apply(accessToken: String): SlackClient = {
    implicit val system = ActorSystem("slack")
    val client = SlackRtmClient(accessToken)
    new SlackClient(client)
  }
}

object App extends scala.App {
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
