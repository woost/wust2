package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import wust.api._
import serialize.Boopickle._
import serialize.Circe._
import wust.ids._
import wust.backend.Dsl._
import wust.backend.auth._
import wust.backend.config.Config
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange}
import wust.db.Db
import covenant.ws._
import covenant.http._
import sloth._
import mycelium.server._
import chameleon.ext.boopickle._
import boopickle.Default._
import chameleon.ext.circe._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import wust.util.RichFuture
import cats.implicits._
import monix.execution.Scheduler

import scala.util.{Failure, Success}

object Server {
  import akka.http.scaladsl.server.RouteResult._
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.Http

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
    val stateInterpreter = new StateInterpreter(jwt, db)
    val guardDsl = new GuardDsl(jwt, db)

    val apiImpl = new ApiImpl(guardDsl, db)
    val authImpl = new AuthApiImpl(guardDsl, db, jwt)
    val pushImpl = new PushApiImpl(guardDsl, db, config.pushNotification)

    val jsonRouter = Router[String, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[PushApi[ApiFunction]](pushImpl)
    val binaryRouter = Router[ByteBuffer, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[AuthApi[ApiFunction]](authImpl)
      .route[PushApi[ApiFunction]](pushImpl)

    val eventDistributor = new HashSetEventDistributorWithPush(db, config.pushNotification)
    val apiConfig = new ApiConfiguration(guardDsl, stateInterpreter, eventDistributor)
    val serverConfig = WebsocketServerConfig(bufferSize = config.server.clientBufferSize, overflowStrategy = OverflowStrategy.fail)

    val corsSettings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange(config.server.allowedOrigins.map(HttpOrigin(_)): _*))

    path("ws") {
      AkkaWsRoute.fromApiRouter(binaryRouter, serverConfig, apiConfig)
    } ~ pathPrefix("api") {
      cors(corsSettings) {
        implicit val jsonMarshaller: ToResponseMarshaller[String] = Marshaller.withFixedContentType(ContentTypes.`application/json`) { s =>
          HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, s))
        }
        AkkaHttpRoute.fromApiRouter(jsonRouter, apiConfig)
      }
    } ~ (path("health") & get) {
      complete("ok")
    }
  }
}
