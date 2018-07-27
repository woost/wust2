package wust.slack

import covenant.http._
import ByteBufferImplicits._
import sloth._
import java.nio.ByteBuffer

import boopickle.Default._
import chameleon.ext.boopickle._
import wust.sdk._
import wust.api._
import wust.ids._
import wust.graph._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange}
import mycelium.client.SendType
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import cats.data.EitherT

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import monix.execution.Scheduler
import monix.reactive.Observable
import cats.implicits._
import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.decoding.ConfiguredDecoder
import monix.reactive.subjects.ConcurrentSubject

import scala.util.{Failure, Success, Try}
import slack.SlackUtil
import slack.api.SlackApiClient
import slack.models._
//import wust.test.events._
import slack.rtm.SlackRtmClient

object Constants {
  //TODO
  val wustUser = AuthUser.Assumed(UserId.fresh, NodeId.fresh)
  val slackNode = Node.Content(NodeData.PlainText("wust-slack"))
  val slackId: NodeId = slackNode.id
}


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

object AppServer {
  import akka.http.scaladsl.server.RouteResult._
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.Http
//  import io.circe.generic.auto._ // TODO: extras does not seem to work with heiko seeberger

//  implicit val genericConfiguration: Configuration = Configuration(transformMemberNames = identity, transformConstructorNames = _.toLowerCase, useDefaults = true, discriminator = Some("event"))
//  import io.circe.generic.extras.auto._ // TODO: extras does not seem to work with heiko seeberger
//  import cats.implicits._
//  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._


  implicit def StringToEpochMilli(s: String): EpochMilli = EpochMilli.from(s)

  def run(config: DefaultConfig, wustReceiver: WustReceiver, oAuthClient: OAuthClient)(
    implicit system: ActorSystem, scheduler: Scheduler
  ): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val apiRouter = Router[ByteBuffer, Future]
      .route[PluginApi](new SlackApiImpl(wustReceiver.client, oAuthClient))

    val corsSettings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange(config.server.allowedOrigins.map(HttpOrigin(_)): _*)
    )

    val tokenObserver = ConcurrentSubject.publish[AccessToken]
    tokenObserver.foreach{ t =>
      scribe.info(s"persisting token: $t")
      //          // get user information
      //          Platform(token).users.getAuth.exec[cats.Id, HttpResponse[String]]() match {
      //            case Right(r) =>
      //              val wustUserId = oAuthRequests(state)
      //              val platformUserId = r.result.id
      //              // Platform data
      //              PersistAdapter.addPlatformToken(platformUserId, token.get)
      //              PersistAdapter.addWustUser(platformUserId, wustUserId)
      //              // Wust data
      //              PersistAdapter.addPlatformUser(wustUserId, platformUserId)
      //            //                  PersistAdapter.oAuthRequests.remove(state)
      //            case Left(e) => println(s"Could not authenticate with OAuthToken: ${e.getMessage}")
      //          }
    }

    val route = {
      pathPrefix("api") {
        cors(corsSettings) {
          AkkaHttpRoute.fromFutureRouter(apiRouter)
        }
      } ~ path(config.server.webhookPath) {
        post {
          decodeRequest {
            //            {
            //              "token":"VaYExAdPeyQtPHe8zwcg72n0",
            //              "team_id":"T70KGCA9J",
            //              "api_app_id":"ABZ223TEK",
            //              "event":
            //                {
            //                  "type":"message",
            //                  "user":"UBMG643BJ",
            //                  "text":"rand",
            //                  "client_msg_id":"f9ae9f61-fd6e-4f2d-84a3-62ee2429710e",
            //                  "ts":"1532687658.000118",
            //                  "channel":"C70HT00EN",
            //                  "event_ts":"1532687658.000118",
            //                  "channel_type":"channel"
            //                },
            //              "type":"event_callback",
            //              "authed_teams":["T70KGCA9J"],
            //              "event_id":"EvBZDKMHNJ",
            //              "event_time":1532687658
            //            }

//            val incomingEvents = entity(as[SlackEvent]) { event =>
//              event match {
//                case e: Hello => scribe.info("hello")
//                //                case e: Message => scribe.info(s"message: ${e.toString}")
//                case unknown => scribe.info(s"unmatched SlackEvent: ${unknown.toString}")
//              }
//              complete(StatusCodes.OK)
//            }

            val incomingEvents = entity(as[SlackEventStructure]) { eventStructure =>
              eventStructure.event match {
                case e: Hello => scribe.info("hello")
                case e: Message => scribe.info(s"message: ${e.toString}")
                case unknown => scribe.info(s"unmatched SlackEvent: ${unknown.toString}")
              }
              complete(StatusCodes.OK)
            }

            val challengeEvent = entity(as[EventServerChallenge]) { eventChallenge =>
              complete {
                HttpResponse(
                  status = StatusCodes.OK,
                  headers = Nil,
                  entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, eventChallenge.challenge)
                )
              }
            }

            //            incomingEvents orElse challengeEvent
            incomingEvents

          }
        }
      } ~ {
        oAuthClient.route(tokenObserver)
      }
    }

    Http().bindAndHandle(route, interface = config.server.host, port = config.server.port).onComplete {
      case Success(binding) =>
        val separator = "\n############################################################"
        val readyMsg = s"\n##### Slack App Server online at ${binding.localAddress} #####"
        scribe.info(s"$separator$readyMsg$separator")
      case Failure(err) => scribe.error(s"Cannot start Slack App Server: $err")
    }
  }
}

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(graphChanges: List[GraphChanges]): Result[List[GraphChanges]]
}

class WustReceiver(val client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(graphChanges: List[GraphChanges]): Future[Either[String, List[GraphChanges]]] = {
    scribe.info(s"pushing new graph change: $graphChanges")
    //TODO use onBehalf with different token
    // client.api.changeGraph(graphChanges, onBehalf = token).map{ success =>
    client.api.changeGraph(graphChanges).map { success =>
      if (success) Right(graphChanges)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {

  object GraphTransition {
    def empty: GraphTransition =
      new GraphTransition(Graph.empty, Seq.empty[GraphChanges], Graph.empty)
  }
  case class GraphTransition(prevGraph: Graph, changes: Seq[GraphChanges], resGraph: Graph)

  def run(config: WustConfig, slackClient: SlackClient)(implicit system: ActorSystem): WustReceiver = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(system.dispatcher)

    //TODO: service discovery or some better configuration for the wust host
    val protocol = if (config.port == 443) "wss" else "ws"
    val location = s"$protocol://core.${config.host}:${config.port}/ws"
    val wustClient = WustClient(location)
    val client = wustClient.sendWith(SendType.WhenConnected, 30 seconds)
    val highPriorityClient = wustClient.sendWith(SendType.WhenConnected.highPriority, 30 seconds)

    highPriorityClient.auth.assumeLogin(Constants.wustUser)
    highPriorityClient.auth.register(config.user, config.password)
    wustClient.observable.connected.foreach { _ =>
      highPriorityClient.auth.login(config.user, config.password)
    }

    //    val changes = GraphChanges(addPosts = Set(Post(Constants.slackId, PostData.Text("wust-slack"), Constants.wustUser.id)))
    // TODO: author
    val changes = GraphChanges(
      addNodes = Set(Constants.slackNode: Node)
    )
    client.api.changeGraph(List(changes))

    println("Running WustReceiver")

    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = wustClient.observable.event
      .map(e => {
        println(s"triggering collect on $e");
        e.collect { case ev: ApiEvent.GraphContent => println("received api event"); ev }
      })
      .collect { case list if list.nonEmpty => println("api event non-empty"); list }

    val graphObs: Observable[GraphTransition] = graphEvents.scan(GraphTransition.empty) {
      (prevTrans, events) =>
        println(s"Got events: $events")
        val changes = events collect { case ApiEvent.NewGraphChanges(_changes) => _changes }
        val nextGraph = events.foldLeft(prevTrans.resGraph)(EventUpdate.applyEventOnGraph)
        GraphTransition(prevTrans.resGraph, changes, nextGraph)
    }

//    val slackApiCalls: Observable[Seq[SlackCall]] = graphObs.map { graphTransition =>
//      createCalls(slackClient, graphTransition)
//    }

    new WustReceiver(client)
  }


  private def validRecover[T]: PartialFunction[Throwable, Either[String, T]] = {
    case NonFatal(t) => Left(s"Exception was thrown: $t")
  }
  private def valid(fut: Future[Boolean], errorMsg: String)(implicit ec: ExecutionContext) =
    EitherT(fut.map(Either.cond(_, (), errorMsg)).recover(validRecover))
  private def valid[T](fut: Future[T])(implicit ec: ExecutionContext) =
    EitherT(fut.map(Right(_): Either[String, T]).recover(validRecover))
}

object SlackClient {
  def apply(accessToken: String)(implicit ec: ExecutionContext): SlackClient = {
    new SlackClient(SlackApiClient(accessToken))
  }
}

class SlackClient(client: SlackApiClient)(implicit ec: ExecutionContext) {

  case class Error(desc: String)

  def run(receiver: MessageReceiver): Unit = {
    // TODO: Get events from slack
    //    private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
    //    private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  }
}

object App extends scala.App {

  import monix.execution.Scheduler.Implicits.global

  implicit val system: ActorSystem = ActorSystem("slack")

  Config.load("wust.slack") match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      val oAuthClient = OAuthClient.create(config.oauth, config.server)
      val slackClient = SlackClient("bla")
      val slackEventReceiver = WustReceiver.run(config.wust, slackClient)
      //      val client = SlackClient(config.oAuthConfig.accessToken.get)
      AppServer.run(config, slackEventReceiver, oAuthClient)
  }
}
