package wust.gitter

import com.amatkivskiy.gitter.sdk.async.faye.client.{AsyncGitterFayeClient => GitterFayeClient}
import com.amatkivskiy.gitter.sdk.async.client.{AsyncGitterApiClient => GitterAsyncClient}
import com.amatkivskiy.gitter.sdk.async.faye.client.AsyncGitterFayeClientBuilder
import com.amatkivskiy.gitter.sdk.async.faye.interfaces.ConnectionListener
import com.amatkivskiy.gitter.sdk.async.faye.interfaces.DisconnectionListener
import com.amatkivskiy.gitter.sdk.async.faye.listeners.RoomMessagesChannel
import com.amatkivskiy.gitter.sdk.async.faye.model.MessageEvent
import com.amatkivskiy.gitter.sdk.model.response.message.MessageResponse
import wust.sdk._
import wust.api._
import wust.ids._
import wust.graph._
import mycelium.client.SendType
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import okhttp3.OkHttpClient
import retrofit.{Callback, RetrofitError}
import retrofit.client.Response
import cats.data.EitherT
import cats.implicits._
import monix.execution.Scheduler
import monix.reactive.Observable

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object Constants {
  //TODO
  val gitterId: NodeId = ???
}

//class GitterApiImpl(client: WustClient, server: ServerConfig, github: GitterConfig, redis: RedisClient)(implicit ec: ExecutionContext) extends PluginApi {
//  def connectUser(auth: Authentication.Token): Future[Option[String]] = {
//    client.auth.verifyToken(auth).map {
//      case Some(verifiedAuth) =>
//        scribe.info(s"User has valid auth: ${verifiedAuth.user.name}")
//        // Step 1: Add wustId -> wustToken
//        AuthClient.addWustToken(redis, verifiedAuth.user.id, verifiedAuth.token)
//
//        // Generate url called by client (e.g. WebApp)
//        AuthClient.generateAuthUrl(verifiedAuth.user.id, server, github)
//      case None =>
//        scribe.info(s"Invalid auth")
//        None
//    }
//  }
//
//  override def importContent(identifier: String): Future[Boolean] = {
//    Seeding
//  }
//}

case class ExchangeMessage(content: String)

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(msg: ExchangeMessage, author: UserId): Result[Node]
}

class WustReceiver(client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(msg: ExchangeMessage, author: UserId): Future[Either[String, Node]] = {
    println(s"new message: ${msg.content}")
    // TODO: author
    val post = Node.Content(NodeData.Markdown(msg.content))
    val connection = Edge.Parent(post.id, Constants.gitterId)

    val changes = List(GraphChanges(addNodes = Set(post), addEdges = Set(connection)))
    client.api.changeGraph(changes).map { success =>
      if (success) Right(post)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {
  type Result[T] = Either[String, T]

  val wustUser: UserId = "wust-gitter".asInstanceOf[UserId]

  def run(config: WustConfig, gitter: GitterClient)(
      implicit system: ActorSystem
  ): Future[Result[WustReceiver]] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(system.dispatcher)

    val location = s"ws://${config.host}:${config.port}/ws"
    val wustClient = WustClient(location)

    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = wustClient.observable.event
      .map(e => e.collect { case ev: ApiEvent.GraphContent => ev })
      .collect { case list if list.nonEmpty => list }

    graphEvents.foreach { events: Seq[ApiEvent.GraphContent] =>
      println(s"Got events in Gitter: $events")
      val changes = events collect { case ApiEvent.NewGraphChanges(changes) => changes }
      val posts = changes.flatMap(_.addNodes)
      posts.map(p => ExchangeMessage(p.data.str)).foreach { msg =>
        gitter.send(msg)
      }

    }

    val client = wustClient.sendWith(SendType.WhenConnected, 30 seconds)

    val res = for { // Assume thas user exists
//      _ <- valid(client.auth.register(config.user, config.password), "Cannot register")
      _ <- valid(client.auth.login(config.user, config.password).map {
        case AuthResult.Success => true
        case _                  => false
      }, "Cannot login")
      // TODO: author: wustUser
      changes = GraphChanges(
        addNodes = Set(Node.Content(Constants.gitterId, NodeData.PlainText("wust-gitter")))
      )
      _ <- valid(client.api.changeGraph(List(changes)), "cannot change graph")
      graph <- valid(client.api.getGraph(Page.empty))
    } yield new WustReceiver(client)

    res.value
  }

  private def valid(fut: Future[Boolean], errorMsg: String)(implicit ec: ExecutionContext) =
    EitherT(fut.map(Either.cond(_, (), errorMsg)))
  private def valid[T](fut: Future[T])(implicit ec: ExecutionContext) =
    EitherT(fut.map(Right(_): Either[String, T]))
}

class GitterClient(streamClient: GitterFayeClient, sendClient: GitterAsyncClient)(
    implicit ec: ExecutionContext,
    system: ActorSystem
) {

  // TODO: change this
  val roomId = "584862d0d73408ce4f3b747f"
  def send(msg: ExchangeMessage): Unit = {
    val text = msg.content

    sendClient.sendMessage(
      roomId,
      text,
      new Callback[MessageResponse]() {
        override def success(t: MessageResponse, response: Response): Unit = {
          println("Successfully send message")
        }
        override def failure(error: RetrofitError): Unit = {
          println(s"Error while send message: ${error.getKind}")
        }
      }
    )

  }

  def run(receiver: MessageReceiver): Unit = {
    val roomMessagesChannel: RoomMessagesChannel = new RoomMessagesChannel(roomId) {
      override def onMessage(channel: String, e: MessageEvent): Unit = {
        println(
          s"Got message from '${e.message.fromUser}' in channel '${channel}': ${e.message.text}"
        )

        val message = ExchangeMessage(e.message.text)
        receiver.push(message, WustReceiver.wustUser) foreach {
          case Left(error) => println(s"Failed to sync with wust: $error")
          case Right(post) => println(s"Created post: $post")
        }
      }
    }

    streamClient.connect(new ConnectionListener {
      override def onConnected(): Unit = {
        streamClient.subscribe(roomMessagesChannel)
      }
    })
  }
}

object GitterClient {
  def apply(
      accessToken: String
  )(implicit ec: ExecutionContext, system: ActorSystem): GitterClient = {

    val streamClient: GitterFayeClient = new AsyncGitterFayeClientBuilder()
      .withAccountToken(accessToken)
      .withOnDisconnected(new DisconnectionListener() {
        override def onDisconnected(): Unit = {
          println("Gitter client disconnected. Trying to reconnect...")
        }
      })
      .withOkHttpClient(new OkHttpClient())
      .build()

    val sendClient: GitterAsyncClient = new GitterAsyncClient.Builder()
      .withAccountToken(accessToken)
      .build()

    new GitterClient(streamClient, sendClient)
  }
}

object App extends scala.App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem("gitter")

  Config.load match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      val client = GitterClient(config.accessToken)
      WustReceiver.run(config.wustServer, client).foreach {
        case Right(receiver) => client.run(receiver)
        case Left(err)       => println(s"Cannot connect to Wust: $err")
      }
  }
}
