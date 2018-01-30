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
import mycelium.client.IncidentHandler
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import okhttp3.OkHttpClient
import retrofit.{Callback, RetrofitError}
import retrofit.client.Response

import scala.concurrent.{ExecutionContext, Future}

object Constants {
  //TODO
  val gitterId = "wust-gitter"
}

case class ExchangeMessage(content: String)

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(msg: ExchangeMessage, author: UserId): Result[Post]
}

class WustReceiver(client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(msg: ExchangeMessage, author: UserId) = {
    println(s"new message: ${msg.content}")
    val post = Post(PostId.fresh, msg.content, author)
    val connection = Connection(post.id, Label.parent, Constants.gitterId)

    val changes = List(GraphChanges(addPosts = Set(post), addConnections = Set(connection)))
    client.api.changeGraph(changes).map { success =>
      if (success) Right(post)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {
  type Result[T] = Either[String, T]

  val wustUser = UserId("wust-gitter")

  def run(config: WustConfig, gitter: GitterClient): Future[Result[WustReceiver]] = {
    implicit val system = ActorSystem("wust")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val location = s"ws://${config.host}:8080/ws"
    val handler = new IncidentHandler[ApiEvent] {
      override def onConnect(isReconnect: Boolean): Unit = println(s"Connected to websocket")
      override def onEvents(events: Seq[ApiEvent]): Unit = {
        println(s"Got events: $events")
        val changes = events collect { case ApiEvent.NewGraphChanges(changes) => changes }
        val posts = changes.flatMap(_.addPosts)
        posts.map(p => ExchangeMessage(p.content)).foreach { msg =>
          gitter.send(msg)
        }
      }
    }
    val client = WustClient(location, handler)

    // Assume that user exists
    val res = for {
      loggedIn <- client.auth.login(config.user, config.password)
      if loggedIn
      changed <- client.api.changeGraph(List(GraphChanges(addPosts = Set(Post(Constants.gitterId, "wust-gitter", wustUser)))))
      if changed
      graph <- client.api.getGraph(Page.Root)
    } yield Right(new WustReceiver(client))

    res recover { case e =>
      system.terminate()
      Left(e.getMessage)
    }
  }
}

class GitterClient(streamClient: GitterFayeClient, sendClient: GitterAsyncClient)(implicit ec: ExecutionContext) {

  val roomId = "5a2c177dd73408ce4f828d9d"
  def send(msg: ExchangeMessage): Unit = {
    val text = msg.content

    sendClient.sendMessage(roomId, text, new Callback[MessageResponse]() {
      override def success(t: MessageResponse, response: Response): Unit = {
        println("Successfully send message")
      }
      override def failure(error: RetrofitError): Unit = {
        println(s"Error while send message: ${error.getKind}")
      }
    })

  }

  def run(receiver: MessageReceiver): Unit = {
    val roomMessagesChannel: RoomMessagesChannel = new RoomMessagesChannel(roomId) {
      override def onMessage(channel: String, e: MessageEvent): Unit = {
        println(s"Got message from '${e.message.fromUser}' in channel '${channel}': ${e.message.text}")

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
  def apply(accessToken: String)(implicit ec: ExecutionContext): GitterClient = {
    implicit val system = ActorSystem("gitter")

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

  Config.load match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      val client = GitterClient(config.accessToken)
      WustReceiver.run(config.wust, client).foreach {
        case Right(receiver) => client.run(receiver)
        case Left(err) => println(s"Cannot connect to Wust: $err")
      }
  }
}
