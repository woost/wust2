package wust.frontend

import boopickle.Default._
import org.scalajs.dom._
import wust.util.{Analytics, RichFuture}
import wust.api.{ApiEvent, Authentication}
import wust.ids._
import wust.graph.{Graph, Page}
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._

import monix.execution.Scheduler.Implicits.global
import scala.util.Success
import concurrent.Future
import wust.util.outwatchHelpers._
import rx.Ctx

object Main {

  def main(args: Array[String]): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val state = new GlobalState()

    def getNewGraph(selection: Page) = {
      //TODO ???
      // Client.api.getGraph(selection).foreach { newGraph =>
      //   val oldSelectionGraph = selection match {
      //     case GraphSelection.Union(ids) => state.rawGraph.now.filter(ids)
      //     case _                         => Graph.empty
      //   }

      //   //TODO problem with concurrent get graph and create post. for now try to partly recover from current graph.
      //   val newNonEmptyGraph = oldSelectionGraph + newGraph

      //   val newCollapsedPostIds: Set[PostId] = if (selection == GraphSelection.Root && state.viewPage.now == views.ViewPage.Graph) {
      //     // on the frontpage all posts are collapsed per default
      //     state.collapsedPostIds.now ++ newGraph.postsById.keySet.filter(p => newGraph.hasChildren(p) && !newGraph.hasParents(p))
      //   } else Set.empty

      //   Var.set(
      //     VarTuple(state.collapsedPostIds, newCollapsedPostIds),
      //     // take changes into account, when we get a new graph
      //     VarTuple(state.rawGraph, newNonEmptyGraph applyChanges state.persistence.currentChanges)
      //   )
      // }
    }

    state.viewConfig.scan((views.ViewConfig.default, views.ViewConfig.default))((p, c) => (p._2, c)).foreach {
      case (prevViewConfig, viewConfig) =>
        viewConfig.invite foreach { token =>
          Client.api.acceptGroupInvite(token).onComplete {
            case Success(Some(_)) =>
              Analytics.sendEvent("group", "invitelink", "success")
            case failedResult =>
              println(s"Failed to accept group invite: $failedResult")
              Analytics.sendEvent("group", "invitelink", "failure")
          }
        }

        if (prevViewConfig.page != viewConfig.page) getNewGraph(viewConfig.page)
    }

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
