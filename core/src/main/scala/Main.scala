package wust.backend

import wust.backend.config.Config
import wust.utilBackend.Logging

object Main extends App {
  Logging.setup()

  Styles.Build()

  Config.load match {
    case Left(error) =>
      val sep = "\n\t- "
      val errString = sep + error.toList.mkString(sep)
      scribe.error(s"Cannot load config: $errString")
    case Right(config) =>
      scribe.info(s"Starting wust with config: $config")
      Server.run(config)
  }
}
