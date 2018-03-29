package wust.utilBackend

import scribe._
import scribe.format._
import scribe.writer._

object Logging {
  val shortThreadName = threadName.map(_.replaceFirst("server-akka.actor.default-dispatcher-", ""))
  val shortLevel = levelPaddedRight.map(_.trim)
  val simpleFormatter = formatter"$positionAbbreviated - $message$newLine"
  val detailFormatter = formatter"$date $shortLevel [$shortThreadName] $positionAbbreviated - $message$newLine"

  def setup(): Unit = {
    Logger.update(Logger.rootName) {
      _.clearHandlers()
        .withHandler(formatter = simpleFormatter, minimumLevel = Level.Debug, writer = ConsoleWriter)
        .withHandler(formatter = detailFormatter, minimumLevel = Level.Info, writer = FileNIOWriter.daily())
    }
  }
}
