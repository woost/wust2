import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

class AliveSpec(implicit ee: ExecutionEnv) extends Specification with WustReady {
  import WustConnection._

  "should serve index.html on root" >> {
    get("/").map { response =>
      response.status.isSuccess must beTrue
      response.entity.toString must contain("Wust")
    } await
  }
}
