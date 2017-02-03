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

class SimConnects(val connects: Connects, val source: SimPost)
  extends SimulationLink[SimPost, ExtendedD3Node] with ExtendedD3Node with SimulationLinkImpl[SimPost, ExtendedD3Node] {
  //TODO: delegert!
  def id = connects.id
  def sourceId = connects.sourceId
  def targetId = connects.targetId

  // this is necessary because target can be a SimConnects itself
  var target: ExtendedD3Node = _

  // propagate d3 gets/sets to incident posts
  def x = for (sx <- source.x; tx <- target.x) yield (sx + tx) / 2
  def x_=(newX: js.UndefOr[Double]) {
    val diff = for (x <- x; newX <- newX) yield (newX - x) / 2
    source.x = for (x <- source.x; diff <- diff) yield x + diff
    target.x = for (x <- target.x; diff <- diff) yield x + diff
  }
  def y = for (sy <- source.y; ty <- target.y) yield (sy + ty) / 2
  def y_=(newY: js.UndefOr[Double]) {
    val diff = for (y <- y; newY <- newY) yield (newY - y) / 2
    source.y = for (y <- source.y; diff <- diff) yield y + diff
    target.y = for (y <- target.y; diff <- diff) yield y + diff
  }
  def vx = for (svx <- source.vx; tvx <- target.vx) yield (svx + tvx) / 2
  def vx_=(newVX: js.UndefOr[Double]) {
    val diff = for (vx <- vx; newVX <- newVX) yield (newVX - vx) / 2
    source.vx = for (vx <- source.vx; diff <- diff) yield vx + diff
    target.vx = for (vx <- target.vx; diff <- diff) yield vx + diff
  }
  def vy = for (svy <- source.vy; tvy <- target.vy) yield (svy + tvy) / 2
  def vy_=(newVY: js.UndefOr[Double]) {
    val diff = for (vy <- vy; newVY <- newVY) yield (newVY - vy) / 2
    source.vy = for (vy <- source.vy; diff <- diff) yield vy + diff
    target.vy = for (vy <- target.vy; diff <- diff) yield vy + diff
  }
  def fx: js.UndefOr[Double] = ???
  def fx_=(newFX: js.UndefOr[Double]): Unit = ???
  def fy: js.UndefOr[Double] = ???
  def fy_=(newFX: js.UndefOr[Double]): Unit = ???
}

class ConnectionLineSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.D3Environment)
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

class ConnectionElementSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.D3Environment)
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
