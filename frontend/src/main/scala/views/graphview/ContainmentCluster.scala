package wust.frontend.views.graphview

import math._
import collection.breakOut
import rx._
import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import vectory._
import org.scalajs.d3v4._

import wust.util.Pipe
import wust.graph._

class ContainmentCluster(val parent: SimPost, val children: IndexedSeq[SimPost], val depth: Int) {
  val id = parent.id
  val posts = (children :+ parent)
  def maxRadius = posts.maxBy(_.radius).radius * 2

  def positions: js.Array[js.Array[Double]] = posts.map(post => js.Array(post.x.asInstanceOf[Double], post.y.asInstanceOf[Double]))(breakOut)
  def convexHull: js.Array[js.Array[Double]] = {
    val hull = d3.polygonHull(positions)
    //TODO: how to correctly handle scalajs union type?
    if (hull == null) positions
    else hull.asInstanceOf[js.Array[js.Array[Double]]]
  }
}

object ContainmentHullSelection extends DataSelection[ContainmentCluster] {
  override val tag = "path"
  override def enterAppend(hull: Selection[ContainmentCluster]) {
    hull
      .style("fill", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke-linejoin", "round")
      .style("stroke-linecap", "round")
      .style("opacity", "0.8")
      // .style("mix-blend-mode", "overlay")
  }

  // https://codeplea.com/introduction-to-splines
  // https://github.com/d3/d3-shape#curves
  // val curve = d3.curveCardinalClosed
  val curve = d3.curveCatmullRomClosed.alpha(0.5)
  // val curve = d3.curveNatural

  override def draw(hull: Selection[ContainmentCluster]) {
    hull
      .attr("d", { (cluster: ContainmentCluster) => d3.line().curve(curve)(cluster.convexHull) })
      .style("stroke-width", (cluster: ContainmentCluster) => s"${cluster.maxRadius + cluster.depth * 15}px") //TODO: maxRadius is calculated every frame, make it reactive
  }
}

object CollapsedContainmentHullSelection extends DataSelection[ContainmentCluster] {
  override val tag = "path"
  override def enterAppend(hull: Selection[ContainmentCluster]) {
    hull
      .style("fill", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke-linejoin", "round")
      .style("stroke-linecap", "round")
      .style("opacity", "0.4")
      // .style("stroke-dasharray", "10 5")
  }

  // https://codeplea.com/introduction-to-splines
  // https://github.com/d3/d3-shape#curves
  // val curve = d3.curveCardinalClosed
  val curve = d3.curveCatmullRomClosed.alpha(0.5)
  // val curve = d3.curveNatural

  override def draw(hull: Selection[ContainmentCluster]) {
    hull
      .attr("d", { (cluster: ContainmentCluster) => d3.line().curve(curve)(cluster.convexHull) })
      .style("stroke-width", (cluster: ContainmentCluster) => s"${cluster.maxRadius + cluster.depth * 15}px") //TODO: maxRadius is calculated every frame, make it reactive
  }
}
