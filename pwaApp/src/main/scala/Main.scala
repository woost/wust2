package wust.pwaApp

import org.scalajs.dom._
import scala.scalajs.js.Dynamic.global
import wust.util.{Analytics, RichFuture}
import wust.api.{ApiEvent, Authentication}
import wust.ids._
import wust.graph.{Graph, Page}
import outwatch.dom._, dsl._

import scribe._
import scribe.formatter.FormatterBuilder
import scribe.writer.ConsoleWriter

import wust.util.outwatchHelpers._
import rx.Ctx

object Main {
  val formatter = FormatterBuilder()
    .date(format = "%1$tT:%1$tL")
    .string(" ")
    .levelPaddedRight
    .string(": ")
    .message.newLine

  Logger.root.clearHandlers()
  Logger.root.addHandler(LogHandler(Level.Info, formatter, ConsoleWriter))

  // require default passive events for scroll/mouse/touch events
  // global.require("default-passive-events")

  def main(args: Array[String]): Unit = {
    OutWatch.renderReplace("#container", div("HALLO")).unsafeRunSync()
  }
}
