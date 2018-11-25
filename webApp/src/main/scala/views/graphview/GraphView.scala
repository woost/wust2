package wust.webApp.views.graphview

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import views.graphview.ForceSimulation
import wust.css.ZIndex
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState

import scala.scalajs.LinkingInfo

object GraphView {
  def apply(state: GlobalState, controls: Boolean = LinkingInfo.developmentMode)(implicit owner: Ctx.Owner) = {

    val forceSimulation = new ForceSimulation(state, onDrop(state)(_, _), onDropWithCtrl(state)(_, _))

    state.jsErrors.foreach { _ =>
      forceSimulation.stop()
    }

    div(
      overflow.auto, // fits graph visualization perfectly into view

      backgroundColor <-- state.pageStyle.map(_.bgLightColor),
      controls.ifTrueOption {
        div(
          position := "absolute",
          zIndex := ZIndex.controls,
          button("start", onMouseDown foreach {
            forceSimulation.startAnimated()
            forceSimulation.simData.alphaDecay = 0
          }, onMouseUp foreach {
            forceSimulation.startAnimated()
          }),
          button("play", onClick foreach {
            forceSimulation.startAnimated()
            forceSimulation.simData.alphaDecay = 0
          }),
          button("start hidden", onClick foreach {
            forceSimulation.startHidden()
          }),
          button("step", onClick foreach {
            forceSimulation.step()
            ()
          }),
          button("stop", onClick foreach {
            forceSimulation.stop()
          })
        )
      },
      forceSimulation.component(
        forceSimulation.postCreationMenus.map(_.map { menu =>
          PostCreationMenu(state, menu, Var(forceSimulation.transform))
        }),
        forceSimulation.selectedNodeId.map(_.map {
          case (pos, id) =>
            SelectedPostMenu(
              pos,
              id,
              state,
              forceSimulation.selectedNodeId,
              Var(forceSimulation.transform)
            )
        })
      )
    )
  }

  def onDrop(state: GlobalState)(dragging: NodeId, target: NodeId): Unit = {
    val graph = state.graph.now
    state.eventProcessor.changes.onNext(GraphChanges.moveInto(graph, dragging, target))
  }

  def onDropWithCtrl(state: GlobalState)(dragging: NodeId, target: NodeId): Unit = {
    val graph = state.graph.now
    state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Parent)(dragging, target))
  }
}
