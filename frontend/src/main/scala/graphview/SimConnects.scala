package frontend.graphview

import graph.Connects
import org.scalajs.d3v4._
import scalajs.js

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
