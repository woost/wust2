package wust.pwaApp

import org.scalajs.dom._

import scala.scalajs.js.Dynamic.global
import wust.api.{ApiEvent, Authentication}
import wust.ids._
import wust.graph.{Graph, GraphChanges, Page}
import wust.utilWeb._
import outwatch.dom._
import dsl._
import wust.utilWeb.outwatchHelpers._
import wust.utilWeb.views._
import wust.utilWeb.Logging
import rx._
import cats._

object Main {
  Logging.setup()

  // require offline plugin, setup in webpack
  val updateReady:Rx[Eval[Unit]] = Rx.create(Eval.Unit) { ready =>
    OfflinePlugin.install(new OfflinePluginConfig {
      def onUpdating(): Unit = {
        scribe.info("SW: onUpdating")
      }
      def onUpdateReady(): Unit = {
        // fires when all required assets are downloaded and ready to be updated. In this callback, you can either call runtime.applyUpdate() to apply updates directly, or in some way prompt for user input, and then apply them.
        scribe.info("SW: onUpdateReady")
        OfflinePlugin.applyUpdate()
      }
      def onUpdated(): Unit = {
        scribe.info("SW: onUpdated")
        //TODO: better update strategy
        //TODO: how is the update interval configured?
        ready() = Eval.later( window.location.reload() )
      }
      def onUpdateFailed(): Unit = {
        scribe.info("SW: onUpdateFailed")
      }
    })
  }

  // require default passive events for scroll/mouse/touch events
  // global.require("default-passive-events")

  def main(args: Array[String]): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val state = new GlobalState(updateReady)

    View.list =
      ChatView ::
      LoginView ::
      Nil

    state.currentAuth.foreach(IndexedDbOps.storeAuth)

    Client.observable.event.foreach { events =>
      val changes = events.collect { case ApiEvent.NewGraphChanges(changes) => changes }.foldLeft(GraphChanges.empty)(_ merge _)
      if (changes.addPosts.nonEmpty) {
        val msg = if (changes.addPosts.size == 1) "New Post" else s"New Post (${changes.addPosts.size})"
        val body = changes.addPosts.map(_.content).mkString(", ")
        Notifications.notify(msg, body = Some(body), tag = Some("new-post"))
      }
    }

    OutWatch.renderReplace("#container", MainView(state)).unsafeRunSync()
  }
}
