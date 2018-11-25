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
    var eulerSetPolygon: Array[js.Array[js.Tuple3[Double, Double, Int]]],
    var eulerSetGeometricCenterX: Array[Double],
    var eulerSetGeometricCenterY: Array[Double],
    var eulerSetPolygonMinX: Array[Double],
    var eulerSetPolygonMinY: Array[Double],
    var eulerSetPolygonMaxX: Array[Double],
    var eulerSetPolygonMaxY: Array[Double]
) {
  def this(n: Int) = this(
    n = n,
    x = Array.fill(n)(Double.NaN),
    y = Array.fill(n)(Double.NaN),
    vx = Array.fill(n)(0),
    vy = Array.fill(n)(0),
    quadtree = d3.quadtree(),
    eulerSetPolygon = Array.empty,
    eulerSetGeometricCenterX = Array.empty,
    eulerSetGeometricCenterY = Array.empty,
    eulerSetPolygonMinX = Array.empty,
    eulerSetPolygonMinY = Array.empty,
    eulerSetPolygonMaxX = Array.empty,
    eulerSetPolygonMaxY = Array.empty
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
      eulerSetPolygon = eulerSetPolygon.map(_.map(t => js.Tuple3(t._1, t._2, t._3))),
      eulerSetGeometricCenterX = eulerSetGeometricCenterX.clone(),
      eulerSetGeometricCenterY = eulerSetGeometricCenterY.clone(),
      eulerSetPolygonMinX = eulerSetPolygonMinX.clone(),
      eulerSetPolygonMinY = eulerSetPolygonMinY.clone(),
      eulerSetPolygonMaxX = eulerSetPolygonMaxX.clone(),
      eulerSetPolygonMaxY = eulerSetPolygonMaxY.clone()
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
