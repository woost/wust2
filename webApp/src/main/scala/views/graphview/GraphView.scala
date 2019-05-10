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
  private val roleToDragItemPayload:PartialFunction[(NodeId, NodeRole), DragPayload] = {
    case (nodeId, NodeRole.Tag) => DragItem.Tag(nodeId)
  }
  private val roleToDragItemTarget:PartialFunction[(NodeId, NodeRole), DragTarget] = {
    case (nodeId, NodeRole.Task) => DragItem.Task(nodeId)
    case (nodeId, NodeRole.Message) => DragItem.Message(nodeId)
    case (nodeId, NodeRole.Project) => DragItem.Project(nodeId)
    case (nodeId, NodeRole.Tag) => DragItem.Tag(nodeId)
  }

  def apply(state: GlobalState, focusState: FocusState)(implicit owner: Ctx.Owner) = {

    val forceSimulation = new ForceSimulation(state, focusState, onDrop(state)(_, _, _), roleToDragItemPayload, roleToDragItemTarget)

    val nodeStyle = PageStyle.ofNode(focusState.focusedId)
    val showControls = Var(LinkingInfo.developmentMode)

    div(
      position.relative, // for absolute positioned menu overlays
      emitter(state.jsErrors).foreach { _ =>
        forceSimulation.stop()
      },

      overflow.auto, // fits graph visualization perfectly into view

      backgroundColor := nodeStyle.bgLightColor,
      Rx{
        VDomModifier.ifTrue(showControls())(
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
            }),
            button("hide controls", onClick foreach {
              showControls() = false
            })
          )
        )
      },
      div(
        position := "absolute",
        top := "5px",
        right := "5px",
        zIndex := ZIndex.controls,
        button("Auto align", cls := "ui secondary button", onMouseDown foreach {
          forceSimulation.startAnimated()
          forceSimulation.simData.alphaDecay = 0
        }, onMouseUp foreach {
          forceSimulation.startAnimated()
        }),
      ),
      forceSimulation.component,
      forceSimulation.postCreationMenus.map(_.map { menu =>
        PostCreationMenu(state, focusState, menu, forceSimulation.transform)
      }),
    )
  }

  def onDrop(state: GlobalState)(draggingId: NodeId, targetId: NodeId, ctrl: Boolean): Boolean = {
    val graph = state.graph.now

    def payload:DragPayload = { roleToDragItemPayload.applyOrElse((draggingId, graph.nodesByIdOrThrow(draggingId).role), (_: (NodeId, NodeRole)) => DragItem.DisableDrag) }
    def target:DragTarget = { roleToDragItemTarget.applyOrElse((targetId, graph.nodesByIdOrThrow(targetId).role), (_: (NodeId, NodeRole)) => DragItem.DisableDrag) }

    val changes = for {
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
