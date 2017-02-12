package frontend.graphview

import graph._
import math._
import collection.breakOut
import mhtml._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import algorithm.topologicalSort
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

object ContainmentHullSelection {
  def apply(container: Selection[dom.EventTarget], rxPosts: RxPosts, rxGraph: Rx[Graph]) = {
    import rxPosts.postIdToSimPost
    val rxData = rxGraph.map { graph =>

      val containments = graph.containments.values
      val parents: Seq[Post] = containments.map(c => graph.posts(c.parentId)).toSeq.distinct

      // due to transitive containment visualisation,
      // inner posts should be drawn above outer ones.
      // TODO: breaks on circular containment
      val ordered = topologicalSort(parents, (p: Post) => graph.children(p.id))

      ordered.map(p =>
        new ContainmentCluster(
          parent = postIdToSimPost.value(p.id),
          children = graph.transitiveChildren(p.id).map(p => postIdToSimPost.value(p.id))(breakOut)
        )).toJSArray
    }
    new ContainmentHullSelection(container, rxData)
  }
}

// TODO: merge with ContainmentCluster?
class ContainmentHullSelection(container: Selection[dom.EventTarget], rxData: Rx[js.Array[ContainmentCluster]])
  extends RxDataSelection[ContainmentCluster](container, "path", rxData, keyFunction = Some((p: ContainmentCluster) => p.id)) {

  override def enter(hull: Selection[ContainmentCluster]) {
    hull
      .style("fill", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke", (cluster: ContainmentCluster) => cluster.parent.color)
      .style("stroke-width", "70px") // TODO: dependent on nesting depth
      .style("stroke-linejoin", "round")
      .style("opacity", "0.8")
  }

  override def drawCall(hull: Selection[ContainmentCluster]) {
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
