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
import wust.serviceUtil.Logging
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange}
import mycelium.client.SendType
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.ByteStringBuilder
import cats.data.{EitherT, OptionT}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import monix.execution.Scheduler
import monix.reactive.Observable
import cats.implicits._
import monix.eval.Task
import monix.reactive.subjects.ConcurrentSubject

import scala.util.{Failure, Success, Try}
import slack.api.SlackApiClient
import wust.api.ApiEvent.NewGraphChanges
import wust.slack.Data._

object Constants {
  //TODO
  val wustUser = AuthUser.Assumed(UserId.fromBase58String("5R1xejdFpxQiauAZtMVqpS"), NodeId.fromBase58String("5R1xejdFpxQiauAZtMVqpT"))
//  val globalSlackNode = Node.Content(NodeId.fromBase58String("5R28qFeQj1Ny6tM9b7BXis"), NodeData.Markdown("wust-slack"), NodeMeta(NodeAccess.Restricted))
}


class SlackApiImpl[F[_]](client: WustClient[Future], oAuthClient: OAuthClient, persistenceAdapter: PersistenceAdapter)(
  implicit ec: ExecutionContext
) extends PluginApi[F] {

  override def connectUser(auth: Authentication.Token): Task[Option[String]] = {
    client.auth.verifyToken(auth).map {
      case Some(verifiedAuth) =>
        scribe.info(s"User has valid auth: ${ verifiedAuth.user.name }")
        oAuthClient.authorizeUrl(
          verifiedAuth,
          List(
            //            "admin",
            // "auditlogs:read",
            "bot",
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
          )
        ).map(_.toString())
      case None               =>
        scribe.info(s"Invalid auth")
        None
    }
  }

  override def isAuthenticated(userId: UserId): Task[Boolean] = {
    persistenceAdapter.getSlackUserDataByWustId(userId).map {
      case Some(slackUser) => slackUser.slackUserToken.isDefined
      case _ => false
    }
  }

  override def getAuthentication(userId: UserId, auth: Authentication.Token): Task[Option[PluginUserAuthentication]] = {
    client.auth.verifyToken(auth).flatMap {
      case Some(_) =>
        persistenceAdapter.getSlackUserDataByWustId(userId).map {
          case Some(slackUser) => Some(PluginUserAuthentication(userId, slackUser.slackUserId, slackUser.slackUserToken))
          case _ => None
        }
      case _ => Task.pure(None)
    }
  }

  override def importContent(identifier: String): Task[Boolean] = {
    // TODO: Seeding
    Task.pure(true)
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

  def run(config: Config, wustReceiver: WustReceiver, slackClient: SlackClient, oAuthClient: OAuthClient, persistenceAdapter: PersistenceAdapter, slackRequestVerifier: SlackRequestVerifier)(
    implicit system: ActorSystem, scheduler: Scheduler, ec: ExecutionContext
  ): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import wust.api.serialize.Boopickle._

    val apiRouter = Router[ByteBuffer, Task]
      .route[PluginApi[Task]](new SlackApiImpl[Task](wustReceiver.client, oAuthClient, persistenceAdapter))

    val corsSettings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange(config.appServer.allowedOrigins.map(HttpOrigin(_)): _*)
    )

//    // TODO: author
//    val changes = GraphChanges(
//      addNodes = Set(Constants.globalSlackNode),
//    )
//    wustReceiver.push(List(changes), None)

    val slackEventMapper = SlackEventMapper(persistenceAdapter, wustReceiver, slackClient.apiClient)

    val tokenObserver = ConcurrentSubject.publish[AuthenticationData]
    tokenObserver.foreach { authData =>

      scribe.info(s"received oauth token")
      val userOAuthToken = authData.platformAuthToken

      def slackUpdate = for {

        // get user information
        slackAuthId <- SlackApiClient(userOAuthToken.accessToken.toString).testAuth()
        true <- persistenceAdapter.storeOrUpdateUserAuthData(User_Mapping(slackAuthId.user_id, authData.wustAuthData.user.id, Some(userOAuthToken.accessToken), authData.wustAuthData.token))

        // Create workspace node and store team mapping
        workspaceNodeId <- persistenceAdapter.getTeamNodeBySlackId(slackAuthId.team_id).flatMap {
          case Some(nodeId) => Task.pure(nodeId)
          case None         =>
            val createdWorkspaceNode = EventToGraphChangeMapper.createWorkspaceInWust(NodeData.PlainText(slackAuthId.team), Constants.wustUser.id, EpochMilli.now)
            wustReceiver.push(List(createdWorkspaceNode.graphChanges), None).map(_ => createdWorkspaceNode.nodeId)
        }
        true <- persistenceAdapter.storeOrUpdateTeamMapping(Team_Mapping(Some(slackAuthId.team_id), slackAuthId.team, workspaceNodeId))

        // Add membership for user to workspace node
        true <- wustReceiver.client.api.addMember(workspaceNodeId, authData.wustAuthData.user.id, AccessLevel.ReadWrite)
        true <- wustReceiver.push(
          List(
            GraphChanges.connect(Edge.Parent)(workspaceNodeId, authData.wustAuthData.user.channelNodeId)
          ),
          Some(WustUserData(authData.wustAuthData.user.id, authData.wustAuthData.token))
        ).map(_.isRight)
      } yield true

      slackUpdate.onComplete {
        case Success(p)  => if(p) scribe.info("persisted user data") else scribe.error("could not persist user data")
        case Failure(ex) => scribe.error("failed to persist user data: ", ex)
      }

//      } yield (slackAuthId.team_id, true)
//
//      slackUpdate.flatMap(isPersist =>
//        if(isPersist._2) SlackSeeder.channelDataToWust(SlackApiClient(userOAuthToken.accessToken), slackEventMapper, persistenceAdapter, isPersist._1)
//        else Task.pure(Seq.empty[GraphChanges])
//      ).onComplete {
//        case Success(p)  => scribe.info("Successfully synced slack data")
//        case Failure(ex) => scribe.error("failed to sync slack data: ", ex)
//      }

//      slackUpdate.onComplete {
//        case Success(p)  => if(p._2) scribe.info("persisted user data") else scribe.error("could not persist user data")
//        case Failure(ex) => scribe.error("failed to persist user data: ", ex)
//      }
    }

    import slack.models._
    val route = {
      pathPrefix("api") {
        cors(corsSettings) {
          AkkaHttpRouteForTask.fromTaskRouter(apiRouter)
        }
      } ~ path(config.appServer.webhookPath) {
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
      } ~ path(config.appServer.webhookPath) {
        headerValueByName("X-Slack-Request-Timestamp") { slackRequestTimestamp =>
          headerValueByName("X-Slack-Signature") { slackSignature =>
            post {
              decodeRequest {
                extractRequest { request =>
                  onComplete[Route](request.entity.dataBytes.runFold(new ByteStringBuilder)(_ append _).map { bytes =>
                    val stringBody = bytes.result().utf8String
                    if(slackRequestVerifier.verify(slackRequestTimestamp, stringBody, slackSignature)) {
                      entity(as[SlackEventStructure]) { eventStructure =>

                      {
                        slackEventMapper.matchSlackEventStructureEvent(eventStructure).runAsync.onComplete {
                          case Success(e)  =>
                            e match {
                              case Right(r) =>
                                scribe.info(s"Successfully mapped $r")
                              case Left(l) =>
                                scribe.error(s"An error occured: $l")
                            }
                          case Failure(ex) =>
                            scribe.error("Could not match event: ", ex)
                        }
                      }

                        complete(StatusCodes.OK)
                      }
                    } else {
                      complete(StatusCodes.Unauthorized)
                    }
                  }) {
                  case Success(r) => r
                  case Failure(f) =>
                    scribe.warn("Failed to get entity bytes", f)
                    complete(StatusCodes.InternalServerError)
                }}
              }
            }
          }
        }
      } ~ {
        oAuthClient.route(tokenObserver)
      }
    }

    Http().bindAndHandle(route, interface = "0.0.0.0", port = config.appServer.port).onComplete {
      case Success(binding) =>
        val separator = "\n############################################################"
        val readyMsg = s"\n##### Slack App Server online at ${ binding.localAddress } #####"
        scribe.info(s"$separator$readyMsg$separator")

      case Failure(err) => scribe.error(s"Cannot start Slack App Server: $err")
    }
  }
}

trait MessageReceiver {
  type Result[T] = Task[Either[String, T]]

  def push(graphChanges: List[GraphChanges], auth: Option[WustUserData]): Result[List[GraphChanges]]
}

class WustReceiver(val client: WustClient[Task])(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(graphChanges: List[GraphChanges], auth: Option[WustUserData]): Result[List[GraphChanges]] = {
    scribe.info(s"pushing new graph change: $graphChanges")
    val enrichedWithAppMembership = graphChanges.map { gc =>
      val appMemberEdges: GraphChanges = GraphChanges.connect((u: UserId, n: NodeId) => Edge.Member(u, EdgeData.Member(AccessLevel.ReadWrite), n))(Constants.wustUser.id, gc.addNodes.map(_.id))
      gc.merge(appMemberEdges)
    }

    (auth match {
      case None    =>
        val withAppUser = graphChanges.map(_.withAuthor(Constants.wustUser.id))
        for {
          true <- client.api.changeGraph(withAppUser)
          true <- Task.sequence {
            graphChanges.flatMap { gc =>
              gc.addNodes.toList.map(n => client.api.addMember(n.id, Constants.wustUser.id, AccessLevel.ReadWrite))
            }
          }.map(_.forall(_ == true))
        } yield true
      case Some(u) =>
        val withSlackUser = graphChanges.map(_.withAuthor(u.wustUserId))
        for {
          true <- client.api.changeGraph(withSlackUser, u.wustUserToken)
          true <- Task.sequence {
            graphChanges.flatMap { gc =>
              gc.addNodes.toList.map(n => client.api.addMember(n.id, Constants.wustUser.id, AccessLevel.ReadWrite))
            }
          }.map(_.forall(_ == true))
        } yield true
    }).map { success =>
      if(success) Right(graphChanges)
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
    val protocol = if(config.port == 443) "wss" else "ws"
    val location = s"$protocol://core.${ config.host }:${ config.port }/ws"
    val wustClient = WustClient.withTask(location)
    val client = wustClient.sendWith(SendType.WhenConnected, 30 seconds)
    val highPriorityClient = wustClient.sendWith(SendType.WhenConnected.highPriority, 30 seconds)
    val wustEventMapper = WustEventMapper(slackAppToken, persistenceAdapter)

    // TODO: failsave and only initially assume
    highPriorityClient.auth.assumeLogin(Constants.wustUser)
    highPriorityClient.auth.register(config.user, config.password)
    wustClient.observable.connected.foreach { _ =>
      highPriorityClient.auth.login(config.user, config.password)
    }

    val separator = "\n#############################################################"
    val readyMsg = s"\n##### Slack App WustReceiver connecting at ${location} #####"
    scribe.info(s"$separator$readyMsg$separator")
    scribe.info("Running WustReceiver")

    val graphChanges: Observable[Seq[NewGraphChanges]] = wustClient.observable.event.map({ e =>
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
    }).map(_.collect {
      case gc: NewGraphChanges => gc
    })

    graphChanges.foreach { graphChangeSeq =>
      graphChangeSeq.foreach { gc =>

        scribe.info(s"Received GraphChanges: ${gc.changes}")
        wustEventMapper.computeMapping(gc.user, gc.changes)
      }
    }

    new WustReceiver(client)
  }


  private def validRecover[T]: PartialFunction[Throwable, Either[String, T]] = {
    case NonFatal(t) => Left(s"Exception was thrown: $t")
  }
  private def valid(fut: Task[Boolean], errorMsg: String)(implicit ec: ExecutionContext) =
    EitherT(fut.map(Either.cond(_, (), errorMsg)).recover(validRecover))
  private def valid[T](fut: Task[T])(implicit ec: ExecutionContext) =
    EitherT(fut.map(Right(_): Either[String, T]).recover(validRecover))
}

object SlackClient {
  def apply(accessToken: String, isUser: Boolean)(implicit ec: ExecutionContext): SlackClient = {
    new SlackClient(SlackApiClient(accessToken), isUser)
  }

  //  def wrapFCall[F, T](f: F => Future[T]): Task[T] = Task.fromFuture(f)
  object TaskImplicits {
    implicit def wrapFCall[T](f: Future[T]): Task[T] = Task.fromFuture(f)
  }
}

class SlackClient(val apiClient: SlackApiClient, val isUser: Boolean)(implicit ec: ExecutionContext) {

  case class Error(desc: String)

  def run(receiver: MessageReceiver): Unit = {
    // TODO: Get events from slack
    //    private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
    //    private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  }

}

case class SlackRequestVerifier(key: String) {
  import javax.crypto.Mac
  import javax.crypto.spec.SecretKeySpec

  private val secret = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256")
  private val hmac = Mac.getInstance("HmacSHA256")
  hmac.init(secret)

  def generateHmac(data: String): String = {
    val hashString: Array[Byte] = hmac.doFinal(data.getBytes)
    hashString.map("%02x".format(_)).mkString
  }

  def verify(timestamp: String, body: String, signature: String, versionNumber: String = "v0"): Boolean = {
    val base = s"$versionNumber:$timestamp:$body"
    val res = generateHmac(base)
    val equal = signature == s"$versionNumber=$res"
    if(!equal) {
      scribe.error(s"Received event does not match signature")
      scribe.error(s"signature: $signature")
      scribe.error(s"computed: $versionNumber=$res")
    }

    equal
  }

}

object App extends scala.App {

  import monix.execution.Scheduler.Implicits.global

  implicit val system: ActorSystem = ActorSystem("slack")

  Config.load() match {
    case Left(err)     => scribe.info(s"Cannot load config: $err")
    case Right(config) =>
      Logging.setup(Logging.Config(id = "slack", config.logstash))
      val oAuthClient = OAuthClient(config.oAuth, config.appServer, config.wustServer)
      val slackPersistenceAdapter = PostgresAdapter(config.postgres)
      val slackClient = SlackClient(config.slack.token, isUser = false)
      val slackEventReceiver = WustReceiver.run(config.wustServer, slackClient, slackPersistenceAdapter, config.slack.token)
      val slackRequestVerifier = SlackRequestVerifier(config.slack.signingSecret)
      AppServer.run(config, slackEventReceiver, slackClient, oAuthClient, slackPersistenceAdapter, slackRequestVerifier)
  }
}
