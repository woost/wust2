package views.graphview

import d3v4.{ Quadtree, d3 }
import vectory._

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

  var eulerSetConvexHull: Array[Array[Circle]],
  var eulerSetConvexHullTangents: Array[Vec2Array],
  var eulerSetCollisionPolygon: Array[Vec2Array],
  var eulerSetCollisionPolygonAABB: Array[AARect],
  var eulerSetGeometricCenter: Vec2Array,

  var eulerZoneCollisionPolygon: Array[Vec2Array],
  var eulerZoneCollisionPolygonAABB: Array[AARect],
  var eulerZoneGeometricCenter: Vec2Array,

  var eulerSetConnectedComponentCollisionPolygon: Array[Vec2Array],
  var eulerSetConnectedComponentCollisionPolygonAABB: Array[AARect],
) {
  def this(n: Int) = this(
    n = n,
    x = Array.fill(n)(Double.NaN),
    y = Array.fill(n)(Double.NaN),
    vx = Array.fill(n)(0),
    vy = Array.fill(n)(0),
    quadtree = d3.quadtree(),

    eulerSetConvexHull = Array.empty,
    eulerSetConvexHullTangents = Array.empty,
    eulerSetCollisionPolygon = Array.empty,
    eulerSetCollisionPolygonAABB = Array.empty,
    eulerSetGeometricCenter = Vec2Array.create(0),

    eulerZoneCollisionPolygon = Array.empty,
    eulerZoneCollisionPolygonAABB = Array.empty,
    eulerZoneGeometricCenter = Vec2Array.create(0),

    eulerSetConnectedComponentCollisionPolygon = Array.empty,
    eulerSetConnectedComponentCollisionPolygonAABB = Array.empty,
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

      eulerSetConvexHull = eulerSetConvexHull.map(_.clone()),
      eulerSetConvexHullTangents = eulerSetConvexHullTangents.map(arr => Vec2Array(arr: _*)),
      eulerSetCollisionPolygon = eulerSetCollisionPolygon.map(arr => Vec2Array(arr: _*)),
      eulerSetCollisionPolygonAABB = eulerSetCollisionPolygonAABB.clone(),
      eulerSetGeometricCenter = Vec2Array(eulerSetGeometricCenter: _*),

      eulerZoneCollisionPolygon = eulerZoneCollisionPolygon.map(arr => Vec2Array(arr: _*)),
      eulerZoneCollisionPolygonAABB = eulerZoneCollisionPolygonAABB.clone(),
      eulerZoneGeometricCenter = Vec2Array(eulerZoneGeometricCenter: _*),

      eulerSetConnectedComponentCollisionPolygon = eulerSetConnectedComponentCollisionPolygon.map(arr => Vec2Array(arr: _*)),
      eulerSetConnectedComponentCollisionPolygonAABB = eulerSetConnectedComponentCollisionPolygonAABB.clone(),
    )
  }

  def isNaN = {
    x.exists(_.isNaN) ||
      y.exists(_.isNaN) ||
      vx.exists(_.isNaN) ||
      vy.exists(_.isNaN)
  }
}

// used to backup coordinates in DOM
class Coordinates(
  var x: Double = Double.NaN,
  var y: Double = Double.NaN,
  var vx: Double = 0,
  var vy: Double = 0
) extends js.Object
