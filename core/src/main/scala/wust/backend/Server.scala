package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import boopickle.Default._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import chameleon.ext.boopickle._
import chameleon.ext.circe._
import covenant.http._
import covenant.ws._
import io.circe._
import io.circe.generic.extras.auto._
import io.circe.syntax._
import monix.execution.Scheduler
import mycelium.server._
import sloth._
import wust.api._
import wust.api.serialize.Boopickle._
import wust.api.serialize.Circe._
import wust.backend.Dsl._
import wust.backend.auth._
import wust.backend.config.Config
import wust.backend.mail.MailService
import wust.core.aws.S3FileUploader
import wust.core.{DbChangeGraphAuthorizer, EmailVerificationEndpoint}
import wust.db.Db

import scala.util.{Failure, Success}

object Server {
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.RouteResult._

  object paths {
    val health = "health"
    val emailVerify = "email-verify"
  }

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
    val mailService = MailService(config.email)
    val fileUploader = config.aws.map(new S3FileUploader(_, config.server)) //TODO local file uploader stub for dev?
    val emailFlow = new AppEmailFlow(config.server, jwt, mailService)
    val cancelable = emailFlow.start()
    val emailVerificationEndpoint = new EmailVerificationEndpoint(db, jwt, config.server)
    val changeGraphAuthorizer = new DbChangeGraphAuthorizer(db)

    val apiImpl = new ApiImpl(guardDsl, db, fileUploader, emailFlow, changeGraphAuthorizer)
    val authImpl = new AuthApiImpl(guardDsl, db, jwt, emailFlow)
    val pushImpl = new PushApiImpl(guardDsl, db, config.pushNotification)

    val jsonRouter = Router[String, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[AuthApi[ApiFunction]](authImpl)
      .route[PushApi[ApiFunction]](pushImpl)
    val binaryRouter = Router[ByteBuffer, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[AuthApi[ApiFunction]](authImpl)
      .route[PushApi[ApiFunction]](pushImpl)

    val eventDistributor = new HashSetEventDistributorWithPush(db, config.pushNotification)
    val apiConfig = new ApiConfiguration(guardDsl, eventDistributor)
    val serverConfig = WebsocketServerConfig(
      bufferSize = config.server.clientBufferSize,
      overflowStrategy = OverflowStrategy.fail
    )

    val corsSettings = CorsSettings.defaultSettings.withAllowedOrigins(
      HttpOriginRange(config.server.allowedOrigins.map(HttpOrigin(_)): _*)
    )

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
    } ~ (path(paths.health) & get) {
      complete("ok")
    } ~ (path(paths.emailVerify) & get) { // needs
      cors(corsSettings) {
        parameters('token.as[String]) { token =>
          emailVerificationEndpoint.verify(Authentication.Token(token))
        }
      }
    }
  }
}