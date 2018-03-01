package wust.backend

import scribe._
import scribe.format._
import scribe.writer._
import wust.backend.config.Config

object Main extends App {
  //TODO: threadName.replaceFirst("server-akka.actor.default-dispatcher-", "")
  val logFormatter: Formatter = formatter"$date $levelPaddedRight [$threadName] $positionAbbreviated - $message$newLine"
  Logger.update(Logger.rootName) {
    _.clearHandlers()
      .withHandler(formatter = logFormatter, minimumLevel = Level.Debug, writer = ConsoleWriter)
      .withHandler(formatter = logFormatter, writer = FileNIOWriter.daily(), minimumLevel = Level.Info)
  }

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
