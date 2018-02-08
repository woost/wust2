package views.graphview

import d3v4.{d3, d3polygon, _}
import vectory.Algorithms.LineIntersection
import vectory.{Line, Vec2}

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object ForceSimulationForces {
  import Math._
  @inline private def sq(x:Double): Double = x * x

  def nanToPhyllotaxis(data: SimulationData, spacing: Double): Unit = {
    import data.{vx, vy, x, y}
    val theta = Math.PI * (3 - Math.sqrt(5))

    var i = 0
    val n = data.n
    while (i < n) {
      if (x(i).isNaN || y(i).isNaN) {
        // println(s"$i: NaN")
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

  def clusterPolygons(simData:SimulationData, staticData: StaticData): Unit = {
    val containmentClusterCount = staticData.eulerSets.length
    simData.clusterPolygons = new Array[js.Array[js.Tuple2[Double,Double]]](containmentClusterCount)
    var i = 0
    while (i < containmentClusterCount) {
      val cluster = staticData.eulerSets(i)

      // this stores the index hidden in the points
      val centerPoints:d3polygon.Polygon = cluster.map(j => js.Tuple3(simData.x(j), simData.y(j), j).asInstanceOf[js.Tuple2[Double,Double]])(breakOut)
      val centerConvexHull = {
        val hull = d3.polygonHull(centerPoints)
        if(hull == null) centerPoints
        else hull
      }.asInstanceOf[js.Array[js.Tuple3[Double,Double,Int]]] // contains hull points with index in ccw order

      // calculate tangents
      // TODO: Different circle sizes are a problem, therefore the trivial convex hull of the circle centers is not enough, we need
      // the convex hull of the circles:
      // https://www.sciencedirect.com/science/article/pii/092577219290015K (A convex hull algorithm for discs, and applications)

      val tangents: Array[(Double, Double, Int)] = (centerConvexHull :+ centerConvexHull(0)).sliding(2).flatMap { points =>
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
      }.toArray

      val polygon = (tangents ++ tangents.take(2)).sliding(4, 2).flatMap { points =>
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

        Array(js.Tuple2(edge.x1, edge.y1), js.Tuple2(edge.x2, edge.y2))
      }.toJSArray

      simData.clusterPolygons(i) = polygon

      i += 1
    }
  }

  def clearVelocities(simData:SimulationData): Unit = {
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

  def rectBound(simData: SimulationData, staticData: StaticData, planeDimension: PlaneDimension): Unit = {
    import planeDimension.{simHeight => planeHeight, simWidth => planeWidth, _}
    import simData._
    import staticData._

    @inline def pushIntoBounds(i: Int, xRadius: Double, yRadius: Double, strength: Double, maxStrength: Double = Double.PositiveInfinity): Unit = {
      val xPos = x(i) - xOffset
      val yPos = y(i) - yOffset
      if (xPos < xRadius) {
        vx(i) += ((xRadius - xPos) min maxStrength) * strength
      }
      if (yPos < yRadius) {
        vy(i) += ((yRadius - yPos) min maxStrength) * strength
      }
      if (xPos > planeWidth - xRadius) {
        vx(i) += (((planeWidth - xRadius) - xPos) max -maxStrength) * strength
      }
      if (yPos > planeHeight - yRadius) {
        vy(i) += (((planeHeight - yRadius) - yPos) max -maxStrength) * strength
      }
    }

    var i = 0
    val n = simData.n
    while (i < n) {
      pushIntoBounds(i, collisionRadius(i), collisionRadius(i), strength = alpha)
      // pushIntoBounds(i2, containmentRadius(i), containmentRadius(i), strength = alpha * 0.1, maxStrength = collisionRadius(i))
      i += 1
    }
  }


  def keepDistance(simData: SimulationData, staticData: StaticData, distance: Double, strength: Double = 1.0): Unit = {
    import simData._
    import staticData._
    import wust.frontend.views.graphview.ForceUtil._

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
          val centerDist = Math.sqrt(sq(bx - ax) + sq(by - ay))
          val visibleDist = centerDist - radius(ai) - radius(bi)
          if (visibleDist < distance) {
            val dirx = (bx - ax) / centerDist
            val diry = (by - ay) / centerDist
            val factor = (distance - visibleDist) * 0.5 * alpha * strength // the other half goes to the other node
//            if (!containmentTest(ai,bi)) { // parents can push children, and children each other
              // TODO: also disallow pushing of transitive parents?
              vx(bi) += dirx * factor
              vy(bi) += diry * factor
//            }
          }
          // }
        }
      }

      ai += 1
    }
  }

//  def clustering(data:SimulationData, staticData:StaticData): Unit = {
//    val innerVelocityFactor = 0.1
//    import data._
//    var ci = 0
//    val cn = staticData.containmentClusters.length
//    var i = 0
//    var i2 = 0
//    while (ci < cn) {
//      val parentI = containmentClusterParentIndex(ci)
//      val parentI2 = parentI * 2
//      val children = containmentClusterChildrenIndices(ci)
//
//      val n = children.size
//      i = 0
//      while (i < n) {
//        val childI = children(i)
//        val targetDistance = containmentRadius(parentI) - containmentRadius(childI) // stop when completely inside the containmentRadius circle
//        val targetDistanceSq = targetDistance * targetDistance
//
//        val childWeight = n / (n+1.0)
//        val parentWeight = 1.0 / (n+1.0)
//
//        val i2 = childI * 2
//        val dx = pos(parentI2) - pos(i2)
//        val dy = pos(parentI2 + 1) - pos(i2 + 1)
//        val distanceSq = Vec2.lengthSq(dx, dy)
//        // be aware: >= produces NaNs
//        if (distanceSq > targetDistanceSq) { // node is outside
//          //TODO: avoid Vec2 allocation and sqrt
//          val distanceDiff = Vec2.length(dx, dy) - targetDistance
//          val velocity = distanceDiff
//          val dir = Vec2(dx, dy).normalized
//          val childDir = dir * (velocity * alpha * childWeight)
//          val parentDir = -dir * (velocity * alpha * parentWeight)
//
//          vel(i2) += childDir.x
//          vel(i2 + 1) += childDir.y
//          if(postParentCount(childI) >= 2) {
//            vel(parentI2) += parentDir.x
//            vel(parentI2 + 1) += parentDir.y
//          }
//        }
//        else { // node is inside
//          // val targetDistance = collisionRadius(parentI) + (containmentRadius(parentI) - collisionRadius(parentI)) / 2 // stop at center between collisionRadius(parentI) and containmentRadius(parentI)
//          val targetDistance = radius(parentI) + Constants.nodePadding + radius(childI)
//          val targetDistanceSq = targetDistance * targetDistance
//          if (distanceSq > targetDistanceSq) {
//            val distanceDiff =  Vec2.length(dx, dy) - targetDistance
//            val velocity = distanceDiff * innerVelocityFactor
//            val dir = Vec2(dx, dy).normalized
//            val childDir = dir * (velocity * alpha * childWeight)
//            val parentDir = dir * (velocity * alpha * parentWeight)
//
//            vel(i2) += childDir.x
//            vel(i2 + 1) += childDir.y
//            // vel(parentI2) += parentDir.x
//            // vel(parentI2 + 1) += parentDir.y
//          }
//        }
//        i += 1
//      }
//      ci += 1
//    }
//  }
}
