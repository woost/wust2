package wust.core

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, OverflowStrategy}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import covenant.http.AkkaHttpRoute
import covenant.ws.AkkaWsRoute
import monix.execution.Scheduler
import mycelium.server.WebsocketServerConfig
import sloth.Router
import wust.api.{Api, AuthApi, Authentication, PushApi}
import wust.core.mail.MailService
import wust.core.aws.{S3FileUploader, SESMailClient}
import wust.core.pushnotifications.PushClients
import wust.db.Db
import wust.core.Dsl._
import wust.core.auth.{JWT, OAuthClientServiceLookup}
import wust.core.config.Config
import boopickle.Default._
import chameleon.ext.boopickle._
import chameleon.ext.circe._
import com.amazonaws.transform.JsonUnmarshallerContext.UnmarshallerType
import io.circe._
import io.circe.generic.extras.auto._
import io.circe.syntax._
import wust.api.serialize.Boopickle._
import wust.api.serialize.Circe._

import scala.util.{Failure, Success}

object Server {
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.RouteResult._

  def run(config: Config) = {
    implicit val system = ActorSystem("server")
    implicit val materializer = ActorMaterializer()
    implicit val scheduler = Scheduler(system.dispatcher)

    val route = createRoute(config)

    Http().bindAndHandle(route, interface = "0.0.0.0", port = config.server.port).onComplete {
      case Success(binding) => {
        val separator = "\n" + ("#" * 50)
        val readyMsg = s"\n##### Server online at ${binding.localAddress} #####"
        scribe.info(s"$separator$readyMsg$separator")
      }
      case Failure(err) => scribe.error(s"Cannot start server: $err")
    }
  }

  private def createRoute(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer, scheduler: Scheduler) = {
    val db = Db(config.db)
    val jwt = new JWT(config.auth.secret, config.auth.tokenLifetime)
    val guardDsl = new GuardDsl(jwt, db)

    val mailService = config.aws.flatMap(_.ses).fold(MailService(config.email)) { sesConfig =>
      MailService(sesConfig.settings, new SESMailClient(AppEmailFlow.teamEmailAddress, sesConfig))
    }
    val fileUploader = config.aws.map(new S3FileUploader(_, config.server)) //TODO local file uploader stub for dev?

    val emailFlow = new AppEmailFlow(config.server, jwt, mailService)
    val cancelable = emailFlow.start()
    val pushClients = config.pushNotification.map(PushClients.apply)
    val oAuthClientServiceLookup = new OAuthClientServiceLookup(jwt, config.server, pushClients)
    val oAuthVerificationEndpoint = new OAuthClientVerificationEndpoint(db, jwt, config.server, oAuthClientServiceLookup)
    val emailVerificationEndpoint = new EmailVerificationEndpoint(db, jwt, config.server)
    val changeGraphAuthorizer = new DbChangeGraphAuthorizer(db)

    val apiImpl = new ApiImpl(guardDsl, db, fileUploader, emailFlow, changeGraphAuthorizer)
    val authImpl = new AuthApiImpl(guardDsl, db, jwt, emailFlow, oAuthClientServiceLookup)
    val pushImpl = new PushApiImpl(guardDsl, db, pushClients)

    val jsonRouter = Router[String, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[AuthApi[ApiFunction]](authImpl)
      .route[PushApi[ApiFunction]](pushImpl)
    val binaryRouter = Router[ByteBuffer, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[AuthApi[ApiFunction]](authImpl)
      .route[PushApi[ApiFunction]](pushImpl)

    val eventDistributor = new HashSetEventDistributorWithPush(db, config.server, pushClients)
    val apiConfig = new ApiConfiguration(guardDsl, eventDistributor)
    val serverConfig = WebsocketServerConfig(
      bufferSize = config.server.clientBufferSize,
      overflowStrategy = OverflowStrategy.fail
    )

    val websiteOrigin = {
      val protocol = if (config.server.host == "localhost") "http://" else "https://"
      s"${protocol}${config.server.host}"
    }
    val corsSettings = CorsSettings.defaultSettings.withAllowedOrigins(HttpOriginRange(websiteOrigin))

    path("ws") {
      AkkaWsRoute.fromApiRouter(binaryRouter, serverConfig, apiConfig)
    } ~ pathPrefix("api") {
      cors(corsSettings) {
        implicit val jsonMarshaller: ToResponseMarshaller[String] =
          Marshaller.withFixedContentType(ContentTypes.`application/json`) { s =>
            HttpResponse(
              status = StatusCodes.OK,
              entity = HttpEntity(ContentTypes.`application/json`, s)
            )
          }
        AkkaHttpRoute.fromApiRouter(jsonRouter, apiConfig)
      }
    } ~ (path(ServerPaths.health) & get) {
      complete("ok")
    } ~ (path(ServerPaths.emailVerify) & get) { // needs
      cors(corsSettings) {
        parameters('token.as[String]) { token =>
          emailVerificationEndpoint.verify(Authentication.Token(token))
        }
      }
    } ~ (pathPrefix(ServerPaths.oauth) & get) {
      path(Remaining) { token =>
        parameters('code.as[String]) { code =>
          oAuthVerificationEndpoint.verify(Authentication.Token(token), code)
        }
      }
    }
  }
}
