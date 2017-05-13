package wust.backend

import wust.config.Config

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {
  scribe.info(s"Starting wust with Config: $Config")

  Server.run(8080) foreach { binding =>
    scribe.info(s"Server online at ${binding.localAddress}")
  }
}
