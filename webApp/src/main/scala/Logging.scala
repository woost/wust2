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

  def setup(): Unit = setup(DevOnly.isTrue, DevOnly.showDebugLogs)

  //TODO setup api-logging as logwriter
  def setup(enabled: Boolean, debugEnabled: Boolean = false): Unit = {
    if (enabled)
      Logger.root
        .clearHandlers()
        .withMinimumLevel(if (debugEnabled) Level.Debug else Level.Info)
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
