package wust.util

import scribe._
import scribe.format._
import scribe.writer._

object Logging {
  val shortThreadName = threadName.map(_.replaceFirst("server-akka.actor.default-dispatcher-", ""))
  val shortLevel = level.map(_.trim)
  val fileBaseName = FormatBlock.FileName.map(fileName => fileName.split('/').last)
  val simpleFormatter = formatter"${scribe.format.time} $fileBaseName:${FormatBlock.LineNumber} - $message$newLine"
  val detailFormatter =
    formatter"${scribe.format.time} $shortLevel [$shortThreadName] $fileBaseName - $message$newLine"

  def setup(): Unit = {
    Logger.root
      .clearHandlers()
      .withHandler(formatter = simpleFormatter, minimumLevel = None, writer = ConsoleWriter)
      .withHandler(
        formatter = detailFormatter,
        minimumLevel = Some(Level.Info),
        writer = FileWriter.date()
      )
      .replace()
  }
}
