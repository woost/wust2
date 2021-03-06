package wust.core

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.util.ByteStringBuilder
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
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
import wust.api.{Api, AuthApi, Authentication, Password, PushApi}
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

import scala.concurrent.duration._
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

    Http().newServerAt("0.0.0.0", config.server.port).bindFlow(route).onComplete {
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
    val fileUploader = config.aws.map(new S3FileUploader(_)) //TODO local file uploader stub for dev?

    val emailFlow = new AppEmailFlow(config.server, jwt, mailService)
    val pushClients = config.pushNotification.map(PushClients.apply)
    val oAuthClientServiceLookup = new OAuthClientServiceLookup(jwt, config.server, pushClients)
    val oAuthFlowEndpoint = new OAuthFlowEndpoint(db, jwt, config.server, oAuthClientServiceLookup)
    val emailVerificationEndpoint = new EmailVerificationEndpoint(db, jwt, config.server)
    val passwordResetEndpoint = new  PasswordResetEndpoint(db, jwt, config.server)
    val changeGraphAuthorizer = new DbChangeGraphAuthorizer(db)
    val graphChangesNotifier = new GraphChangesNotifier(db, emailFlow)
    val pollingNotifier = new PollingNotifier(db, emailFlow)
    val stripeApi = config.stripe.map(new StripeApi(_, config.server))
    val stripeWebhookEndpoint = stripeApi.map(new StripeWebhookEndpoint(db, _))

    emailFlow.start()
    // pollingNotifier.start(interval = 15 minutes)

    val apiImpl = new ApiImpl(guardDsl, db, fileUploader, config.server, emailFlow, changeGraphAuthorizer, graphChangesNotifier, stripeApi)
    val authImpl = new AuthApiImpl(guardDsl, db, jwt, emailFlow, oAuthClientServiceLookup)
    val pushImpl = new PushApiImpl(guardDsl, db, config.pushNotification)

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
      val (protocol,port) = if (config.server.host == "localhost") ("http://", ":12345") else ("https://", "")
      s"${protocol}${config.server.host}${port}"
    }
    val corsSettings = CorsSettings.defaultSettings.withAllowedOrigins(HttpOriginMatcher(websiteOrigin))

    implicit val jsonMarshaller: ToResponseMarshaller[String] =
      Marshaller.withFixedContentType(ContentTypes.`application/json`) { s =>
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(ContentTypes.`application/json`, s)
        )
      }

    path("ws") {
      AkkaWsRoute.fromApiRouter(binaryRouter, serverConfig, apiConfig)
    } ~ pathPrefix("api") {
      cors(corsSettings) {
        AkkaHttpRoute.fromApiRouter(jsonRouter, apiConfig)
      }
    } ~ (path(ServerPaths.health) & get) {
      cors(corsSettings) {
        complete("ok")
      }
    } ~ (path(ServerPaths.emailVerify) & get) { // needs
      parameters('token.as[String]) { token =>
        emailVerificationEndpoint.verify(Authentication.Token(token))
      }
    } ~ path(ServerPaths.passwordReset) {
      parameters('token.as[String]) { token =>
        get {
          passwordResetEndpoint.form(Authentication.Token(token))
        } ~ post {
          formFields('password.as[String]) { newPassword =>
            passwordResetEndpoint.reset(Authentication.Token(token), Password(newPassword))
          }
        }
      }
    } ~ (pathPrefix(ServerPaths.oauth) & get) {
      path(Remaining) { token =>
        parameters('code.as[String]) { code =>
          oAuthFlowEndpoint.connect(Authentication.Token(token), code)
        }
      }
    } ~ (pathPrefix(ServerPaths.stripeWebhook) & post) {
      import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._ // WARNING: only EVER import locally here. will break other json api!!!!!
      stripeWebhookEndpoint match {
        case Some(endpoint) =>
          headerValueByName("Stripe-Signature") { signature =>
            extractRequest { request =>
              onComplete(request.entity.dataBytes.runFold(new ByteStringBuilder)(_ append _)) {
                case Success(eventBytes) =>
                  val eventJson = eventBytes.result().utf8String
                  endpoint.receive(eventJson, signature)
                case Failure(error) =>
                  scribe.warn("Error while decoding incoming Stripe webhook", error)
                  complete(StatusCodes.InternalServerError)
              }
            }
          }
        case None =>
          scribe.warn("Got Stripe event, but no endpoint is configured")
          complete(StatusCodes.InternalServerError)
      }
    }
  }
}
