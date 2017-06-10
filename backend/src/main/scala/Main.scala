package wust.backend

import config.Config
import scribe._
import scribe.formatter.FormatterBuilder
import scribe.writer.ConsoleWriter

object Main extends App {
  val formatter = FormatterBuilder()
    .date()
    .string(" ")
    .levelPaddedRight
    .string(": ")
    .message.newLine

  Logger.root.clearHandlers()
  Logger.root.addHandler(LogHandler(Level.Info, formatter, ConsoleWriter))

  scribe.info(s"Starting wust with Config: $Config")
  Server.run(8080)
}
