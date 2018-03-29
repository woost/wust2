package wust.utilWeb

import scribe._
import scribe.format._
import scribe.writer.ConsoleWriter

object Logging {
  val logFormatter: Formatter = formatter"$levelPaddedRight $positionAbbreviated - $message$newLine"

  def setup(): Unit = {
    Logger.update(Logger.rootName) { l =>
      l.clearHandlers().withHandler(formatter = logFormatter, minimumLevel = Some(Level.Debug), writer = ConsoleWriter)
    }
  }
}
