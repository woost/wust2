package views.graphview

import d3v4.{ d3, d3polygon, _ }
import vectory.Algorithms.LineIntersection
import vectory._
import views.graphview.ForceSimulationConstants._

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import wust.util.collection._
import flatland._

object ForceSimulationForces {
  import Math._

  import wust.webApp.views.graphview.ForceUtil._

  @inline private def sq(x: Double): Double = x * x

  def nanToPhyllotaxis(data: SimulationData, spacing: Double): Unit = {
    import data.{ vx, vy, x, y }
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

  //TODO: extract function for weighted center
  def eulerSetCenter(simData: SimulationData, staticData: StaticData): Unit = {
    val eulerSetCount = staticData.eulerSetCount
    if (simData.eulerSetGeometricCenter.length != eulerSetCount) {
      simData.eulerSetGeometricCenter = Vec2Array.create(eulerSetCount)
    }

    staticData.eulerSetAllNodes.foreachIndexAndElement{ (i, allNodes) =>
      //TODO: faster
      //TODO: sets of size 1 produce NaNs
      val sumOfAllRadii = allNodes.map(staticData.collisionRadius).sum
      val radiiRatio = allNodes.map(i => 1 - staticData.collisionRadius(i) / sumOfAllRadii)
      val radiiRatioNormalized = {
        val sum = radiiRatio.sum
        radiiRatio.map(_ / sum)
      }

      var cx = 0.0
      var cy = 0.0
      allNodes.foreachIndexAndElement{ (j, nodeIdx) =>
        cx += simData.x(nodeIdx) * radiiRatioNormalized(j)
        cy += simData.y(nodeIdx) * radiiRatioNormalized(j)
      }

      simData.eulerSetGeometricCenter(i) = Vec2(cx, cy)
    }
  }

  //TODO: extract function for weighted center
  def eulerZoneCenter(simData: SimulationData, staticData: StaticData): Unit = {
    val eulerZoneCount = staticData.eulerZoneCount
    if (simData.eulerZoneGeometricCenter.length != eulerZoneCount) {
      simData.eulerZoneGeometricCenter = Vec2Array.create(eulerZoneCount)
    }

    staticData.eulerZoneNodes.foreachIndexAndElement{ (i, allNodes) =>
      simData.eulerZoneGeometricCenter(i) = {
        assert(allNodes.nonEmpty)
        if(allNodes.size == 1) Vec2(simData.x(allNodes(0)), simData.y(allNodes(0)))
        else {
          //TODO: faster
          val sumOfAllRadii = allNodes.map(staticData.collisionRadius).sum
          val radiiRatio = allNodes.map(i => 1 - staticData.collisionRadius(i) / sumOfAllRadii)
          val radiiRatioNormalized = {
            val sum = radiiRatio.sum
            radiiRatio.map(_ / sum)
          }

          var cx = 0.0
          var cy = 0.0
          allNodes.foreachIndexAndElement{ (j, nodeIdx) =>
            cx += simData.x(nodeIdx) * radiiRatioNormalized(j)
            cy += simData.y(nodeIdx) * radiiRatioNormalized(j)
          }

          Vec2(cx, cy)
        }
      }
    }
  }

  def calculatePolygons(circles: Seq[Circle]): (Array[Circle], Vec2Array, Vec2Array) = {
    val convexHull = ConvexHullOfCircles(circles).toArray // counter clockwise order of circles

    val tangents: Vec2Array = if (convexHull.size <= 1) Vec2Array.create(0) else Vec2Array(((convexHull :+ convexHull.head)
      .sliding(2)
      .flatMap {
        case Array(c1, c2) =>
          val tangent = c1.outerTangentCCW(c2).get
          // assert(!tangent.start.x.isNaN && !tangent.start.y.isNaN, s"tangent.start $tangent ($c1, $c2)")
          // assert(!tangent.end.x.isNaN && !tangent.end.y.isNaN, "tangent.end")
          Array(tangent.start, tangent.end)
      }
      .toArray): _*)

    val collisionPolygon: Vec2Array = {
      //TODO: handle CCW order
      //TODO: handle CCW order
      //TODO: handle CCW order
      //TODO: handle CCW order
      //TODO: handle CCW order
      //TODO: handle CCW order
      if (convexHull.size == 0) throw new Exception("collision cluster without any convex hull")
      else if (convexHull.size == 1) convexHull.head.sampleCircumference(4)
      else Vec2Array(((tangents ++ tangents.take(2))
        .sliding(4, 2).zip(convexHull.iterator.drop(1) ++ Iterator(convexHull.head))
        .flatMap {
          case (tangentPoints, circle) =>
            // val point0 = tangentPoints(0)
            val point1 = tangentPoints(1)
            val point2 = tangentPoints(2)
            // val point3 = tangentPoints(3)

            val tangentDirection = point2 - point1
            val a = tangentDirection.normal.normalized * circle.r
            val tangentPoint = circle.center + a
            // assert(!point1.x.isNaN && !point1.y.isNaN, "point1")
            // assert(!point2.x.isNaN && !point2.y.isNaN, "point2")
            // assert(!tangentPoint.x.isNaN && !tangentPoint.y.isNaN, "tangentPoint")

            Array(point1, tangentPoint, point2)
        }
        .toJSArray): _*)
    }

    (convexHull, tangents, collisionPolygon)
  }

  def calculateEulerSetPolygons(simData: SimulationData, staticData: StaticData): Unit = {
    val eulerSetCount = staticData.eulerSetCount
    if (simData.eulerSetConvexHull.length != eulerSetCount) {
      simData.eulerSetConvexHull = new Array[Array[Circle]](eulerSetCount)
      simData.eulerSetConvexHullTangents = new Array[Vec2Array](eulerSetCount)
      simData.eulerSetCollisionPolygon = new Array[Vec2Array](eulerSetCount)
      simData.eulerSetCollisionPolygonAABB = new Array[AARect](eulerSetCount)
    }
    var i = 0
    while (i < eulerSetCount) {
      val eulerSetNodes = staticData.eulerSetAllNodes(i)

      val borderWidth = 10
      val circles = eulerSetNodes.map { j =>
        val border = staticData.eulerSetDepth(i) * borderWidth
        Circle(Vec2(simData.x(j), simData.y(j)), staticData.radius(j) + border)
      }

      val (convexHull, tangents, collisionPolygon) = calculatePolygons(circles)

      simData.eulerSetConvexHull(i) = convexHull
      simData.eulerSetConvexHullTangents(i) = tangents
      simData.eulerSetCollisionPolygon(i) = collisionPolygon
      simData.eulerSetCollisionPolygonAABB(i) = Algorithms.axisAlignedBoundingBox(collisionPolygon)

      i += 1
    }
  }

  def calculateEulerZonePolygons(simData: SimulationData, staticData: StaticData): Unit = {
    val eulerZoneCount = staticData.eulerZoneCount
    if (simData.eulerZoneConvexHull.length != eulerZoneCount) {
      simData.eulerZoneConvexHull = new Array[Array[Circle]](eulerZoneCount)
      simData.eulerZoneConvexHullTangents = new Array[Vec2Array](eulerZoneCount)
      simData.eulerZoneCollisionPolygon = new Array[Vec2Array](eulerZoneCount)
      simData.eulerZoneCollisionPolygonAABB = new Array[AARect](eulerZoneCount)
    }
    var i = 0
    while (i < eulerZoneCount) {
      val eulerZoneNodes = staticData.eulerZoneNodes(i)

      val circles = eulerZoneNodes.map { j =>
        Circle(Vec2(simData.x(j), simData.y(j)), staticData.radius(j))
      }

      val (convexHull, tangents, collisionPolygon) = calculatePolygons(circles)

      simData.eulerZoneConvexHull(i) = convexHull
      simData.eulerZoneConvexHullTangents(i) = tangents
      simData.eulerZoneCollisionPolygon(i) = collisionPolygon
      simData.eulerZoneCollisionPolygonAABB(i) = Algorithms.axisAlignedBoundingBox(collisionPolygon)

      i += 1
    }
  }

  def clearVelocities(simData: SimulationData): Unit = {
    val nodeCount = simData.n
    loop (nodeCount) { i =>
      simData.vx(i) = 0
      simData.vy(i) = 0
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
    import planeDimension.{ simHeight => planeHeight, simWidth => planeWidth, _ }
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

  def keepMinimumNodeDistance(
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
            val weight = radius(ai) / (radius(bi) + radius(ai)) // the smaller node is pushed stronger
            val factor = (distance - visibleDist) * weight * alpha * strength // the remaining part (1-weight) goes to the other node
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
    // If a node is far away from the center of its euler set, push it towards it
    import simData._
    import staticData._
    @inline def saddle(x: Double, tolerance: Double): Double = {
      // a linear function with flat origin
      // https://www.wolframalpha.com/input/?i=plot+piecewise+%5B%7B%7B(x%2F50)%5E3,+-1%3C%3Dx%3C%3D250*sqrt(2)%7D,%7Bx,+x+%3E+250*sqrt(2)%7D%7D%5D
      if (x.abs > Math.pow(tolerance, 1.5)) x
      else Math.pow(x / tolerance, 3)
    }

    eulerSetAllNodes.foreachIndexAndElement{ (ci, allNodes) =>
      val geometricCenterX = eulerSetGeometricCenter(ci).x
      val geometricCenterY = eulerSetGeometricCenter(ci).y

      //TODO: calculate tolerance in staticdata
      val tolerance = Math.sqrt(eulerSetArea(ci)) / 5 // a higher tolerance prevents clusters from moving themselves

      allNodes.foreachElement { nodeIdx =>
        val dx = geometricCenterX - x(nodeIdx)
        val dy = geometricCenterY - y(nodeIdx)
        val distance = Vec2.length(dx, dy) - collisionRadius(nodeIdx) // TODO: avoid sqrt
        //TODO: avoid Vec2 allocation and sqrt
        val velocity = Vec2(dx, dy).normalized * strength * saddle(distance, tolerance) * alpha
        vx(nodeIdx) += velocity.x
        vy(nodeIdx) += velocity.y
      }
    }
  }


  def eulerZoneAttraction(
    simData: SimulationData,
    staticData: StaticData,
    strength: Double
  ): Unit = {
    // If a node is far away from the center of its euler set, push it towards it
    import simData._
    import staticData._
    @inline def saddle(x: Double, tolerance: Double): Double = {
      // a linear function with flat origin
      // https://www.wolframalpha.com/input/?i=plot+piecewise+%5B%7B%7B(x%2F50)%5E3,+-1%3C%3Dx%3C%3D250*sqrt(2)%7D,%7Bx,+x+%3E+250*sqrt(2)%7D%7D%5D
      if (x.abs > Math.pow(tolerance, 1.5)) x
      else Math.pow(x / tolerance, 3)
    }

    eulerZoneNeighbourhoods.foreach { case (zoneAIdx, zoneBIdx) =>
      val geometricCenterA = eulerZoneGeometricCenter(zoneAIdx)
      val geometricCenterB = eulerZoneGeometricCenter(zoneBIdx)
      val direction = (geometricCenterA - geometricCenterB)
      val radiusA = Math.sqrt(eulerZoneArea(zoneAIdx)) / 5
      val radiusB = Math.sqrt(eulerZoneArea(zoneBIdx)) / 5
      val distance = direction.length - radiusA - radiusB
      if(distance > 0) {
        val tolerance = radiusA + radiusB // a higher tolerance prevents clusters from moving themselves
        val push = direction.normalized * strength * saddle(distance, tolerance) * alpha
        eulerZoneNodes(zoneAIdx).foreachElement { nodeIdx =>
          vx(nodeIdx) -= push.x
          vy(nodeIdx) -= push.y
        }
        eulerZoneNodes(zoneBIdx).foreachElement { nodeIdx =>
          vx(nodeIdx) += push.x
          vy(nodeIdx) += push.y
        }
      }
    }
  }

  def eulerZoneClustering(
    simData: SimulationData,
    staticData: StaticData,
    strength: Double
  ): Unit = {
    // If a node is far away from the center of its euler set, push it towards it
    import simData._
    import staticData._
    @inline def saddle(x: Double, tolerance: Double): Double = {
      // a linear function with flat origin
      // https://www.wolframalpha.com/input/?i=plot+piecewise+%5B%7B%7B(x%2F50)%5E3,+-1%3C%3Dx%3C%3D250*sqrt(2)%7D,%7Bx,+x+%3E+250*sqrt(2)%7D%7D%5D
      if (x.abs > Math.pow(tolerance, 1.5)) x
      else Math.pow(x / tolerance, 3)
    }

    eulerZoneNodes.foreachIndexAndElement{ (ci, allNodes) =>
      val geometricCenterX = eulerZoneGeometricCenter(ci).x
      val geometricCenterY = eulerZoneGeometricCenter(ci).y

      //TODO: calculate tolerance in staticdata
      val tolerance = Math.sqrt(eulerZoneArea(ci)) / 5 // a higher tolerance prevents clusters from moving themselves

      if(allNodes.size > 1) {
        allNodes.foreachElement { nodeIdx =>
          val dx = geometricCenterX - x(nodeIdx)
          val dy = geometricCenterY - y(nodeIdx)
          val distance = Vec2.length(dx, dy) - collisionRadius(nodeIdx) // TODO: avoid sqrt
          //TODO: avoid Vec2 allocation and sqrt
          val velocity = Vec2(dx, dy).normalized * strength * saddle(distance, tolerance) * alpha
          vx(nodeIdx) += velocity.x
          vy(nodeIdx) += velocity.y
        }
      }
    }
  }

  def separateOverlappingEulerSets(
    simData: SimulationData,
    staticData: StaticData,
    strength: Double
  ): Unit = {
    import simData._
    import staticData._
    //TODO: speed up with quadtree?
    for {
      (ai, bi) <- eulerSetDisjunctSetPairs
      pa = eulerSetCollisionPolygon(ai)
      pb = eulerSetCollisionPolygon(bi)
      if !pa(0).x.isNaN && !pb(0).x.isNaN // polygons contain NaNs in the first simulation step
      pushVector <- ConvexPolygon(pa) intersectsMtd ConvexPolygon(pb)
    } {
      // No weight distributed over nodes, since we want to move the whole eulerSet with full speed
      val aPush = -pushVector * strength * alpha
      val bPush = pushVector * strength * alpha

      eulerSetAllNodes(ai).foreach { i =>
        vx(i) += aPush.x
        vy(i) += aPush.y
      }

      eulerSetAllNodes(bi).foreach { i =>
        vx(i) += bPush.x
        vy(i) += bPush.y
      }
    }
  }

  def separateOverlappingEulerZones(
    simData: SimulationData,
    staticData: StaticData,
    strength: Double
  ): Unit = {
    import simData._
    import staticData._
    
    //TODO: speed up with quadtree?
    // make use of eulerZoneAdjacencyMatrix
    for {
      zoneAIdx <- 0 until eulerZoneCount
      zoneBIdx <- zoneAIdx until eulerZoneCount
      if(zoneAIdx != zoneBIdx)
      pa = eulerZoneCollisionPolygon(zoneAIdx)
      pb = eulerZoneCollisionPolygon(zoneBIdx)
      if !pa(0).x.isNaN && !pb(0).x.isNaN // polygons contain NaNs in the first simulation step
      pushVector <- ConvexPolygon(pa) intersectsMtd ConvexPolygon(pb)
    } {
      // No weight distributed over nodes, since we want to move the whole eulerSet with full speed
      val aPush = -pushVector * strength * alpha
      val bPush = pushVector * strength * alpha

      eulerZoneNodes(zoneAIdx).foreach { i =>
        vx(i) += aPush.x
        vy(i) += aPush.y
      }

      eulerZoneNodes(zoneBIdx).foreach { i =>
        vx(i) += bPush.x
        vy(i) += bPush.y
      }
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

  def pushOutOfWrongEulerSet(simData: SimulationData, staticData: StaticData, strength: Double): Unit = {
    import simData._
    import staticData._

    var ci = 0
    val cn = eulerSetCount
    while (ci < cn) {
      val hull = ConvexPolygon(eulerSetCollisionPolygon(ci))

      forAllPointsInRect(quadtree, eulerSetCollisionPolygonAABB(ci)) { ai =>
          val center = Vec2(x(ai), y(ai))
          val radius = staticData.radius(ai) + nodeSpacing

          val belongsToCluster = eulerSetAllNodes(ci).contains(ai) //TODO: fast lookup with precomputed table
          if (!belongsToCluster) {
            val visuallyInCluster = hull intersectsMtd Circle(center, radius)
            visuallyInCluster.foreach { pushVector =>

              val belongsToOtherCluster: Int = eulerSetAllNodes.indexWhere((c: Array[Int]) => c.contains(ai))

              val nodePushDir = belongsToOtherCluster match {
                case -1 => // does not belong to any other cluster, just push it away (shortest way defined by Mtd)
                  pushVector * (alpha * strength)
                case otherClusterIdx =>
                  val otherCenter = eulerSetGeometricCenter(otherClusterIdx)
                  if (hull includes otherCenter) // pushing towards center would make it worse....
                    pushVector * (alpha * strength)
                  else {
                    val directionToCenter = (otherCenter - eulerSetGeometricCenter(ci)).normalized
                    directionToCenter * pushVector.length * (alpha * strength)
                  }
              }

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

  def pushOutOfWrongEulerZone(simData: SimulationData, staticData: StaticData, strength: Double): Unit = {
    import simData._
    import staticData._

    var ci = 0
    val cn = eulerZoneCount
    while (ci < cn) {
      val hull = ConvexPolygon(eulerZoneCollisionPolygon(ci))

      forAllPointsInRect(quadtree, eulerZoneCollisionPolygonAABB(ci)) { ai =>
          val center = Vec2(x(ai), y(ai))
          val radius = staticData.radius(ai) + nodeSpacing

          val belongsToCluster = eulerZoneNodes(ci).contains(ai) //TODO: fast lookup with precomputed table
          if (!belongsToCluster) {
            val visuallyInCluster = hull intersectsMtd Circle(center, radius)
            visuallyInCluster.foreach { pushVector =>

              val belongsToOtherCluster: Int = eulerZoneNodes.indexWhere((c: Array[Int]) => c.contains(ai))

              val nodePushDir = belongsToOtherCluster match {
                case -1 => // does not belong to any other cluster, just push it away (shortest way defined by Mtd)
                  pushVector * (alpha * strength)
                case otherClusterIdx =>
                  val otherCenter = eulerZoneGeometricCenter(otherClusterIdx)
                  if (hull includes otherCenter) // pushing towards center would make it worse....
                    pushVector * (alpha * strength)
                  else {
                    val directionToCenter = (otherCenter - eulerZoneGeometricCenter(ci)).normalized
                    directionToCenter * pushVector.length * (alpha * strength)
                  }
              }

              // push node out
              vx(ai) += nodePushDir.x
              vy(ai) += nodePushDir.y
            }
          }
        }

      ci += 1
    }
  }
}
