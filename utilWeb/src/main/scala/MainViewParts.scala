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
    span(
      state.upButtonTargetPage.map(_.toSeq.map(upTarget =>
          button("↑", width := "2.5em", onClick(upTarget) --> state.page)
      ))
    )
  }

  def userStatus(state: GlobalState): VNode = {
    div( "User: ", state.currentUser.map(u => s"${u.id}" ))
  }

  def syncStatus(state: GlobalState): VNode = {
    val isOnline = Observable.merge(Client.observable.connected.map(_ => true), Client.observable.closed.map(_ => false))
    div(
      isOnline.map { isOnline =>
        span(
          if (isOnline) Seq(asVDomModifier("On"), color := "white", title := "The connection to the server is established.")
          else Seq(asVDomModifier("Off"), color := "red", title := "The connection to the server has stopped. Will try to reconnect.")
        )
      },
      " (",
      state.syncMode.map { mode =>
        span(
          mode.toString,
          cursor.pointer,
          title := "Click to switch syncing mode (Live/Local). Live mode automatically synchronizes all changes online. Local mode will keep all your changes locally and hide incoming events.",
          if (mode == SyncMode.Live) Seq(color := "white")
          else Seq(color := "grey"),
          onClick.map(_ => if (mode == SyncMode.Live) SyncMode.Local else SyncMode.Live) --> state.syncMode
        )
      },
      ")",
      state.eventProcessor.areChangesSynced.map { synced =>
        span(
          " ⬤ ", // middle dot
          if (synced) Seq(color := "green", title := "Everything is synchronized.")
          else Seq(color := "blue", title := "Some changes are only local, just wait until they are send online.")
        )
      }
    )
  }

  def undoRedo(state:GlobalState):VNode = {
    val historySink = ObserverSink(state.eventProcessor.history.action)
    div(
      state.eventProcessor.changesHistory.startWith(Seq(ChangesHistory.empty)).map { history =>
        div(
          display.flex,
          style("justify-content") := "space-evenly",
          button("Undo", title := "Undo last change", onClick(ChangesHistory.Undo) --> historySink, disabled := !history.canUndo),
          button("Redo", title := "Redo last undo change", onClick(ChangesHistory.Redo) --> historySink, disabled := !history.canRedo)
        )
      }
    )
  }

  def newGroupButton(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    def groupTitle = {
      var today = new Date()
      // January is 0!
      val title = s"Group: ${today.getMonth+1}-${today.getDate}"
      val sameNamePosts = state.inner.highLevelPosts.now.filter(_.content.startsWith(title))
      if (sameNamePosts.isEmpty) title
      else s"$title (${sameNamePosts.size})"
    }
    button("New Group",
      onClick --> sideEffect{ _ =>
        val post = Post(groupTitle, state.inner.currentUser.now.id)
        for {
          _ <- state.eventProcessor.changes.onNext(GraphChanges.addPost(post))
        } {
          state.inner.page() = Page(post.id)
          state.inner.highLevelPosts.update(post :: _)
        }

        ()
      })
  }

  def channels(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      color := "#C4C4CA",
      Rx {
        state.inner.highLevelPosts().map{p => div(
          padding := "5px 3px",
          p.content,
          cursor.pointer,
          onClick(Page(p.id)) --> state.page,
          title := p.id,
          if(state.inner.page().parentIds.contains(p.id)) Seq(
            color := state.inner.pageStyle().darkBgColor.toHex,
            backgroundColor := state.inner.pageStyle().darkBgColorHighlight.toHex)
          else Option.empty[VDomModifier]
        )}
      }.toObservable
    )
  }


}
