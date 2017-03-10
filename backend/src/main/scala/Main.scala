package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {
  Server.run(8080) foreach { binding =>
    scribe.info(s"Server online at ${binding.localAddress}")
  }
}
