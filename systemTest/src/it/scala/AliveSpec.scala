import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.{Sink, Source}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable

class AliveSpec(implicit ee: ExecutionEnv) extends mutable.Specification with WustReady {
  import WustConnection._

  import scala.concurrent.duration._
  private val timeout = 500.millis

  "should serve index.html on root" >> {
    get("/").map { response =>
      response.status.isSuccess must beTrue
      val text = response.entity.toStrict(timeout).map(_.data.utf8String)
      text must contain("Woost").await
    } await
  }

  "should upgrade websocket request on /ws" >> {
    val (upgradeResponse, _) = ws(Sink.ignore, Source.empty)

    upgradeResponse.map { upgrade =>
      upgrade.response.status must beEqualTo(StatusCodes.SwitchingProtocols)
    } await
  }
}
