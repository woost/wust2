package wust.webApp

import org.scalajs.dom._
import wust.api.{ApiEvent, Authentication}
import wust.ids._
import wust.graph.{Graph, Page}
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._

import scribe._
import scribe.formatter.FormatterBuilder
import scribe.writer.ConsoleWriter

import scala.util.Success
import concurrent.Future
import wust.utilWeb.views._
import wust.utilWeb._
import wust.webApp.views.graphview._
import wust.webApp.views._
import wust.utilWeb.outwatchHelpers._
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

  def main(args: Array[String]): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    View.list =
      ChatView ::
      //    BoardView ::
      //    MyBoardView ::
      ArticleView ::
      UserSettingsView ::
      //    CodeView ::
      //    ListView ::
      new GraphView() ::
      // TestView ::
      Nil

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
