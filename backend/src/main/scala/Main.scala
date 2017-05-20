package wust.backend

import wust.config.Config

object Main extends App {
  scribe.info(s"Starting wust with Config: $Config")
  Server.run(8080)
}
