package wust.backend

import wust.backend.config.Config
import wust.serviceUtil.Logging

object Main extends App {
  Config.load match {
    case Left(error) =>
      val sep = "\n\t- "
      val errString = sep + error.toList.mkString(sep)
      scribe.error(s"Cannot load config: $errString")
    case Right(config) =>
      Logging.setup(Logging.Config(id = "core", logstash = config.logstash))
      scribe.info(s"Starting wust with config: $config")
      Server.run(config)
  }
}
