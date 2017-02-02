import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.{Sink, Source}

import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

class WebsocketSpec(implicit ee: ExecutionEnv) extends Specification with WustReady {
  import WustConnection._

  "should upgrade websocket request on /ws" >> {
    val (upgradeResponse, _) = ws(Sink.ignore, Source.empty)

    upgradeResponse.map { upgrade =>
      upgrade.response.status must beEqualTo(StatusCodes.SwitchingProtocols)
    } await
  }
}
