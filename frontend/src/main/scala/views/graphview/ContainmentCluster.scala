package wust.frontend.views.graphview

import java.lang.Math._

import org.scalajs.d3v4._
import vectory._
import wust.frontend.Color._

import scala.collection.breakOut
import scala.scalajs.js

// class ContainmentCluster(val parent: SimPost, val children: IndexedSeq[SimPost], val depth: Int) {
//   val id = parent.id
//   val posts: IndexedSeq[SimPost] = children :+ parent

//   val postCount = posts.size
//   private val sn = 16 //TODO: on low numbers this leads to NaNs
//   private val step = PI * 2.0 / sn
//   private val positionSamples = new js.Array[js.Tuple2[Double, Double]](sn * posts.size)
//   private val padding = 0 // 15
//   private def regenerateCircleSamples() {
//     var i = 0
//     val n = postCount * sn
//     while (i < n) {
//       val a = (i % sn) * step
//       val post = posts(i / sn)
//       val radius = post.radius + padding
//       positionSamples(i) = js.Tuple2(
//         cos(a) * radius + post.x.getOrElse(0.0),
//         sin(a) * radius + post.y.getOrElse(0.0)
//       )
//       i += 1
//     }
//   }

//   private var _convexHull: js.Array[js.Tuple2[Double, Double]] = null
//   def convexHull = _convexHull
//   def recalculateConvexHull() {
//     regenerateCircleSamples()
//     val hull = d3.polygonHull(positionSamples)
//     _convexHull = if (hull == null)
//       positionSamples
//     else
//       hull.asInstanceOf[js.Array[js.Tuple2[Double, Double]]]
//   }

//   recalculateConvexHull()
// }

// object ContainmentClusterSelection extends DataSelection[ContainmentCluster] {
//   override val tag = "path"
//   override def enterAppend(hull: Selection[ContainmentCluster]) {
//     hull
//       .style("fill", (cluster: ContainmentCluster) => baseColor(cluster.parent.id))
//       .style("stroke", (cluster: ContainmentCluster) => baseColor(cluster.parent.id))
//       .style("stroke-linejoin", "round")
//       .style("stroke-linecap", "round")
//     // .style("mix-blend-mode", "overlay")
//   }

//   // https://codeplea.com/introduction-to-splines
//   // https://github.com/d3/d3-shape#curves
//   // val curve = d3.curveCardinalClosed
//   val curve = d3.curveCatmullRomClosed.alpha(0.5)
//   // val curve = d3.curveLinearClosed
//   // val curve = d3.curveNatural

//   override def draw(hull: Selection[ContainmentCluster]) {
//     hull
//       .attr("d", { (cluster: ContainmentCluster) => d3.line().curve(curve)(cluster.convexHull) })
//       .style("stroke-width", (cluster: ContainmentCluster) => s"${cluster.depth * 15}px") // *2 because the stroke is half inward, half outward
//       .style("opacity", (cluster: ContainmentCluster) => cluster.parent.opacity * 0.8)
//   }
// }

// object CollapsedContainmentClusterSelection extends DataSelection[ContainmentCluster] {
//   import ContainmentClusterSelection.curve

//   override val tag = "path"
//   override def enterAppend(hull: Selection[ContainmentCluster]) {
//     hull
//       .style("fill", (cluster: ContainmentCluster) => baseColor(cluster.parent.id))
//       .style("stroke", (cluster: ContainmentCluster) => baseColor(cluster.parent.id))
//       .style("stroke-linejoin", "round")
//       .style("stroke-linecap", "round")
//     // .style("stroke-dasharray", "10 5")
//   }

//   override def draw(hull: Selection[ContainmentCluster]) {
//     hull
//       .attr("d", { (cluster: ContainmentCluster) => d3.line().curve(curve)(cluster.convexHull) })
//       .style("stroke-width", (cluster: ContainmentCluster) => s"${cluster.depth * 15}px")
//       .style("opacity", (cluster: ContainmentCluster) => cluster.parent.opacity * 0.4)
//   }
// }

