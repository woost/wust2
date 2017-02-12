package frontend.graphview

import frontend._

import graph._
import math._

import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.d3v4._
import org.scalajs.dom
import mhtml._

import vectory._
import util.collectionHelpers._

object ConnectionLineSelection {
  def apply(container: Selection[dom.EventTarget], rxPosts: RxPosts, rxGraph: Rx[Graph]) = {
    import rxPosts.postIdToSimPost
    val rxData = for {
      graph <- rxGraph
      postIdToSimPost <- postIdToSimPost
    } yield {

      val newData = graph.connections.values.map { c =>
        new SimConnects(c, postIdToSimPost(c.sourceId))
      }.toJSArray

      val connIdToSimConnects: Map[AtomId, SimConnects] = (newData: js.ArrayOps[SimConnects]).by(_.id)

      // set hyperedge targets, goes away with custom linkforce
      newData.foreach { e =>
        e.target = postIdToSimPost.getOrElse(e.targetId, connIdToSimConnects(e.targetId))
      }

      newData
    }

    new ConnectionLineSelection(container, rxData)

  }
}

class ConnectionLineSelection(
  container: Selection[dom.EventTarget],
  rxData: Rx[js.Array[SimConnects]]
)
  extends RxDataSelection[SimConnects](container, "line", rxData, keyFunction = Some((p: SimConnects) => p.id)) {

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

class ConnectionElementSelection(container: Selection[dom.EventTarget], rxData: Rx[js.Array[SimConnects]])
  extends RxDataSelection[SimConnects](container, "div", rxData, keyFunction = Some((p: SimConnects) => p.id)) {

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
