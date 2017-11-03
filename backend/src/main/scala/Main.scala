package wust.backend

import scribe._
import scribe.formatter.FormatterBuilder
import scribe.writer.ConsoleWriter
import wust.backend.config.Config

object Main extends App {
  val formatter = FormatterBuilder()
    .date()
    .string(" ")
    .levelPaddedRight
    .string(": ")
    .message.newLine

  Logger.root.clearHandlers()
  Logger.root.addHandler(LogHandler(Level.Info, formatter, ConsoleWriter))

  Config.load match {
    case Left(error) =>
      val sep = "\n\t- "
      val errString = sep + error.toList.mkString(sep)
      scribe.error(s"Cannot load config: $errString")
    case Right(config) =>
      scribe.info(s"Starting wust with Config: $config")
      Server.run(config)
  }
}
