package wust.utilWeb.views

import wust.utilWeb._
import wust.sdk.{ChangesHistory, SyncMode}
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.events.window
import rx._
import wust.api._
import wust.sdk.PostColor._
import wust.utilWeb.outwatchHelpers._
import outwatch.ObserverSink
import scala.scalajs.js.Date
import wust.graph._

object MainViewParts {
  val titleBanner: VNode = {
      div(
        "Woost",
        fontWeight.bold,
        fontSize := "20px",
        marginBottom := "10px"
      )
  }

  def upButton(state: GlobalState): VNode = {
    //TODO: outwatch child <-- Option[VNode]
    span(
      children <-- state.upButtonTargetPage.map(_.toSeq.map(upTarget =>
          button("↑", width := "2.5em", onClick(upTarget) --> state.page)
      ))
    )
  }

  def userStatus(state: GlobalState): VNode = {
    div( "User: ", child <-- state.currentUser.map(u => s"${u.id}" ))
  }

  def syncStatus(state: GlobalState): VNode = {
    val isOnline = Observable.merge(Client.observable.connected.map(_ => SyncMode.Live), Client.observable.closed.map(_ => SyncMode.Offline))
    div(
      managed(state.syncMode <--isOnline),
      child <-- state.syncMode.map { mode =>
        span(
          mode.toString,
          color := (if (mode == SyncMode.Live) "white" else "red"),
          title := "Click to switch syncing modes (Live/Offline)",
          cursor.pointer,
          onClick.map(_ => if (mode == SyncMode.Live) SyncMode.Offline else SyncMode.Live) --> state.syncMode
        )
      },
      child <-- state.eventProcessor.areChangesSynced.map { synced =>
        span(
          " ⬤ ", // middle dot
          color := (if (synced) "green" else "blue"),
          title := (if (synced) "Everything is synced" else "Some changes are only local, just wait until they are send online."),
        )
      },
    )
  }

  def undoRedo(state:GlobalState):VNode = {
    val historySink = ObserverSink(state.eventProcessor.history.action)
    div(
      child <-- state.eventProcessor.changesHistory.startWith(Seq(ChangesHistory.empty)).map { history =>
        div(
          display.flex,
          style("justify-content") := "space-evenly",
          button("Undo", title := "Undo last change", onClick(ChangesHistory.Undo) --> historySink, disabled := !history.canUndo),
          button("Redo", title := "Redo last undo change", onClick(ChangesHistory.Redo) --> historySink, disabled := !history.canRedo)
        )
      }
    )
  }

  def newGroupButton(state: GlobalState)(implicit ctx:Ctx.Owner) = {
    def suffix = {
      var today = new Date()
      // January is 0!
      s"${today.getMonth+1}-${today.getDate}"
    }
    button( "new group",
      onClick --> sideEffect{ _ =>
        val post = Post("new group " + suffix, state.inner.currentUser.now.id)
        for {
          _ <- state.eventProcessor.changes.onNext(GraphChanges.addPost(post))
        } {
          state.inner.page() = Page(post.id)
          state.inner.highLevelPosts.update(post :: _)
        }

        ()
      })
  }


}
