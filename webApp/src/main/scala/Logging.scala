package wust.webApp

import scribe._
import scribe.format._
import scribe.writer.ConsoleWriter

object Logging {
  val fileBaseName = FormatBlock.FileName.map(fileName => fileName.split('/').last)
  val logFormatter: Formatter = {
    if(DevOnly.isTrue)
      formatter"$message$newLine"
    else
      formatter"$levelPaddedRight $fileBaseName:${FormatBlock.LineNumber} - $message$newLine"
  }

  //TODO setup api-logging as logwriter
  def setup(): Unit = {
    if (DevOnly.isTrue)
      Logger.root
        .clearHandlers()
        .withMinimumLevel(if (DevOnly.showDebugLogs) Level.Debug else Level.Info)
        .withHandler(
          formatter = logFormatter,
          writer = ConsoleWriter
        )
        .replace()
    else
      Logger.root
        .clearHandlers()
        .replace()
  }
}
