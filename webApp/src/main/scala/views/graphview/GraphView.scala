package wust.webApp.views.graphview

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import views.graphview.ForceSimulation
import wust.graph._
import wust.ids._
import wust.util._
import wust.utilWeb.GlobalState
import wust.utilWeb.outwatchHelpers._
import wust.utilWeb.views.View

import scala.scalajs.LinkingInfo


//TODO: remove disableSimulation argument, as it is only relevant for tests. Better solution?
class GraphView(disableSimulation: Boolean = false) extends View {
  override val key = "graph"
  override val displayName = "Mindmap"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = GraphView(state, state.displayGraphWithoutParents.map(_.graph))
}

object GraphView {
  def apply(state: GlobalState, graph:Rx[Graph], controls:Boolean = LinkingInfo.developmentMode)(implicit owner: Ctx.Owner) = {
    val forceSimulation = new ForceSimulation(state, graph, onDrop(state)( _, _), onDropWithCtrl(state)(_, _))
    state.jsErrors.foreach { _ => forceSimulation.stop() }

    div(
      height := "100%",
      controls.option {
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
        )
      },

      forceSimulation.component(
        forceSimulation.postCreationMenus.map(_.map { menu =>
          PostCreationMenu(state, menu, Var(forceSimulation.transform))
        }),

        forceSimulation.selectedPostId.map(_.map { case (pos, id) =>
          SelectedPostMenu(pos, id, state, forceSimulation.selectedPostId, Var(forceSimulation.transform))
        })
      )
    )
  }

  def onDrop(state: GlobalState)(dragging:PostId, target:PostId): Unit = {
    val graph = state.displayGraphWithoutParents.now.graph
    state.eventProcessor.changes.onNext(GraphChanges.moveInto(graph, dragging, target))
  }

  def onDropWithCtrl(state: GlobalState)(dragging:PostId, target:PostId): Unit = {
    val graph = state.displayGraphWithoutParents.now.graph
    state.eventProcessor.changes.onNext(GraphChanges.tagWith(graph, dragging, target))
  }
}



