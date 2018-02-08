package views.graphview

import d3v4.{Quadtree, d3}

import scala.scalajs.js

//TODO: use typedarrays? with DataViews to store interleaved data? shared typed arrays for webworkers?
class SimulationData(
                      val n: Int,
                      var alpha: Double = 1.0,
                      var alphaMin: Double = 0.001,
                      var alphaTarget: Double = 0,
                      var alphaDecay: Double = 1 - Math.pow(0.001, 1.0 / 300.0), // ~0.0228
                      var velocityDecay: Double = 0.4,
                      val x: Array[Double],
                      val y: Array[Double],
                      val vx: Array[Double],
                      val vy: Array[Double],
                      var quadtree: Quadtree[Int],
                      var clusterPolygons: Array[js.Array[js.Tuple2[Double, Double]]]
                    ) {
  def this(n: Int) = this(
    n = n,
    x = Array.fill(n)(Double.NaN),
    y = Array.fill(n)(Double.NaN),
    vx = Array.fill(n)(0),
    vy = Array.fill(n)(0),
    quadtree = d3.quadtree(),
    clusterPolygons = Array.empty
  )

  override def clone(): SimulationData = {
    new SimulationData(
      n = n,
      alpha = alpha,
      alphaMin = alphaMin,
      alphaTarget = alphaTarget,
      alphaDecay = alphaDecay,
      velocityDecay = velocityDecay,
      x = x.clone(),
      y = y.clone,
      vx = vx.clone(),
      vy = vy.clone(),
      quadtree = quadtree.copy,
      clusterPolygons = clusterPolygons.map(_.map(t => js.Tuple2(t._1, t._2)))
    )
  }
}

// used to backup coordinates in DOM
class Coordinates(
                   var x: Double = Double.NaN,
                   var y: Double = Double.NaN,
                   var vx: Double = 0,
                   var vy: Double = 0
                 ) extends js.Object
