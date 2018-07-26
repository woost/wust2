package wust.slack

import java.nio.ByteBuffer

import slack.SlackUtil
import slack.models._
import slack.rtm.SlackRtmClient
import wust.sdk._
import wust.api._
import wust.ids._
import wust.graph._
import mycelium.client.SendType
import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange}
import akka.stream.ActorMaterializer
import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import monix.reactive.Observable
import sloth.Router

object Constants {
  //TODO
  val slackId: NodeId = ???
}

case class ExchangeMessage(content: String)

class SlackApiImpl(client: WustClient, oAuthClient: OAuthClient)(
  implicit ec: ExecutionContext
) extends PluginApi {
  def connectUser(auth: Authentication.Token): Future[Option[String]] = {
    client.auth.verifyToken(auth).map {
      case Some(verifiedAuth) =>
        scribe.info(s"User has valid auth: ${verifiedAuth.user.name}")
        oAuthClient.authorizeUrl(verifiedAuth.user.id).map(_.toString())
      case None =>
        scribe.info(s"Invalid auth")
        None
    }
  }

  override def importContent(identifier: String): Future[Boolean] = {
    // TODO: Seeding
    Future.successful(true)
  }
}

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(msg: ExchangeMessage, author: UserId): Result[Node]
}

class WustReceiver(client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(msg: ExchangeMessage, author: UserId): Future[Either[String, Node]] = {
    println(s"new message: msg")
    // TODO: author
    val post = Node.Content(NodeData.PlainText(msg.content))
    val connection = Edge.Parent(post.id, Constants.slackId)

    val changes = List(GraphChanges(addNodes = Set(post), addEdges = Set(connection)))
    client.api.changeGraph(changes).map { success =>
      if (success) Right(post)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {
  type Result[T] = Either[String, T]

  val wustUser = ("wust-slack").asInstanceOf[UserId]

  def run(
           config: WustConfig,
           slackClient: SlackClient,
           oAuthClient: OAuthClient
  )(implicit ec: ExecutionContext, system: ActorSystem): Future[Result[WustReceiver]] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(system.dispatcher)

    val location = s"ws://${config.host}:${config.port}/ws"
    val wustClient = WustClient(location)

    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = wustClient.observable.event
      .map(e => e.collect { case ev: ApiEvent.GraphContent => ev })
      .collect { case list if list.nonEmpty => list }

    graphEvents.foreach { events: Seq[ApiEvent.GraphContent] =>
      println(s"Got events in Slack: $events")
      val changes = events collect { case ApiEvent.NewGraphChanges(changes) => changes }
      val posts = changes.flatMap(_.addNodes)
      posts.map(p => ExchangeMessage(p.data.str)).foreach { msg =>
        slackClient.send(msg).foreach { success =>
          println(s"Send message success: $success")
        }
      }
    }

    val client = wustClient.sendWith(SendType.NowOrFail, 30 seconds)

    val res = for {
      loggedIn <- client.auth.login(config.user, config.password)
      if loggedIn == AuthResult.Success
      // TODO: author
      changed <- client.api.changeGraph(
        List(
          GraphChanges(
            addNodes = Set(Node.Content(Constants.slackId, NodeData.PlainText("wust-slack")))
          )
        )
      )
      if changed
      graph <- client.api.getGraph(Page.empty)
    } yield Right(new WustReceiver(client))

    res recover {
      case e =>
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
        if (mentionedIds.contains(selfId)) {
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

object App {

  def run(config: sdk.Config, wustReceiver: WustReceiver, oAuthClient: OAuthClient)(
    implicit system: ActorSystem, sheduler: Scheduler
  ): Unit = {
    import io.circe.generic.auto._ // TODO: extras does not seem to work with heiko seeberger
    import cats.implicits._

    val apiRouter = Router[ByteBuffer, Future]
      .route[PluginApi](new SlackApiImpl(wustReceiver.client, oAuthClient))

    val corsSettings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange(config.server.allowedOrigins.map(HttpOrigin(_)): _*)
    )
  }

}

object SlackClient {
  def apply(
      accessToken: String
  )(implicit ec: ExecutionContext, actorSystem: ActorSystem): SlackClient = {
    val client = SlackRtmClient(accessToken)
    new SlackClient(client)
  }
}

object App extends scala.App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem("slack")

  Config.load("wust.slack") match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      // TODO: get token for user or get a new one
      val oAuthClient = OAuthClient.create(config.oauth, config.server)
      //      val client = SlackClient(config.oAuthConfig.accessToken.get)
      val slackClient = SlackClient("bla")
      WustReceiver.run(config.wust, slackClient, oAuthClient).foreach {
        case Right(receiver) => slackClient.run(receiver)
        case Left(err)       => println(s"Cannot connect to Wust: $err")
      }
  }
}
