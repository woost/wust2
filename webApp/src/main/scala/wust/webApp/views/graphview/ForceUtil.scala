package wust.webApp.views.graphview

import d3v4._
import vectory._

import scala.scalajs.js

// abstract class CustomForce[N <: SimulationNode] extends js.Object {
//   def initialize(nodes: js.Array[N]): Unit = {}
//   def force(alpha: Double): Unit
// }
// object CustomForce {
//   implicit def asD3Force[N <: SimulationNode](customForce: CustomForce[N]): Force[N] = {
//     val f: js.Function1[Double, Unit] = customForce.force _
//     f.asInstanceOf[js.Dynamic].initialize = customForce.initialize _
//     f.asInstanceOf[Force[N]]
//   }
// }

object ForceUtil {
  @inline private def forAllNodes[T](n: d3.QuadtreeNode[T])(code: T => Unit): Unit = {
    def isLeaf = !n.length.isDefined
    if (isLeaf) {
      var maybeNode: js.UndefOr[d3.QuadtreeNode[T]] = n
      while (maybeNode.isDefined) {
        val node = maybeNode.get
        code(node.data)
        maybeNode = node.next
      }
    }
  }

  @inline def forAllPointsInCircle(quadtree: d3.Quadtree[Int], x: Double, y: Double, r: Double)(
      code: Int => Unit
  ): Unit = {
    quadtree.visit { (n: d3.QuadtreeNode[Int], x0: Double, y0: Double, x1: Double, y1: Double) =>
      forAllNodes(n)(code)

      val rw = x1 - x0
      val rh = y1 - y0
      val rwh = rw * 0.5
      val rhh = rh * 0.5
      val centerX = x0 + rwh
      val centerY = y0 + rhh
      !Algorithms.intersectCircleAARect(x, y, r, centerX, centerY, rw, rh)
    }
  }

  @inline def forAllPointsInRect(quadtree: d3.Quadtree[Int], x0: Double, y0: Double, x3: Double, y3: Double)(
      code: Int => Unit
  ): Unit = {
    quadtree.visit { (n: d3.QuadtreeNode[Int], x1: Double, y1: Double, x2: Double, y2: Double) =>
      forAllNodes(n)(code)

      x1 >= x3 || y1 >= y3 || x2 < x0 || y2 < y0
    }
  }
  @inline def forAllPointsInRect(quadtree: d3.Quadtree[Int], rect:AARect)(code: Int => Unit): Unit = {
    forAllPointsInRect(
      quadtree,
      rect.minCorner.x,
      rect.minCorner.y,
      rect.maxCorner.x,
      rect.maxCorner.y
    )(code)
  }

  @inline def jitter = scala.util.Random.nextDouble

  @inline def min2By(list: js.Array[Int], f: Int => Double): (Int, Int) = {
    val n = list.size
    var i = 0
    var min1 = Int.MaxValue
    var min2 = Int.MaxValue
    var min1Value = Double.MaxValue
    var min2Value = Double.MaxValue
    while (i < n) {
      val x = list(i)
      val value = f(x)
      if (value < min1Value) {
        min2 = min1; min2Value = min1Value
        min1 = x; min1Value = value
      } else if (value < min2Value) {
        min2 = x; min2Value = value
      }
      i += 1
    }
    (min1, min2)
  }
}
