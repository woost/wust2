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
    val historySink = ObserverSink(state.eventProcessor.history.action)
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
      child <-- state.eventProcessor.changesHistory.startWith(Seq(ChangesHistory.empty)).map { history =>
        span(
          button("Undo", title := "Undo last change", onClick(ChangesHistory.Undo) --> historySink, disabled := !history.canUndo),
          button("Redo", title := "Redo last undo change", onClick(ChangesHistory.Redo) --> historySink, disabled := !history.canRedo)
        )
      }
    )
  }
}
