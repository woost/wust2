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
import wust.webApp.state.{FocusState, GlobalState, PageStyle}

import scala.scalajs.LinkingInfo

object GraphView {
  def apply(state: GlobalState, focusState: FocusState, controls: Boolean = LinkingInfo.developmentMode)(implicit owner: Ctx.Owner) = {

    val forceSimulation = new ForceSimulation(state, focusState, onDrop(state)(_, _), onDropWithCtrl(state)(_, _))

    val nodeStyle = PageStyle.ofNode(focusState.focusedId)

    div(
      emitter(state.jsErrors).foreach { _ =>
        forceSimulation.stop()
      },

      overflow.auto, // fits graph visualization perfectly into view

      backgroundColor := nodeStyle.bgLightColor,
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
          PostCreationMenu(state, focusState, menu, Var(forceSimulation.transform))
        }),
        forceSimulation.selectedNodeId.map(_.map {
          case (pos, id) =>
            SelectedPostMenu(
              pos,
              id,
              state,
              focusState,
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
