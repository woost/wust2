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
import sloth.core._
import sloth.mycelium._
import sloth.boopickle._
import sloth.server.{Server => SlothServer, _}
import mycelium.server._
import wust.util.{ Pipe, RichFuture }
import cats.implicits._

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
    import system.dispatcher

    val websocketFlowFactory = WebsocketFactory(config)
    val route = (path("ws") & get) {
      handleWebSocketMessages(websocketFlowFactory())
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
}

object WebsocketFactory {
  import DbConversions._

  def apply(config: Config)(implicit ec: ExecutionContext, system: ActorSystem) = {
    val db = Db(config.db)
    val jwt = JWT(config.auth.secret, config.auth.tokenLifetime)
    val stateInterpreter = new StateInterpreter(jwt, db)
    val guardDsl = GuardDsl(jwt, db)

    val server = SlothServer[ByteBuffer, ApiFunction]
    val api =
      server.route[Api[ApiFunction]](new ApiImpl(guardDsl, db)) orElse
        server.route[AuthApi[ApiFunction]](new AuthApiImpl(guardDsl, db, jwt))

    val requestHandler = new ApiRequestHandler(new EventDistributor(db), stateInterpreter, api)
    val serverConfig = ServerConfig(bufferSize = config.server.clientBufferSize, overflowStrategy = OverflowStrategy.fail)
    () => WebsocketServerFlow(serverConfig, requestHandler)
  }
}
