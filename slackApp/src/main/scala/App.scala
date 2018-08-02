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
import cats.data.{EitherT, OptionT}

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
import wust.slack.Data.{Message_Mapping, User_Mapping, WustUserData}
//import wust.test.events._
import slack.rtm.SlackRtmClient

object Constants {
  //TODO
  val wustUser = AuthUser.Assumed(UserId.fresh, NodeId.fresh)
  val slackNode = Node.Content(NodeData.PlainText("wust-slack"))
  val slackId: NodeId = slackNode.id
}


class SlackApiImpl(client: WustClient, oAuthClient: OAuthClient, persistenceAdapter: PersistenceAdapter)(
  implicit ec: ExecutionContext
) extends PluginApi {
  def connectUser(auth: Authentication.Token): Future[Option[String]] = {
    client.auth.verifyToken(auth).map {
      case Some(verifiedAuth) =>
        scribe.info(s"User has valid auth: ${verifiedAuth.user.name}")
        oAuthClient.authorizeUrl(
          verifiedAuth,
          List("users:read", "mpim:history", "mpim:read", "mpim:write", "im:history", "im:read", "im:write", "groups:history", "groups:read", "groups:write", "channels:history", "channels:read", "channels:write")
        ).map(_.toString())
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

  def run(config: Config, wustReceiver: WustReceiver, slackClient: SlackClient, oAuthClient: OAuthClient, persistenceAdapter: PersistenceAdapter)(
    implicit system: ActorSystem, scheduler: Scheduler
  ): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val apiRouter = Router[ByteBuffer, Future]
      .route[PluginApi](new SlackApiImpl(wustReceiver.client, oAuthClient, persistenceAdapter))

    val corsSettings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange(config.server.allowedOrigins.map(HttpOrigin(_)): _*)
    )

    val tokenObserver = ConcurrentSubject.publish[AuthenticationData]
    tokenObserver.foreach{ authData =>

      val userOAuthToken = authData.platformAuthToken
      val slackUser = SlackApiClient(userOAuthToken.accessToken.toString).testAuth()
      slackUser.flatMap { slackAuthId =>
        //        persistenceAdapter.storeUserAuthData(User_Mapping(slackAuthId.user_id, authData.wustAuthData.user.id, userOAuthToken, authData.wustAuthData))
        persistenceAdapter.storeOrUpdateUserAuthData(User_Mapping(slackAuthId.user_id, authData.wustAuthData.user.id, Some(userOAuthToken.accessToken), authData.wustAuthData.token))
      }.onComplete {
        case Success(p) => if(p) scribe.info("persisted oauth token") else scribe.error("could not persist oauth token")
        case Failure(ex) => scribe.error("failed to persist oauth token: ", ex)
      }
      scribe.info(s"persisting token: $authData")
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
      //            case Left(e) => scribe.info(s"Could not authenticate with OAuthToken: ${e.getMessage}")
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
            val incomingEvents = entity(as[SlackEventStructure]) { eventStructure =>
              eventStructure.event match {
                case e: Hello =>
                  scribe.info("hello")

                case e: Message =>
                  scribe.info(s"message: ${e.toString}")

                  val graphChanges: OptionT[Future, (NodeId, GraphChanges, Authentication.Token)] = for {
                    wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(e.user, wustReceiver.client))
                    wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNode(e.channel))
                  } yield {
                    val changes: (NodeId, GraphChanges) = EventMapper.createMessageInWust(
                      NodeData.PlainText(e.text),
                      wustUserData.wustUserId,
                      e.ts,
                      wustChannelNodeId
                    )
                    (changes._1, changes._2, wustUserData.wustUserToken)
                  }

                  val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not create message").flatMapF { changes =>
                    val res = wustReceiver.push(List(changes._2), Some(changes._3))
                    res.foreach {
                      case Right(_) => persistenceAdapter.storeMessageMapping(Message_Mapping(e.channel, e.ts, changes._1))
                      case _ => scribe.error(s"Could not apply changes to wust: $changes")
                    }
                    res
                  }

                  applyChanges.value

                case e: MessageChanged =>
                  scribe.info(s"message: ${e.toString}")

                  val graphChanges: OptionT[Future, (NodeId, GraphChanges, Authentication.Token)] = for {
                    nodeId <- OptionT[Future, NodeId](persistenceAdapter.getMessageNodeByChannelAndTimestamp(e.channel, e.previous_message.ts))
                    wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(e.message.user, wustReceiver.client))
                    node <- OptionT[Future, Node.Content](
                      wustReceiver.client.api.getNode(nodeId, wustUserData.wustUserToken).map {
                        case Some(n: Node.Content) => Some(n)
                        case _ =>
                          scribe.error(s"Failed getting slack node in wust: ${e.previous_message}")
                          None
                      })
                    wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNode(e.channel))
                  } yield {
                    (node.id, EventMapper.editMessageContentInWust(
                      node,
                      NodeData.PlainText(e.message.text)
                    ), wustUserData.wustUserToken)
                  }

                  val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not change message").flatMapF { changes =>
                    val res = wustReceiver.push(List(changes._2), Some(changes._3))
                    res.foreach {
                      case Right(_) => persistenceAdapter.storeMessageMapping(Message_Mapping(e.channel, e.ts, changes._1))
                      case _ => scribe.error(s"Could not apply changes to wust: $changes")
                    }
                    res
                  }

                  applyChanges.value

                case e: MessageDeleted =>
                  scribe.info(s"message: ${e.toString}")
                  val graphChanges: OptionT[Future, GraphChanges] = for {
                    nodeId <- OptionT(persistenceAdapter.getMessageNodeByChannelAndTimestamp(e.channel, e.ts))
                    wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNode(e.channel))
                  } yield {
                    EventMapper.deleteMessageInWust(
                      nodeId,
                      wustChannelNodeId
                    )
                  }

                  val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not change message").flatMapF { changes =>
                    wustReceiver.push(List(changes), None)
                  }

                  applyChanges.value

                case e: BotMessage =>
                  scribe.info(s"message: ${e.toString}")
                case e: MessageWithSubtype =>
                  scribe.info(s"message: ${e.toString}")

                case e: ReactionAdded =>
                  scribe.info(s"reaction: ${e.toString}")
                case e: ReactionRemoved =>
                  scribe.info(s"reaction: ${e.toString}")

                case e: UserTyping =>
                  scribe.info(s"user typing: ${e.toString}")

                case e: ChannelMarked =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelCreated =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelJoined =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelLeft =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelDeleted =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelRename =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelArchive =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelUnarchive =>
                  scribe.info(s"channel: ${e.toString}")
                case e: ChannelHistoryChanged =>
                  scribe.info(s"channel: ${e.toString}")

                case e: ImCreated =>
                  scribe.info(s"im: ${e.toString}")
                case e: ImOpened =>
                  scribe.info(s"im: ${e.toString}")
                case e: ImClose =>
                  scribe.info(s"im: ${e.toString}")
                case e: ImMarked =>
                  scribe.info(s"im: ${e.toString}")
                case e: ImHistoryChanged =>
                  scribe.info(s"im: ${e.toString}")

                case e: MpImJoined =>
                  scribe.info(s"mp Im: ${e.toString}")
                case e: MpImOpen =>
                  scribe.info(s"mp Im: ${e.toString}")
                case e: MpImClose =>
                  scribe.info(s"mp Im: ${e.toString}")

                case e: GroupJoined =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupLeft =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupOpen =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupClose =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupArchive =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupUnarchive =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupRename =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupMarked =>
                  scribe.info(s"group: ${e.toString}")
                case e: GroupHistoryChanged =>
                  scribe.info(s"group: ${e.toString}")

                case e: FileCreated =>
                  scribe.info(s"file: ${e.toString}")
                case e: FileShared =>
                  scribe.info(s"file: ${e.toString}")
                case e: FileUnshared =>
                  scribe.info(s"file: ${e.toString}")
                case e: FilePublic =>
                  scribe.info(s"file: ${e.toString}")
                case e: FilePrivate =>
                  scribe.info(s"file: ${e.toString}")
                case e: FileChange =>
                  scribe.info(s"file: ${e.toString}")
                case e: FileDeleted =>
                  scribe.info(s"file: ${e.toString}")
                case e: FileCommentAdded =>
                  scribe.info(s"file: ${e.toString}")
                case e: FileCommentEdited =>
                  scribe.info(s"file: ${e.toString}")
                case e: FileCommentDeleted =>
                  scribe.info(s"file: ${e.toString}")

                case e: PinAdded =>
                  scribe.info(s"pin: ${e.toString}")
                case e: PinRemoved =>
                  scribe.info(s"pin: ${e.toString}")

                case e: PresenceChange =>
                  scribe.info(s"presence: ${e.toString}")
                case e: ManualPresenceChange =>
                  scribe.info(s"presence: ${e.toString}")

                case e: PrefChange =>
                  scribe.info(s"pref: ${e.toString}")

                case e: UserChange =>
                  scribe.info(s"user: ${e.toString}")

                case e: TeamJoin =>
                  scribe.info(s"team: ${e.toString}")

                case e: StarAdded =>
                  scribe.info(s"star: ${e.toString}")
                case e: StarRemoved =>
                  scribe.info(s"star: ${e.toString}")

                case e: EmojiChanged =>
                  scribe.info(s"emoji: ${e.toString}")

                case e: CommandsChanged =>
                  scribe.info(s"commands: ${e.toString}")

                case e: TeamPlanChanged =>
                  scribe.info(s"team: ${e.toString}")
                case e: TeamPrefChanged =>
                  scribe.info(s"team: ${e.toString}")
                case e: TeamRename =>
                  scribe.info(s"team: ${e.toString}")
                case e: TeamDomainChange =>
                  scribe.info(s"team: ${e.toString}")

                case e: BotAdded =>
                  scribe.info(s"bot: ${e.toString}")
                case e: BotChanged =>
                  scribe.info(s"bot: ${e.toString}")

                case e: AccountsChanged =>
                  scribe.info(s"account: ${e.toString}")

                case e: TeamMigrationStarted =>
                  scribe.info(s"team: ${e.toString}")

                case e: ReconnectUrl =>
                  scribe.info(s"reconnect: ${e.toString}")

                case e: Reply =>
                  scribe.info(s"reply: ${e.toString}")

                case e: AppsChanged =>
                  scribe.info(s"apps: ${e.toString}")
                case e: AppsUninstalled =>
                  scribe.info(s"apps: ${e.toString}")
                case e: AppsInstalled =>
                  scribe.info(s"apps: ${e.toString}")

                case e: DesktopNotification =>
                  scribe.info(s"desktop: ${e.toString}")

                case e: DndUpdatedUser =>
                  scribe.info(s"dnd: ${e.toString}")

                case e: MemberJoined =>
                  scribe.info(s"member: ${e.toString}")

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
//            incomingEvents
            challengeEvent

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

  def push(graphChanges: List[GraphChanges], auth: Option[Authentication.Token]): Result[List[GraphChanges]]
}

class WustReceiver(val client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(graphChanges: List[GraphChanges], auth: Option[Authentication.Token]): Result[List[GraphChanges]] = {
    scribe.info(s"pushing new graph change: $graphChanges")

    (auth match {
      case None => client.api.changeGraph(graphChanges)
      case Some(t) => client.api.changeGraph(graphChanges, t)
    }).map { success =>
      if (success) Right(graphChanges)
      else Left(s"Failed to apply GraphChanges: $graphChanges")
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

    scribe.info("Running WustReceiver")

    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = wustClient.observable.event
      .map(e => {
        scribe.info(s"triggering collect on $e");
        e.collect { case ev: ApiEvent.GraphContent => scribe.info("received api event"); ev }
      })
      .collect { case list if list.nonEmpty => scribe.info("api event non-empty"); list }

    val graphObs: Observable[GraphTransition] = graphEvents.scan(GraphTransition.empty) {
      (prevTrans, events) =>
        scribe.info(s"Got events: $events")
        val changes = events collect { case ApiEvent.NewGraphChanges(_changes) => _changes }
        val nextGraph = events.foldLeft(prevTrans.resGraph)(EventUpdate.applyEventOnGraph)
        GraphTransition(prevTrans.resGraph, changes, nextGraph)
    }

//    val slackApiCalls: Observable[Seq[SlackCall]] = graphObs.map { graphTransition =>
//      createCalls(slackClient, graphTransition)
//    }

    graphObs.foreach { graphTransition =>

      scribe.info(s"Received GraphChanges: ${graphTransition.changes}")

    }

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

class SlackClient(val client: SlackApiClient)(implicit ec: ExecutionContext) {

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

  Config.load match {
    case Left(err) => scribe.info(s"Cannot load config: $err")
    case Right(config) =>
      val oAuthClient = OAuthClient(config.oauth, config.server)
      val slackPersistenceAdapter = PostgresAdapter(config.postgres)
      val slackClient = SlackClient(config.slack.token)
      val slackEventReceiver = WustReceiver.run(config.wust, slackClient)
      //      val client = SlackClient(config.oAuthConfig.accessToken.get)
      AppServer.run(config, slackEventReceiver, slackClient, oAuthClient, slackPersistenceAdapter)
  }
}
