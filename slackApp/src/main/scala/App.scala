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
import wust.api.ApiEvent.NewGraphChanges
import wust.slack.Data._
//import wust.test.events._
import slack.rtm.SlackRtmClient

object Constants {
  //TODO
//  val wustUser = AuthUser.Assumed(UserId.fresh, NodeId.fresh)
//  val wustUser = AuthUser.Assumed(UserId(Cuid.fromCuidString("5R1xejdFpxQiauAZtMVqpS")): UserId, NodeId(Cuid.fromCuidString("5R1xejdFpxQiauAZtMVqpS")))
  val wustUser = AuthUser.Assumed(UserId.fromBase58String("5R1xejdFpxQiauAZtMVqpS"), NodeId.fromBase58String("5R1xejdFpxQiauAZtMVqpS"))
//  val slackNode = Node.Content(NodeData.Markdown("wust-slack"))
  val slackNode = Node.Content(NodeId.fromBase58String("5R28qFeQj1Ny6tM9b7BXis"), NodeData.Markdown("wust-slack"), NodeMeta(NodeAccess.ReadWrite))
  val slackNodeId: NodeId = slackNode.id
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
          List(
//            "admin",
            // "auditlogs:read",
//            "bot",
            "channels:history", "channels:read", "channels:write",
            "chat:write:bot", "chat:write:user",
            // "client",
//            "commands",
            "dnd:read", "dnd:write",
            "emoji:read",
            "files:read", "files:write:user",
            "groups:history", "groups:read", "groups:write",
            // "identify", "identity.avatar", "identity.basic", "identity.email", "identity.team",
            "im:history", "im:read", "im:write",
//            "incoming-webhook",
            "links:read", "links:write",
            "mpim:history", "mpim:read", "mpim:write",
            "pins:read", "pins:write",
//            "post",
            "reactions:read", "reactions:write",
            "reminders:read", "reminders:write",
            "search:read",
            "stars:read", "stars:write",
            "team:read",
            "usergroups:read", "usergroups:write",
            "users.profile:read", "users.profile:write", "users:read", "users:read.email", "users:write",

            // Workspace Tokens
//            "channels:history", "channels:read", "channels:write",
//            "chat:write",
//            "commands",
//            "conversations:history", "conversations:read", "conversations:write",
//            "dnd:read", "dnd:write:user",
//            "emoji:read",
//            "files:read", "files:write",
//            "groups:history", "groups:read", "groups:write",
//            "identity.avatar:read:user", "identity.email:read:user", "identity.team:read:user", "identity:read:user",
//            "im:history", "im:read", "im:write",
//            "links:read", "links:write",
//            "mpim:history", "mpim:read", "mpim:write",
//            "pins:read", "pins:write",
//            "reactions:read", "reactions:write",
//            "reminders:read:user", "reminders:write:user",
//            "team:read",
//            "usergroups:read", "usergroups:write",
//            "users:read", "users:read.email", "users.profile:read", "users.profile:write", "users.profile:write:user",
          )
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

  def toEpochMilli(str: String) = EpochMilli(((str.toDouble) * 1000).toLong)

  def run(config: Config, wustReceiver: WustReceiver, slackClient: SlackClient, oAuthClient: OAuthClient, persistenceAdapter: PersistenceAdapter)(
    implicit system: ActorSystem, scheduler: Scheduler
  ): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    scribe.info(s"slackNode: ${Constants.slackNode.id.toString}")
    val apiRouter = Router[ByteBuffer, Future]
      .route[PluginApi](new SlackApiImpl(wustReceiver.client, oAuthClient, persistenceAdapter))

    val corsSettings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange(config.server.allowedOrigins.map(HttpOrigin(_)): _*)
    )

     // TODO: author
    val changes = GraphChanges(
      addNodes = Set(Constants.slackNode),
    )
    wustReceiver.push(List(changes), None)

    val tokenObserver = ConcurrentSubject.publish[AuthenticationData]
    tokenObserver.foreach{ authData =>

      scribe.info(s"received oauth token: $authData")
      val userOAuthToken = authData.platformAuthToken
      val slackUser = SlackApiClient(userOAuthToken.accessToken.toString).testAuth()
      scribe.info(s"slackTestAuth: $slackUser")
      slackUser.flatMap { slackAuthId =>
        scribe.info(s"persisting token: $authData")

        scribe.info(s"slackTestAuth: $slackAuthId")
        //        persistenceAdapter.storeUserAuthData(User_Mapping(slackAuthId.user_id, authData.wustAuthData.user.id, userOAuthToken, authData.wustAuthData))
        persistenceAdapter.storeOrUpdateUserAuthData(User_Mapping(slackAuthId.user_id, authData.wustAuthData.user.id, Some(userOAuthToken.accessToken), authData.wustAuthData.token))
      }.onComplete {
        case Success(p) => if(p) scribe.info("persisted oauth token") else scribe.error("could not persist oauth token")
        case Failure(ex) => scribe.error("failed to persist oauth token: ", ex)
      }
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

//                  val graphChanges: OptionT[Future, (NodeId, GraphChanges, WustUserData)] = for {
//                    wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(e.user, wustReceiver.client))
//                    wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getOrCreateChannelNode(e.channel, Constants.slackNode.id, wustReceiver, slackClient.client))
//                  } yield {
//                    val changes: (NodeId, GraphChanges) = EventMapper.createMessageInWust(
//                      NodeData.Markdown(e.text),
//                      wustUserData.wustUserId,
//                      toEpochMilli(e.ts),
//                      wustChannelNodeId
//                    )
//                    (changes._1, changes._2, wustUserData)
//                  }
//
//                  val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not create message").flatMapF { changes =>
//                    val res = wustReceiver.push(List(changes._2), Some(changes._3))
//                    res.foreach {
//                      case Right(_) => persistenceAdapter.storeMessageMapping(Message_Mapping(e.channel, e.ts, changes._1))
//                      case _ => scribe.error(s"Could not apply changes to wust: $changes")
//                    }
//                    res
//                  }
//
//                  applyChanges.value.onComplete {
//                    case Success(request) =>
//                    case Failure(ex) => scribe.error("Error creating message: ", ex)
//                  }
//
//                  applyChanges.value

                case e: MessageChanged =>
                  scribe.info(s"message: ${e.toString}")

//                  val graphChanges: OptionT[Future, (NodeId, GraphChanges, WustUserData)] = for {
//                    nodeId <- OptionT[Future, NodeId](persistenceAdapter.getMessageNodeByChannelAndTimestamp(e.channel, e.previous_message.ts))
//                    wustUserData <- OptionT[Future, WustUserData](persistenceAdapter.getOrCreateWustUser(e.message.user, wustReceiver.client))
//                    wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getOrCreateChannelNode(e.channel, Constants.slackNode.id, wustReceiver, slackClient.client))
//                    changes <- OptionT[Future, (NodeId, GraphChanges)](wustReceiver.client.api.getNode(nodeId, wustUserData.wustUserToken).map {
//                      case Some(existingNode: Node.Content) =>
//                        Some(EventMapper.editMessageContentInWust(
//                          existingNode,
//                          NodeData.Markdown(e.message.text)
//                        ))
//                      case None =>
//                        Some(EventMapper.createMessageInWust(
//                          NodeData.Markdown(e.message.text),
//                          wustUserData.wustUserId,
//                          toEpochMilli(e.ts),
//                          wustChannelNodeId
//                        ))
//                      case n =>
//                        scribe.error(s"The node id does not corresponds to a content node: $n")
//                        None
//                    })
//                  } yield {
//                    (changes._1, changes._2, wustUserData)
//                  }
//
//                  val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not change message").flatMapF { changes =>
//                    val res = wustReceiver.push(List(changes._2), Some(changes._3))
//                    res.foreach {
//                      case Right(_) => persistenceAdapter.storeMessageMapping(Message_Mapping(e.channel, e.ts, changes._1))
//                      case _ => scribe.error(s"Could not apply changes to wust: $changes")
//                    }
//                    res
//                  }
//
//                  applyChanges.value.onComplete {
//                    case Success(request) =>
//                    case Failure(ex) => scribe.error("Error changing message: ", ex)
//                  }
//
//                  applyChanges.value

                case e: MessageDeleted =>
                  scribe.info(s"message: ${e.toString}")

//                  val graphChanges: OptionT[Future, GraphChanges] = for {
//                    nodeId <- OptionT(persistenceAdapter.getMessageNodeByChannelAndTimestamp(e.channel, e.previous_message.ts))
//                    wustChannelNodeId <- OptionT[Future, NodeId](persistenceAdapter.getChannelNode(e.channel))
//                  } yield {
//                    EventMapper.deleteMessageInWust(
//                      nodeId,
//                      wustChannelNodeId
//                    )
//                  }
//
//                  val applyChanges: EitherT[Future, String, List[GraphChanges]] = graphChanges.toRight[String]("Could not change message").flatMapF { changes =>
//                    wustReceiver.push(List(changes), None)
//                  }
//
//                  applyChanges.value.onComplete {
//                    case Success(request) =>
//                    case Failure(ex) => scribe.error("Error deleting message: ", ex)
//                  }
//
//                  applyChanges.value

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

           incomingEvents

          }
        }
      } ~ path(config.server.webhookPath) {
        post {
          decodeRequest {
            val challengeEvent = entity(as[EventServerChallenge]) { eventChallenge =>
              complete {
                HttpResponse(
                  status = StatusCodes.OK,
                  headers = Nil,
                  entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, eventChallenge.challenge)
                )
              }
            }

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

  def push(graphChanges: List[GraphChanges], auth: Option[WustUserData]): Result[List[GraphChanges]]
}

class WustReceiver(val client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(graphChanges: List[GraphChanges], auth: Option[WustUserData]): Result[List[GraphChanges]] = {
    scribe.info(s"pushing new graph change: $graphChanges")
    val enrichedWithAppMembership = graphChanges.map { gc =>
      val appMemberEdges: GraphChanges = GraphChanges.connect((u: UserId, n: NodeId) => Edge.Member(u, EdgeData.Member(AccessLevel.ReadWrite), n))(Constants.wustUser.id, gc.addNodes.map(_.id))
      gc.merge(appMemberEdges)
    }

    (auth match {
      case None =>
        val withAppUser = graphChanges.map(_.withAuthor(Constants.wustUser.id))
        for {
          true <- client.api.changeGraph(withAppUser)
          true <- Future.sequence {
            graphChanges.flatMap { gc =>
              gc.addNodes.toList.map(n => client.api.addMember(n.id, Constants.wustUser.id, AccessLevel.ReadWrite))
            }
          }.map(_.forall(_ == true))
        } yield true
      case Some(u) =>
        val withSlackUser = graphChanges.map(_.withAuthor(u.wustUserId))
        for {
          true <- client.api.changeGraph(withSlackUser, u.wustUserToken)
          true <- Future.sequence {
            graphChanges.flatMap { gc =>
              gc.addNodes.toList.map(n => client.api.addMember(n.id, Constants.wustUser.id, AccessLevel.ReadWrite))
            }
          }.map(_.forall(_ == true))
        } yield true
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

  def run(config: WustConfig, slackClient: SlackClient, persistenceAdapter: PersistenceAdapter, slackAppToken: String)(implicit system: ActorSystem): WustReceiver = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(system.dispatcher)

    //TODO: service discovery or some better configuration for the wust host
    val protocol = if (config.port == 443) "wss" else "ws"
    val location = s"$protocol://core.${config.host}:${config.port}/ws"
    val wustClient = WustClient(location)
    val client = wustClient.sendWith(SendType.WhenConnected, 30 seconds)
    val highPriorityClient = wustClient.sendWith(SendType.WhenConnected.highPriority, 30 seconds)

//    highPriorityClient.auth.assumeLogin(Constants.wustUser)
//    highPriorityClient.auth.register(config.user, config.password)
    wustClient.observable.connected.foreach { _ =>
      highPriorityClient.auth.login(config.user, config.password)
    }

    scribe.info("Running WustReceiver")

    val graphChanges: Observable[Seq[GraphChanges]] = wustClient.observable.event.map({ e =>
        scribe.info(s"triggering collect on $e")
        e.collect {
          case ev: ApiEvent.GraphContent =>
            scribe.info("received api event")
            ev
        }
    }).collect({
      case list if list.nonEmpty =>
        scribe.info("api event non-empty")
        list
    }).map(_.map {
      case NewGraphChanges(gc) => gc
    })

    graphChanges.foreach { graphChangeSeq =>
      graphChangeSeq.foreach { gc =>

        scribe.info(s"Received GraphChanges: $gc")

        /** ************/
        /* Meta stuff */
        /** ************/


        // TODO: Not possible to tell who created / deleted edges
        val wustEventUser = gc.addEdges.flatMap {
          case Edge.Author(userId, _, _) => Some(userId)
          case _ => None
        }.headOption

        val slackEventUser = OptionT[Future, SlackUserData](wustEventUser match {
          case Some(u) => persistenceAdapter.getSlackUser(u)
          case _ => Future.successful(None)
        })

        val slackUserToken = slackEventUser.map(_.slackUserToken).value.map(_.flatten).map(_.getOrElse(slackAppToken))

        val eventSlackClient = slackUserToken.map(SlackApiClient(_))

        /** ***************************/
        /* Delete channel or message */
        /** ***************************/
        val deleteEvents = Future.sequence(gc.delEdges.map {
          case Edge.Parent(childId, _, parentId) =>
            val slackMessage = persistenceAdapter.getSlackMessage(childId)

            slackMessage.flatMap {
              case Some(message) =>
                eventSlackClient.flatMap(_.deleteChat(message.slackChannelId, message.slackTimestamp, Some(true)))

              case None =>
                val slackChannel = persistenceAdapter.getSlackChannel(childId)
                slackChannel.flatMap {
                  case Some(channelId) =>
                    eventSlackClient.flatMap(_.archiveChannel(channelId)) //TODO: delete channel - not possible during time of writing
                  case None => Future.successful(None)
                }

            }
          case _ => Future.successful(None)
        })

        deleteEvents.onComplete {
          case Success(deleteChanges) => scribe.info(s"Successfully applied delete events: $deleteChanges")
          case Failure(ex) => scribe.error("Could not apply delete events: ", ex)
        }


        /** ************************/
        /* Add channel or message */
        /** ************************/

        val addChannelEvents = gc.addEdges.flatMap {
          case Edge.Parent(childId, _, Constants.slackNode.id) =>
            Some(childId -> Constants.slackNode.id)
          case _ => None
        }

        val addMessageEvents = Future.sequence(gc.addEdges.map {
          case Edge.Parent(childId, _, parentId) =>
            persistenceAdapter.getSlackChannel(parentId).map {
              case Some(slackChannelId) => Some(childId -> slackChannelId)
              case _ => None
            }
          case _ => Future.successful(None)
        }).map(_.flatten)


        def applyCreateChannelEvents() = Future.sequence(addChannelEvents.flatMap { channel =>
          gc.addNodes.filter(_.id == channel._1).map { channelNode =>
            eventSlackClient.flatMap(_.createChannel(channelNode.str))
          }
        })

        def applyCreateMessageEvents() = addMessageEvents.map { events =>
          Future.sequence(events.flatMap { message =>
            gc.addNodes.filter(_.id == message._1).map { messageNode =>
              eventSlackClient.flatMap(_.postChatMessage(message._2, messageNode.str))
            }
          })
        }.flatten


        def applyChangeChannelEvents(channel: (NodeId, String)) = Future.sequence(gc.addNodes.filter(_.id == channel._1).map { channelNode =>
          eventSlackClient.flatMap(_.renameChannel(channel._2, channelNode.str))
        })

        def applyChangeMessageEvents(message: (NodeId, SlackMessageId)) = Future.sequence(gc.addNodes.filter(_.id == message._1).map { messageNode =>
          eventSlackClient.flatMap(_.updateChatMessage(message._2.slackChannelId, message._2.slackTimestamp, messageNode.str, Some(true)))
        })


        val changeOrAddChannelEvents = Future.sequence(addChannelEvents.map { channel =>
          persistenceAdapter.getSlackChannel(channel._1).flatMap {
            case Some(slackChannelId) => applyChangeChannelEvents(channel._1 -> slackChannelId)
            case _ => applyCreateChannelEvents().map(_.map(_ => true))
          }
        }).map(_.flatten).onComplete {
          case Success(_) => scribe.info("Successfully created or updated slack channel")
          case Failure(ex) => scribe.error("Could not create or update slack channel: ", ex)
        }

        val changeOrAddMessageEvents = addMessageEvents.flatMap(events =>
          Future.sequence(events.map { message =>
            persistenceAdapter.getSlackMessage(message._1).flatMap {
              case Some(slackMessageId) => applyChangeMessageEvents(message._1 -> slackMessageId).map(_.map(_ => true))
              case _ => applyCreateMessageEvents().map(_.map(_ => true))
            }
          })).map(_.flatten).onComplete {
          case Success(_) => scribe.info("Successfully created or updated slack message")
          case Failure(ex) => scribe.error("Could not create or update slack message: ", ex)
        }


        /** ************************/
        /* Add channel or message */
        /** ************************/

        //        val addChannelEvents = gc.addEdges.flatMap {
        //          case Edge.Parent(childId, _, Constants.slackNode.id) =>
        //            Some(childId -> Constants.slackNode.id)
        //          case _ => None
        //        }
        //
        //        val addMessageEvents = Future.sequence(gc.addEdges.map {
        //          case Edge.Parent(childId, _, parentId) =>
        //            persistenceAdapter.getSlackChannel(parentId).map {
        //              case Some(slackChannelId) => Some(childId -> slackChannelId )
        //              case _ => None
        //            }
        //          case _ => Future.successful(None)
        //        }).map(_.flatten)
        //
        //
        //        def applyCreateChannelEvents(): Unit = Future.sequence(addChannelEvents.flatMap { channel =>
        //          gc.addNodes.filter(_.id == channel._1).map { channelNode =>
        //            eventSlackClient.flatMap (_.createChannel(channelNode.str))
        //          }
        //        }).onComplete {
        //          case Success(_) =>
        //          case Failure(ex) => scribe.error("Could not create new slack channel: ", ex)
        //        }
        //
        //        def applyCreateMessageEvents(): Unit = addMessageEvents.map { events =>
        //          Future.sequence(events.flatMap { message =>
        //            gc.addNodes.filter(_.id == message._1).map { messageNode =>
        //              eventSlackClient.flatMap(_.postChatMessage(message._2, messageNode.str))
        //            }
        //          })
        //        }.flatten.onComplete{
        //          case Success(_) =>
        //          case Failure(ex) => scribe.error("Could not create new slack message: ", ex)
        //        }

        /** ***************************/
        /* Change channel or message */
        /** ***************************/

        //        val changeOrAddChannelEvents = Future.sequence(addChannelEvents.map { channel =>
        //          persistenceAdapter.getSlackChannel(channel._1).map {
        //            case Some(slackChannelId) => Some(channel._1 -> slackChannelId)
        //            case _ => None
        //          }
        //        }).map(_.flatten)
        //
        //        val changeOrAddMessageEvents = addMessageEvents.flatMap(events =>
        //          Future.sequence(events.map { message =>
        //          persistenceAdapter.getSlackMessage(message._1).map {
        //            case Some(slackMessageId) => Some(message._1 -> slackMessageId)
        //            case _ => None
        //          }
        //        })).map(_.flatten)
        //
        //        def applyChangeChannelEvents(changeChannelEvents: Future[Set[(NodeId, String)]]): Unit = changeChannelEvents.flatMap(events =>
        //          Future.sequence(events.flatMap { channel =>
        //          gc.addNodes.filter(_.id == channel._1).map { channelNode =>
        //            eventSlackClient.flatMap(_.renameChannel(channel._2, channelNode.str))
        //          }
        //        })).onComplete{
        //          case Success(_) =>
        //          case Failure(ex) => scribe.error("Could not rename slack channel: ", ex)
        //        }
        //
        //        def applyChangeMessageEvents(changeMessageEvents: Future[Set[(NodeId, SlackMessageId)]]): Unit = changeMessageEvents.flatMap(events =>
        //          Future.sequence(events.flatMap { message =>
        //            gc.addNodes.filter(_.id == message._1).map { messageNode =>
        //              eventSlackClient.flatMap(_.updateChatMessage(message._2.slackChannelId, message._2.slackTimestamp, messageNode.str, Some(true)))
        //            }
        //          })).onComplete{
        //          case Success(_) =>
        //          case Failure(ex) => scribe.error("Could not update slack message: ", ex)
        //        }


      }
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
      val slackEventReceiver = WustReceiver.run(config.wust, slackClient, slackPersistenceAdapter, config.slack.token)
      //      val client = SlackClient(config.oAuthConfig.accessToken.get)
      AppServer.run(config, slackEventReceiver, slackClient, oAuthClient, slackPersistenceAdapter)
  }
}
