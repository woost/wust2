package wust.webApp

import scribe._
import scribe.format._
import scribe.writer.ConsoleWriter
import scala.scalajs.LinkingInfo

object Logging {
  val fileBaseName = FormatBlock.FileName.map(fileName => fileName.split('/').last)
  val logFormatter: Formatter =
    formatter"$levelPaddedRight $fileBaseName:${FormatBlock.LineNumber} - $message$newLine"

  def setup(): Unit = {
    if (LinkingInfo.developmentMode)
      Logger.root
        .clearHandlers()
        .withHandler(
          formatter = logFormatter,
          minimumLevel = Some(Level.Debug),
          writer = ConsoleWriter
        )
        .replace()
    else
      Logger.root
        .clearHandlers()
        .replace()
  }
}
