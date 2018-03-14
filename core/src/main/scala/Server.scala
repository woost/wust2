package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import wust.api._, serialize.Boopickle._, serialize.Circe._
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
import boopickle.shapeless.Default._
import chameleon.ext.circe._
import io.circe._, io.circe.syntax._, io.circe.generic.auto._
import wust.util.{ Pipe, RichFuture }
import cats.implicits._
import monix.execution.Scheduler

import scala.concurrent.{ ExecutionContext, Future }

import scala.util.{ Success, Failure }
import scala.util.control.NonFatal

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
    val authApiImpl = new AuthApiImpl(guardDsl, db, jwt)
    val jsonRouter = Router[String, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[AuthApi[ApiFunction]](authApiImpl)
    val binaryRouter = Router[ByteBuffer, ApiFunction]
      .route[Api[ApiFunction]](apiImpl)
      .route[AuthApi[ApiFunction]](authApiImpl)

    val eventDistributor = new HashSetEventDistributorWithPush(db)
    val apiConfig = new ApiConfiguration(guardDsl, eventDistributor)
    val serverConfig = WebsocketServerConfig(bufferSize = config.server.clientBufferSize, overflowStrategy = OverflowStrategy.fail)

    path("ws") {
      AkkaWsRoute.fromApiRouter(binaryRouter, serverConfig, apiConfig)
    } ~ pathPrefix("api") {
      //TODO no hardcode
      val allowedOrigins = List("http://localhost:12345", "https://woost.space")
      cors(CorsSettings.defaultSettings.copy(allowedOrigins = HttpOriginRange(allowedOrigins.map(HttpOrigin(_)): _*))) {
        AkkaHttpRoute.fromApiRouter(jsonRouter, apiConfig)
      }
    } ~ (path("health") & get) {
      complete("ok")
    }
  }
}
