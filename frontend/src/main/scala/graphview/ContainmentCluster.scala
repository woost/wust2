package frontend.graphview

import graph._
import math._
import collection.breakOut
import mhtml._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import vectory._

import org.scalajs.d3v4._

class ContainmentCluster(val parent: SimPost, val children: IndexedSeq[SimPost]) {
  val id = parent.id

  def positions: js.Array[js.Array[Double]] = (children :+ parent).map(post => js.Array(post.x.asInstanceOf[Double], post.y.asInstanceOf[Double]))(breakOut)
  def convexHull: js.Array[js.Array[Double]] = {
    val hull = d3.polygonHull(positions)
    //TODO: how to correctly handle scalajs union type?
    if (hull == null) positions
    else hull.asInstanceOf[js.Array[js.Array[Double]]]
  }
}

// TODO: merge with ContainmentCluster?
object ContainmentHullSelection extends DataComponent[ContainmentCluster] {
  override val tag = "path"
  override val key: ContainmentCluster => Any = _.id
  override def enter(hull: Selection[ContainmentCluster]) {
    hull
      .style("fill", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke-width", "70px") // TODO: dependent on nesting depth
      .style("stroke-linejoin", "round")
      .style("opacity", "0.8")
  }

  override def draw(hull: Selection[ContainmentCluster]) {
    hull
      .attr("d", { (cluster: ContainmentCluster) =>
        // https://codeplea.com/introduction-to-splines
        // https://github.com/d3/d3-shape#curves
        val points = cluster.convexHull
        // val curve = d3.curveCardinalClosed
        val curve = d3.curveCatmullRomClosed.alpha(0.5)
        // val curve = d3.curveNatural

        d3.line().curve(curve)(points)
      })
  }
}
