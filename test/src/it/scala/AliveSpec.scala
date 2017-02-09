import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

class AliveSpec(implicit ee: ExecutionEnv) extends Specification with WustReady {
  import WustConnection._

  import scala.concurrent.duration._
  private val timeout = 500.millis

  "should serve index.html on root" >> {
    get("/").map { response =>
      response.status.isSuccess must beTrue
      val text = response.entity.toStrict(timeout).map(_.data.utf8String)
      text must contain("Wust").await
    } await
  }
}
