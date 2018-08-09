package wust.webApp

import scribe._
import scribe.format._
import scribe.writer.ConsoleWriter
import wust.graph.GraphChanges

object Logging {
  val fileBaseName = FormatBlock.FileName.map(fileName => fileName.split('/').last)
  val logFormatter: Formatter =
    formatter"$levelPaddedRight $fileBaseName:${FormatBlock.LineNumber} - $message$newLine"

  def setup(): Unit = {
    Logger.root
      .clearHandlers()
      .withHandler(
        formatter = logFormatter,
        minimumLevel = Some(Level.Debug),
        writer = ConsoleWriter
      )
      .replace()
  }
}
