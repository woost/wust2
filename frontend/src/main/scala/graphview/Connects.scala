package frontend.graphview

import frontend._

import graph._
import math._

import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.d3v4._
import org.scalajs.dom

import vectory._
import util.collectionHelpers._

class ConnectionLineSelection(container: Selection[dom.EventTarget])(implicit env: GraphState)
  extends DataSelection[SimConnects](container, "line", keyFunction = Some((p: SimConnects) => p.id)) {
  import env._
  import postSelection.postIdToSimPost

  def update(connections: Iterable[Connects]) {
    val newData = graph.connections.values.map { c =>
      new SimConnects(c, postIdToSimPost(c.sourceId))
    }.toJSArray

    val connIdToSimConnects: Map[AtomId, SimConnects] = (newData: js.ArrayOps[SimConnects]).by(_.id)

    newData.foreach { e =>
      e.target = postIdToSimPost.getOrElse(e.targetId, connIdToSimConnects(e.targetId))
    }

    update(newData)
  }
  override def enter(line: Selection[SimConnects]) {
    line
      .style("stroke", "#8F8F8F")
  }

  override def drawCall(line: Selection[SimConnects]) {
    line
      .attr("x1", (e: SimConnects) => e.source.x)
      .attr("y1", (e: SimConnects) => e.source.y)
      .attr("x2", (e: SimConnects) => e.target.x)
      .attr("y2", (e: SimConnects) => e.target.y)
  }
}

class ConnectionElementSelection(container: Selection[dom.EventTarget])(implicit env: GraphState)
  extends DataSelection[SimConnects](container, "div", keyFunction = Some((p: SimConnects) => p.id)) {
  import env._

  override def enter(element: Selection[SimConnects]) {
    element
      .style("position", "absolute")
      .style("font-size", "20px")
      .style("margin-left", "-0.5ex")
      .style("margin-top", "-0.5em")
      .text("\u00d7")
      .style("pointer-events", "auto") // reenable
      .style("cursor", "pointer")
      .on("click", { (e: SimConnects) =>
        import autowire._
        import boopickle.Default._

        Client.api.deleteConnection(e.id).call()
      })

  }

  override def drawCall(element: Selection[SimConnects]) {
    element
      .style("left", (e: SimConnects) => s"${e.x.get}px")
      .style("top", (e: SimConnects) => s"${e.y.get}px")

  }
}
