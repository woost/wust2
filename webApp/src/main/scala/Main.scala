package wust.webApp

import org.scalajs.dom._
import wust.util.{Analytics, RichFuture}
import wust.api.{ApiEvent, Authentication}
import wust.ids._
import wust.graph.{Graph, Page}
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._

import scribe._
import scribe.format._
import scribe.writer.ConsoleWriter

import scala.util.Success
import concurrent.Future
import wust.util.outwatchHelpers._
import rx.Ctx

object Main {
  val logFormatter: Formatter = formatter"$levelPaddedRight $positionAbbreviated - $message$newLine"
  Logger.update(Logger.rootName) { l =>
    l.clearHandlers().withHandler(formatter = logFormatter, minimumLevel = Level.Debug, writer = ConsoleWriter)
  }

  def main(args: Array[String]): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val state = new GlobalState()

    state.inviteToken.foreach(_.foreach { token =>
      Client.api.acceptGroupInvite(token).onComplete {
        case Success(Some(_)) =>
          Analytics.sendEvent("group", "invitelink", "success")
        case failedResult =>
          println(s"Failed to accept group invite: $failedResult")
          Analytics.sendEvent("group", "invitelink", "failure")
      }
    })

    Client.api.log("Starting web app")

    OutWatch.renderReplace("#container", views.MainView(state)).unsafeRunSync()

    //TODO: create global keyevent observer (in outwatch?):
    // document.onkeypress = { (e: KeyboardEvent) =>
    //   if (e.keyCode == KeyCode.Escape) {
    //     Var.set(
    //       VarTuple(state.focusedPostId, None),
    //       VarTuple(state.postCreatorMenus, Nil)
    //     )
    //   }
    // }
  }
}
