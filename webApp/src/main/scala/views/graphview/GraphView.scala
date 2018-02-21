package wust.webApp.views.graphview

import scala.scalajs.js.JSConverters._
import d3v4._
import io.circe.Decoder.state
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{Element, console, window}
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import vectory._
import views.graphview.ForceSimulation
import wust.webApp.Color._
import wust.webApp.views.View
import wust.webApp.{DevOnly, DevPrintln, GlobalState}
import wust.graph._
import wust.util.outwatchHelpers._
import wust.util.time.time
import wust.ids._

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import wust.webApp.views.Placeholders


//TODO: remove disableSimulation argument, as it is only relevant for tests. Better solution?
class GraphView(disableSimulation: Boolean = false)(implicit ec: ExecutionContext, owner: Ctx.Owner) extends View {
  override val key = "graph"
  override val displayName = "Mindmap"

  override def apply(state: GlobalState) = {
    val forceSimulation = new ForceSimulation(state, onDrop(state)( _, _), onDropWithCtrl(state)(_, _))
    state.jsErrors.foreach { _ => forceSimulation.stop() }

    div(
      height := "100%",
      backgroundColor <-- state.pageStyle.map(_.bgColor),

      div(
        position := "absolute",
        zIndex := 10,
        button("start", onClick --> sideEffect {
          forceSimulation.startAnimated()
        }),
        button("start hidden", onClick --> sideEffect {
          forceSimulation.startHidden()
        }),
        button("step", onClick --> sideEffect {
          forceSimulation.step()
          ()
        }),
        button("stop", onClick --> sideEffect {
          forceSimulation.stop()
        })
      ),

      forceSimulation.component(
        children <-- forceSimulation.postCreationMenus.map(_.map { menu =>
          PostCreationMenu(state, menu, Var(forceSimulation.transform))
        }).toObservable,

        child <-- forceSimulation.selectedPostId.map(_.map { case (pos, id) =>
          SelectedPostMenu(pos, id, state, forceSimulation.selectedPostId, Var(forceSimulation.transform))
        }).toObservable
      )
    )
  }

  def onDrop(state: GlobalState)(dragging:PostId, target:PostId): Unit = {
    val graph = state.inner.displayGraphWithoutParents.now.graph
    state.eventProcessor.changes.unsafeOnNext(GraphChanges.moveInto(graph, dragging, target))
  }

  def onDropWithCtrl(state: GlobalState)(dragging:PostId, target:PostId): Unit = {
    val graph = state.inner.displayGraphWithoutParents.now.graph
    state.eventProcessor.changes.unsafeOnNext(GraphChanges.tagWith(graph, dragging, target))
  }
}



