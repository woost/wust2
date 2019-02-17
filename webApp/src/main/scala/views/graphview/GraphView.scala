package wust.webApp.views.graphview

import wust.webApp.dragdrop.{DragItem, DragTarget, DragPayload, DragActions, DragPayloadAndTarget}
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

    val forceSimulation = new ForceSimulation(state, focusState, onDrop(state)(_, _, _))

    val nodeStyle = PageStyle.ofNode(focusState.focusedId)

    div(
      position.relative, // for absolute positioned menu overlays
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
          button("stop", onClick foreach {
            forceSimulation.stop()
          }),
          button("step", onClick foreach {
            forceSimulation.step()
            ()
          }),
          button("reposition", onClick foreach {
            forceSimulation.reposition()
          })
        )
      },
      forceSimulation.component,
      forceSimulation.postCreationMenus.map(_.map { menu =>
        PostCreationMenu(state, focusState, menu, forceSimulation.transform)
      }),
      forceSimulation.selectedNodeId.map(_.map {
        case (pos, id) =>
          SelectedPostMenu(
            pos,
            id,
            state,
            focusState,
            forceSimulation.selectedNodeId,
            forceSimulation.transform
          )
      })
    )
  }

  private val roleToDragItem:PartialFunction[(NodeId, NodeRole), DragPayloadAndTarget] = {
    case (nodeId, NodeRole.Task) => DragItem.Task(nodeId)
    case (nodeId, NodeRole.Message) => DragItem.Message(nodeId)
    case (nodeId, NodeRole.Project) => DragItem.Project(nodeId)
    case (nodeId, NodeRole.Tag) => DragItem.Tag(nodeId)
  }

  def onDrop(state: GlobalState)(draggingId: NodeId, targetId: NodeId, ctrl: Boolean): Boolean = {
    val graph = state.graph.now

    def payload:Option[DragPayload] = { roleToDragItem.lift((draggingId,graph.nodesById(draggingId).role)) }
    def target:Option[DragTarget] = { roleToDragItem.lift((targetId, graph.nodesById(targetId).role)) }

    val changes = for {
      payload <- payload
      target <- target
      changes <- DragActions.dragAction.lift((payload, target, ctrl, false))
      
    } yield changes(graph, state.user.now.id)
    
    changes match {
      case Some(changes) => 
        state.eventProcessor.changes.onNext(changes)
        true
      case None => false
    } 
  }
}
