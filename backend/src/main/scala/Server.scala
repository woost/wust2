package wust.backend

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import boopickle.Default._
import wust.api._, serialize.Boopickle._
import wust.ids._
import wust.backend.auth._
import wust.backend.config.Config
import wust.db.Db
import covenant.ws._
import mycelium.server._
import chameleon.ext.boopickle._
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

    val route = path("ws") {
      websocketRoute(config)
    } ~ (path("health") & get) {
      complete("ok")
    }

    Http().bindAndHandle(route, interface = "0.0.0.0", port = config.server.port).onComplete {
      case Success(binding) => {
        val separator = "\n" + ("#" * 50)
        val readyMsg = s"\n##### Server online at ${binding.localAddress} #####"
        scribe.info(s"$separator$readyMsg$separator")
      }
      case Failure(err) => scribe.error(s"Cannot start server: $err")
    }
  }

  private def websocketRoute(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer, scheduler: Scheduler) = {
    import DbConversions._
    val db = Db(config.db)
    val jwt = new JWT(config.auth.secret, config.auth.tokenLifetime)
    val stateInterpreter = new StateInterpreter(jwt, db)
    val guardDsl = new GuardDsl(jwt, db)

    val router = Router[ByteBuffer, ApiFunction]
      .route[Api[ApiFunction]](new ApiImpl(guardDsl, db))
      .route[AuthApi[ApiFunction]](new AuthApiImpl(guardDsl, db, jwt))

    val requestHandler = new ApiRequestHandler(new EventDistributor(db), stateInterpreter, router)
    val serverConfig = WebsocketServerConfig(bufferSize = config.server.clientBufferSize, overflowStrategy = OverflowStrategy.fail)

    router.asWsRoute(serverConfig, requestHandler)
  }
}
