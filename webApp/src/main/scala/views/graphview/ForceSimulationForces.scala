package views.graphview

import d3v4.{d3, d3polygon, _}
import vectory.Algorithms.LineIntersection
import vectory._
import views.graphview.ForceSimulationConstants._

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object ForceSimulationForces {
  import Math._

  import wust.webApp.views.graphview.ForceUtil._

  @inline private def sq(x: Double): Double = x * x

  def nanToPhyllotaxis(data: SimulationData, spacing: Double): Unit = {
    import data.{vx, vy, x, y}
    val theta = Math.PI * (3 - Math.sqrt(5))

    var i = 0
    val n = data.n
    while (i < n) {
      if (x(i).isNaN || y(i).isNaN) {
        val radius = spacing * Math.sqrt(i)
        val angle = i * theta
        x(i) = radius * Math.cos(angle)
        y(i) = radius * Math.sin(angle)
        vx(i) = 0
        vy(i) = 0
      }

      if (vx(i).isNaN || vy(i).isNaN) {
        vx(i) = 0
        vy(i) = 0
      }

      i += 1
    }
  }

  def initQuadtree(data: SimulationData, staticData: StaticData): Unit = {
    import data._
    import staticData._

    // https://devdocs.io/d3~4/d3-quadtree#quadtree
    quadtree = d3.quadtree[Int](indices.toJSArray, x.apply _, y.apply _)
  }

  def eulerSetGeometricCenter(simData: SimulationData, staticData: StaticData): Unit = {
    val eulerSetCount = staticData.eulerSetCount
    if (simData.eulerSetGeometricCenterX.length != eulerSetCount) {
      simData.eulerSetGeometricCenterX = new Array[Double](eulerSetCount)
      simData.eulerSetGeometricCenterY = new Array[Double](eulerSetCount)
    }

    var cx = 0.0
    var cy = 0.0
    var j = 0

    var i = 0
    while (i < eulerSetCount) {
      val allNodes = staticData.eulerSetAllNodes(i)
      val nodeCount = allNodes.length
      j = 1
      cx = simData.x(allNodes(0))
      cy = simData.y(allNodes(0))
      while (j < nodeCount) {
        //TODO: take radius into account: (        *        ) ( * )
        //      and padding                                  ^
        //                                                   |
        //                                               Center should be here
        cx += simData.x(allNodes(j))
        cy += simData.y(allNodes(j))
        j += 1
      }

      simData.eulerSetGeometricCenterX(i) = cx / nodeCount
      simData.eulerSetGeometricCenterY(i) = cy / nodeCount
      i += 1
    }
  }

  def calculateEulerSetPolygons(simData: SimulationData, staticData: StaticData): Unit = {
    val eulerSetCount = staticData.eulerSetCount
    if (simData.eulerSetPolygons.length != eulerSetCount) {
      simData.eulerSetPolygons = new Array[js.Array[js.Tuple3[Double, Double, Int]]](eulerSetCount)
      simData.eulerSetPolygonMinX = new Array[Double](eulerSetCount)
      simData.eulerSetPolygonMinY = new Array[Double](eulerSetCount)
      simData.eulerSetPolygonMaxX = new Array[Double](eulerSetCount)
      simData.eulerSetPolygonMaxY = new Array[Double](eulerSetCount)
    }
    var i = 0
    while (i < eulerSetCount) {
      val eulerSetNodes = staticData.eulerSetAllNodes(i)

      // this stores the index hidden in the points
      val centerPoints: d3polygon.Polygon = eulerSetNodes.map(
        j => js.Tuple3(simData.x(j), simData.y(j), j).asInstanceOf[js.Tuple2[Double, Double]]
      )(breakOut)
      val centerConvexHull = {
        val hull = d3.polygonHull(centerPoints)
        if (hull == null) centerPoints
        else hull
      }.asInstanceOf[js.Array[js.Tuple3[Double, Double, Int]]] // contains hull points with index in ccw order

      // calculate tangents
      // TODO: Different circle sizes are a problem, therefore the trivial convex hull of the circle centers is not enough, we need
      // the convex hull of the circles:
      // https://www.sciencedirect.com/science/article/pii/092577219290015K (A convex hull algorithm for discs, and applications)

      val tangents: Array[(Double, Double, Int)] = (centerConvexHull :+ centerConvexHull(0))
        .sliding(2)
        .flatMap { points =>
          val point1 = points(0)
          val point2 = points(1)
          // outer tangent with corrected atan sign: https://en.wikipedia.org/wiki/Tangent_lines_to_circles#Outer_tangent
          val x1 = simData.x(point1._3)
          val y1 = simData.y(point1._3)
          val r1 = staticData.radius(point1._3)
          val x2 = simData.x(point2._3)
          val y2 = simData.y(point2._3)
          val r2 = staticData.radius(point2._3)

          val gamma = -atan2(y2 - y1, x2 - x1) // atan2 sets the correct sign, so that all tangents are on the right side of point order
          val beta = asin((r2 - r1) / sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)))
          val alpha = gamma - beta

          val x3 = x1 + r1 * cos(PI / 2 - alpha)
          val y3 = y1 + r1 * sin(PI / 2 - alpha)
          val x4 = x2 + r2 * cos(PI / 2 - alpha)
          val y4 = y2 + r2 * sin(PI / 2 - alpha)

          Array((x3, y3, point1._3), (x4, y4, point2._3))
        }
        .toArray

      val polygon = (tangents ++ tangents.take(2))
        .sliding(4, 2)
        .flatMap { points =>
          val i = points(1)._3
          val point0 = Vec2(points(0)._1, points(0)._2)
          val point1 = Vec2(points(1)._1, points(1)._2)
          val point2 = Vec2(points(2)._1, points(2)._2)
          val point3 = Vec2(points(3)._1, points(3)._2)

          val preLine = Line(point0, point1)
          val postLine = Line(point2, point3)

          val tangentDirection = point1 - point2
          val a = tangentDirection.normal.normalized * staticData.radius(i)
          val center = Vec2(simData.x(i), simData.y(i))
          val tangentPoint = center + a
          val tangent = Line(tangentPoint, tangentPoint + tangentDirection)

          val edge = (for {
            LineIntersection(corner1, _, _) <- preLine.intersect(tangent)
            LineIntersection(corner2, _, _) <- tangent.intersect(postLine)
          } yield Line(corner1, corner2)).get

          Array(js.Tuple3(edge.x1, edge.y1, i), js.Tuple3(edge.x2, edge.y2, i))
        }
        .toJSArray

      simData.eulerSetPolygons(i) = polygon

      // calculate axis aligned bounding box of polygon for quadtree lookup
      // TODO: move to own function?
      // TODO: include node spacing?
      var j = 0
      var minX = polygon(0)._1
      var minY = polygon(0)._2
      var maxX = minX
      var maxY = minY
      j = 1
      while (j < polygon.length) {
        val x = polygon(j)._1
        val y = polygon(j)._2
        if (x < minX) minX = x
        else if (x > maxX) maxX = x
        if (y < minY) minY = y
        else if (y > maxY) maxY = y
        j += 1
      }
      simData.eulerSetPolygonMinX(i) = minX
      simData.eulerSetPolygonMinY(i) = minY
      simData.eulerSetPolygonMaxX(i) = maxX
      simData.eulerSetPolygonMaxY(i) = maxY

      i += 1
    }
  }

  def clearVelocities(simData: SimulationData): Unit = {
    var i = 0
    val nodeCount = simData.n
    while (i < nodeCount) {
      simData.vx(i) = 0
      simData.vy(i) = 0
      i += 1
    }
  }

  def applyVelocities(simData: SimulationData): Unit = {
    import simData._
    var i = 0
    val n = simData.n
    while (i < n) {
      vx(i) *= velocityDecay
      vy(i) *= velocityDecay
      x(i) += vx(i)
      y(i) += vy(i)

      i += 1
    }
  }

  def rectBound(
      simData: SimulationData,
      staticData: StaticData,
      planeDimension: PlaneDimension,
      strength: Double
  ): Unit = {
    import planeDimension.{simHeight => planeHeight, simWidth => planeWidth, _}
    import simData._
    import staticData._

    @inline def pushIntoBounds(i: Int, xRadius: Double, yRadius: Double, strength: Double): Unit = {
      val xPos = x(i) - xOffset
      val yPos = y(i) - yOffset
      if (xPos < xRadius) {
        vx(i) += (xRadius - xPos) * strength
      }
      if (yPos < yRadius) {
        vy(i) += (yRadius - yPos) * strength
      }
      if (xPos > planeWidth - xRadius) {
        vx(i) += ((planeWidth - xRadius) - xPos) * strength
      }
      if (yPos > planeHeight - yRadius) {
        vy(i) += ((planeHeight - yRadius) - yPos) * strength
      }
    }

    var i = 0
    val n = simData.n
    while (i < n) {
      pushIntoBounds(i, collisionRadius(i), collisionRadius(i), strength = alpha * strength)
      // pushIntoBounds(i2, containmentRadius(i), containmentRadius(i), strength = alpha * 0.1, maxStrength = collisionRadius(i))
      i += 1
    }
  }

  def keepDistance(
      simData: SimulationData,
      staticData: StaticData,
      distance: Double,
      strength: Double
  ): Unit = {
    import simData._
    import staticData._

    var ai = 0
    val n = simData.n
    while (ai < n) {
      var ax = x(ai)
      var ay = y(ai)
      forAllPointsInCircle(quadtree, ax, ay, radius(ai) + distance + maxRadius) { bi =>
        if (bi != ai) {
          val bx = x(bi)
          val by = y(bi)

          if (ax == bx && ay == by) {
            ax += distance * 0.5 + jitter
            ay -= distance * 0.5 + jitter
          }

          // val centerDist = (b - a).length
          val centerDist = Vec2.length(bx - ax, by - ay)
          val visibleDist = centerDist - radius(ai) - radius(bi)
          if (visibleDist < distance) {
            val dirx = (bx - ax) / centerDist
            val diry = (by - ay) / centerDist
            val factor = (distance - visibleDist) * 0.5 * alpha * strength // the other half goes to the other node
            vx(bi) += dirx * factor
            vy(bi) += diry * factor
          }
        }
      }

      ai += 1
    }
  }

  def eulerSetClustering(
      simData: SimulationData,
      staticData: StaticData,
      strength: Double
  ): Unit = {
    // If a node is too far away from the geometric center of its euler set, push it towards it
    import simData._
    import staticData._

    var ci = 0
    val cn = eulerSetCount
    while (ci < cn) {
      val allNodes = eulerSetAllNodes(ci)
      val geometricCenterX = eulerSetGeometricCenterX(ci)
      val geometricCenterY = eulerSetGeometricCenterY(ci)
      val eulerSetRadius = staticData.eulerSetRadius(ci)
      val eulerSetRadiusSq = sq(eulerSetRadius)

      val n = allNodes.length
      var i = 0
      while (i < n) {
        val node = allNodes(i)

        val dx = geometricCenterX - x(node)
        val dy = geometricCenterY - y(node)
        val distance = Vec2.length(dx, dy) // TODO: avoid sqrt
        val distanceDiff = distance + radius(node) - eulerSetRadius
        // be aware: >= produces NaNs
        if (distanceDiff > 0) { // node is too far outside
          //TODO: avoid Vec2 allocation and sqrt
          val velocity = Vec2(dx, dy).normalized * strength * distanceDiff * alpha

          vx(node) += velocity.x
          vy(node) += velocity.y
        }
        i += 1
      }
      ci += 1
    }
  }

  def edgeLength(simData: SimulationData, staticData: StaticData): Unit = {
    import simData._
    import staticData._

    var i = 0
    val cn = edgeCount
    while (i < cn) {
      val source = staticData.source(i)
      val target = staticData.target(i)
      val targetDistance = radius(source) + nodeSpacing + radius(target)
      val targetDistanceSq = targetDistance * targetDistance // TODO: cache in array?
      val dx = x(source) - x(target)
      val dy = y(source) - y(target)
      val distanceSq = Vec2.lengthSq(dx, dy)
      if (distanceSq > targetDistanceSq) {
        //TODO: avoid Vec2 allocation and sqrt
        val distanceDiff = Vec2.length(dx, dy) - targetDistance
        val velocity = distanceDiff * 0.5
        val targetDir = Vec2(dx, dy).normalized * velocity * alpha

        vx(source) -= targetDir.x
        vy(source) -= targetDir.y
        vx(target) += targetDir.x
        vy(target) += targetDir.y
      }

      i += 1
    }
  }

  def pushOutOfWrongEulerSet(simData: SimulationData, staticData: StaticData): Unit = {
    import simData._
    import staticData._

    var ci = 0
    val cn = eulerSetCount
    while (ci < cn) {
      val hull = ConvexPolygon(eulerSetPolygons(ci).map(t => Vec2(t._1, t._2)))
      //TODO: variable for eulerSetAllNodes.length
      val forceWeight = 1.0 / (eulerSetAllNodes.length + 1) // per node

      forAllPointsInRect(
        quadtree,
        eulerSetPolygonMinX(ci),
        eulerSetPolygonMinY(ci),
        eulerSetPolygonMaxX(ci),
        eulerSetPolygonMaxY(ci)
      ) { ai =>
        val center = Vec2(x(ai), y(ai))
        val radius = staticData.radius(ai) + nodeSpacing

        val belongsToCluster = eulerSetAllNodes(ci).contains(ai) //TODO: fast lookup with precomputed table
        if (!belongsToCluster) {
          val visuallyInCluster = hull intersectsMtd Circle(center, radius)
          visuallyInCluster.foreach { pushVector =>
            val nodePushDir = pushVector * (alpha * forceWeight)

            // push node out
            vx(ai) += nodePushDir.x
            vy(ai) += nodePushDir.y

          //TODO: push eulerSet away

          // // push closest nodes of cluster (forming line segment) back
          // // TODO: the closest points are not necessarily forming the closest line segment.
          // val (ia, ib) = min2By(containmentClusterPostIndices(ci), i => Vec2.lengthSq(pos(i * 2) - center.x, pos(i * 2 + 1) - center.y))
          // vel(ia * 2) += -nodePushDir.x
          // vel(ia * 2 + 1) += -nodePushDir.y
          // vel(ib * 2) += -nodePushDir.x
          // vel(ib * 2 + 1) += -nodePushDir.y

          // containmentClusterPostIndices(ci).toSeq.sortBy(i => Vec2.lengthSq(pos(i * 2) - center.x, pos(i * 2 + 1) - center.y)).take(2).foreach{ i =>
          //   val i2 = i * 2
          //   vel(i2) += -nodePushDir.x
          //   vel(i2 + 1) += -nodePushDir.y
          // }
          }
        }
      }

      ci += 1
    }
  }
}
