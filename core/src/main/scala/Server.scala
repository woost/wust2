package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import wust.api._
import wust.api.serialize.Boopickle._
import wust.api.serialize.Circe._
import wust.backend.Dsl._
import wust.backend.auth._
import wust.backend.config.Config
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange}
import wust.db.{Db, SuccessResult}
import covenant.ws._
import covenant.http._
import sloth._
import mycelium.server._
import chameleon.ext.boopickle._
import boopickle.Default._
import chameleon.ext.circe._
import io.circe._
import io.circe.syntax._
import io.circe.generic.extras.auto._
import wust.util.RichFuture
import cats.implicits._
import monix.execution.Scheduler
import wust.backend.mail.MailService
import wust.core.aws.S3FileUploader

import scala.concurrent.Future
import scala.util.{Failure, Success}

object Server {
  import akka.http.scaladsl.server.RouteResult._
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.Http

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
    import DbConversions._
    val db = Db(config.db)
    val jwt = new JWT(config.auth.secret, config.auth.tokenLifetime)
    val guardDsl = new GuardDsl(jwt, db)
    val mailService = MailService(config.email)
    val fileUploader = config.aws.map(new S3FileUploader(_, config.server))
    val emailFlow = new AppEmailFlow(config.server, jwt, mailService)
    val cancelable = emailFlow.start()

    val apiImpl = new ApiImpl(guardDsl, db, fileUploader)
    val authImpl = new AuthApiImpl(guardDsl, db, jwt, emailFlow)
    val pushImpl = new PushApiImpl(guardDsl, db, config.pushNotification)

    val jsonRouter = Router[String, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
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
          def link =  s"""<a href="https://${config.server.host}">Go back to app</a>"""
          def successMessage = complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Your email address has been verified. Thank you! $link"))
          def invalidMessage = complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Cannot verify email address. This email verification token was already used or is invalid or expired. $link"))
          def errorMessage = complete(StatusCodes.InternalServerError -> s"Sorry, we cannot verify your email address. Please try again later.")
          jwt.emailActivationFromToken(token) match {
           case Some(activation) if !JWT.isExpired(activation) =>
             onComplete(db.user.verifyEmailAddress(activation.userId, activation.email)) {
               case Success(true) => successMessage
               case Success(false) => invalidMessage
               case Failure(t) =>
                 scribe.error("There was an error when verifying an email address", t)
                 errorMessage
             }
           case _ => invalidMessage
         }
        }
      }
    }
  }
}
