package wust.utilWeb.views

import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import wust.sdk.PostColor._
import rx._
import wust.ids._
import wust.graph._
import wust.sdk.{ChangesHistory, SyncMode}
import wust.util.RichBoolean
import wust.utilWeb._
import wust.utilWeb.outwatchHelpers._

import scala.scalajs.js.Date

object MainViewParts {
  val titleBanner: VNode = {
      div(
        "Woost",
        fontWeight.bold,
        fontSize := "20px",
        marginBottom := "10px"
      )
  }

  def upButton(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    span(
      state.upButtonTargetPage.map(_.toSeq.map(upTarget =>
          button("↑", width := "2.5em", onClick(upTarget) --> state.page.toHandler)
      ))
    )
  }

  def syncStatus(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
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
          onClick.map(_ => (if (mode == SyncMode.Live) SyncMode.Local else SyncMode.Live):SyncMode) --> state.syncMode
        )
      },
      ")",
      state.eventProcessor.changesInTransit.map { changes =>
        span(
          " ⬤ ", // middle dot
          if (changes.isEmpty) Seq(color := "green", title := "Everything is synchronized.")
          else Seq(color := "blue", title := "Some changes are only local, just wait until they are send online.")
        )
      }
    )
  }

  def login(state: GlobalState)(implicit ctx:Ctx.Owner) = button("Login", onClick(LoginView: View) --> state.view)
  val logout = button("Logout", onClick --> sideEffect { Client.auth.logout(); () })

  def authentication(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = div(
    state.currentUser.map {
      case user: User.Assumed => login(state)
      case user: User.Implicit => login(state)
      case user: User.Real => logout
    }
  )

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
      val sameNamePosts = state.highLevelPosts.now.filter(_.content.startsWith(title))
      if (sameNamePosts.isEmpty) title
      else s"$title (${sameNamePosts.size})"
    }
    button("New Group",
      onClick --> sideEffect{ _ =>
        val post = Post(groupTitle, state.currentUser.now.id)
        for {
          _ <- state.eventProcessor.changes.onNext(GraphChanges.addPost(post))
        } {
          state.view() = View.default
          state.page() = Page(post.id)
          state.highLevelPosts.update(post :: _)
        }

        ()
      })
  }

  def channels(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      color := "#C4C4CA",
      Rx {
        state.highLevelPosts().map{p => div(
          padding := "5px 3px",
          p.content,
          cursor.pointer,
          onChannelClick(p.id)(state),
          title := p.id,
          if(state.page().parentIds.contains(p.id)) Seq(
            color := state.pageStyle().darkBgColor.toHex,
            backgroundColor := state.pageStyle().darkBgColorHighlight.toHex)
          else Option.empty[VDomModifier]
        )}
      }
    )
  }

  def channelIcons(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      color := "#C4C4CA",
      Rx {
        state.highLevelPosts().map{p => div(
          margin := "0",
          padding := "0",
          width := "30px",
          height := "30px",
          cursor.pointer,
          backgroundColor := baseColor(p.id).toHex,
          onChannelClick(p.id)(state),
          state.page().parentIds.contains(p.id).ifTrueSeq(Seq(
            borderLeft := "4px solid",
            borderColor := state.pageStyle().bgColor.toHex))
        )}
      }
    )
  }

  private def onChannelClick(id: PostId)(state: GlobalState)(implicit ctx: Ctx.Owner) = onClick.map { e =>
    val page = state.page.now
    //TODO if (e.shiftKey) {
    val newParents = if (e.ctrlKey) {
      val filtered = page.parentIds.filterNot(_ == id)
      if (filtered.size == page.parentIds.size) page.parentIds :+ id
      else if (filtered.nonEmpty) filtered
      else Seq(id)
    } else Seq(id)

    page.copy(parentIds = newParents)
    } --> sideEffect { page =>
      state.view() = View.default
      state.page() = page
      //TODO: Why does Var.set not work?
      // Var.set(
      //   state.page -> page,
      //   state.view -> view
      // )
    }
}
