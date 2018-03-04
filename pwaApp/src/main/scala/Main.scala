package wust.pwaApp

import org.scalajs.dom._
import scala.scalajs.js.Dynamic.global
import wust.api.{ApiEvent, Authentication}
import wust.ids._
import wust.graph.{Graph, Page}
import outwatch.dom._, dsl._

import scribe._
import scribe.formatter.FormatterBuilder
import scribe.writer.ConsoleWriter

import wust.utilWeb.outwatchHelpers._
import wust.utilWeb.GlobalState
import wust.utilWeb.views._
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

  // require offline plugin, setup in webpack
  OfflinePlugin.install()

  // require default passive events for scroll/mouse/touch events
  // global.require("default-passive-events")

  def main(args: Array[String]): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val state = new GlobalState

    val view = View.default.apply(state)
    OutWatch.renderReplace("#container", view).unsafeRunSync()
  }
}
