package wust.webApp.views.graphview

import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import rx._
import wust.css.ZIndex
import wust.ids._
import wust.webApp.dragdrop.{DragActions, DragItem, DragPayload, DragTarget}
import wust.webApp.state.{FocusState, GlobalState}
import wust.webUtil.outwatchHelpers._

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

  def apply(focusState: FocusState)(implicit owner: Ctx.Owner) = {

    val forceSimulation = new ForceSimulation( focusState, onDrop(_, _, _), roleToDragItemPayload, roleToDragItemTarget)

    val showControls = Var(LinkingInfo.developmentMode)

    div(
      keyed,
      position.relative, // for absolute positioned menu overlays

      overflow.auto, // fits graph visualization perfectly into view

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
        PostCreationMenu( focusState, menu, forceSimulation.transform)
      }),
    )
  }

  def onDrop(draggingId: NodeId, targetId: NodeId, ctrl: Boolean): Boolean = {
    val graph = GlobalState.graph.now

    def payload:DragPayload = { roleToDragItemPayload.applyOrElse((draggingId, graph.nodesByIdOrThrow(draggingId).role), (_: (NodeId, NodeRole)) => DragItem.DisableDrag) }
    def target:DragTarget = { roleToDragItemTarget.applyOrElse((targetId, graph.nodesByIdOrThrow(targetId).role), (_: (NodeId, NodeRole)) => DragItem.DisableDrag) }

    val changes = for {
      changes <- DragActions.dragAction.lift((payload, target, ctrl, false))
    } yield changes(graph, GlobalState.user.now.id)

    changes match {
      case Some(changes) =>
        GlobalState.submitChanges(changes)
        true
      case None => false
    }
  }
}
