package wust.utilBackend

import scribe._
import scribe.format._
import scribe.writer._
import java.io.File

object Logging {
  val shortThreadName = threadName.map(_.replaceFirst("server-akka.actor.default-dispatcher-", ""))
  val shortLevel = level.map(_.trim)
  val fileBaseName = FormatBlock.FileName.map(fileName => fileName.split('/').last)
  val simpleFormatter = formatter"$fileBaseName:${FormatBlock.LineNumber} - $message$newLine"
  val detailFormatter = formatter"$date $shortLevel [$shortThreadName] $fileBaseName - $message$newLine"

  def setup(): Unit = {
    Logger.update(Logger.rootName) {
      _.clearHandlers()
        .withHandler(formatter = simpleFormatter, minimumLevel = None, writer = ConsoleWriter)
        .withHandler(formatter = detailFormatter, minimumLevel = Some(Level.Info), writer = FileNIOWriter.daily())
    }
  }
}
