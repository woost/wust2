package wust.webApp

import scribe._
import scribe.format._
import scribe.writer.ConsoleWriter

object Logging {
  val fileBaseName = FormatBlock.FileName.map(fileName => fileName.split('/').last)
  val logFormatter: Formatter = formatter"$levelPaddedRight $fileBaseName:${FormatBlock.LineNumber} - $message$newLine"

  def setup(): Unit = {
    Logger.update(Logger.rootName) { l =>
      l.clearHandlers().withHandler(formatter = logFormatter, minimumLevel = Some(Level.Debug), writer = ConsoleWriter)
    }
  }
}
