package wust.webApp.views.graphview

import d3v4._
import vectory._

import scala.scalajs.js

abstract class CustomForce[N <: SimulationNode] extends js.Object {
  def initialize(nodes: js.Array[N]): Unit = {}
  def force(alpha: Double): Unit
}
object CustomForce {
  implicit def asD3Force[N <: SimulationNode](customForce: CustomForce[N]): Force[N] = {
    val f: js.Function1[Double, Unit] = customForce.force _
    f.asInstanceOf[js.Dynamic].initialize = customForce.initialize _
    f.asInstanceOf[Force[N]]
  }
}

object ForceUtil {
  @inline private def forAllNodes[T](n: QuadtreeNode[T])(code: T => Unit): Unit = {
    def isLeaf = !n.length.isDefined
    if (isLeaf) {
      var maybeNode: js.UndefOr[QuadtreeNode[T]] = n
      while (maybeNode.isDefined) {
        val node = maybeNode.get
        code(node.data)
        maybeNode = node.next
      }
    }
  }

  @inline def forAllPointsInCircle(quadtree: Quadtree[Int], x: Double, y: Double, r: Double)(
      code: Int => Unit
  ): Unit = {
    quadtree.visit { (n: QuadtreeNode[Int], x0: Double, y0: Double, x1: Double, y1: Double) =>
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

  //noinspection ComparingUnrelatedTypes
  @inline def forAllPointsInRect(quadtree: Quadtree[Int], x0: Double, y0: Double, x3: Double, y3: Double)(
      code: Int => Unit
  ): Unit = {
    quadtree.visit { (n: QuadtreeNode[Int], x1: Double, y1: Double, x2: Double, y2: Double) =>
      forAllNodes(n)(code)

      x1 >= x3 || y1 >= y3 || x2 < x0 || y2 < y0
    }
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

//class PushOutOfWrongCluster {
//  import ForceUtil._
//  // pushes a wrong vertex and the closest 2 vertices of the cluster away from each other
//  val minVisibleDistance = Constants.nodePadding
//
//  def force(data: MetaForce, alpha: Double): Unit = {
//    import data._
//    var ci = 0
//    val cn = containmentClusters.size
//    while (ci < cn) {
//      val cluster = containmentClusters(ci)
//      val hull = containmentClusterPolygons(ci)
//      val boundingBox = hull.aabb
//      val voronoiBoundingBox = boundingBox.copy(size = boundingBox.size + 2 * maxRadius + 2 * minVisibleDistance)
//      val wh = voronoiBoundingBox.size.width * 0.5
//      val hh = voronoiBoundingBox.size.height * 0.5
//      val postCount = 2 //cluster.posts.size
//      val forceWeight = 1.0 / (postCount + 1) // per node
//
//      forAllPointsInRect(quadtree, voronoiBoundingBox.center.x - wh, voronoiBoundingBox.center.y - hh, voronoiBoundingBox.center.x + wh, voronoiBoundingBox.center.y + hh) { ai2 =>
//        val ai = ai2 / 2
//        val center = Vec2(pos(ai2), pos(ai2 + 1))
//        val radius = data.radius(ai) + minVisibleDistance
//
//        val belongsToCluster = containmentClusterPostIndices(ci).contains(ai)
//        if (!belongsToCluster) {
//          val visuallyInCluster = hull intersectsMtd Circle(center, radius)
//          visuallyInCluster.foreach { pushVector =>
//            val nodePushDir = pushVector * (alpha * forceWeight)
//
//            // push node out
//            vel(ai2) += nodePushDir.x
//            vel(ai2 + 1) += nodePushDir.y
//
//            // push closest nodes of cluster (forming line segment) back
//            // TODO: the closest points are not necessarily forming the closest line segment.
//            val (ia, ib) = min2By(containmentClusterPostIndices(ci), i => Vec2.lengthSq(pos(i * 2) - center.x, pos(i * 2 + 1) - center.y))
//            vel(ia * 2) += -nodePushDir.x
//            vel(ia * 2 + 1) += -nodePushDir.y
//            vel(ib * 2) += -nodePushDir.x
//            vel(ib * 2 + 1) += -nodePushDir.y
//
//            // containmentClusterPostIndices(ci).toSeq.sortBy(i => Vec2.lengthSq(pos(i * 2) - center.x, pos(i * 2 + 1) - center.y)).take(2).foreach{ i =>
//            //   val i2 = i * 2
//            //   vel(i2) += -nodePushDir.x
//            //   vel(i2 + 1) += -nodePushDir.y
//            // }
//          }
//        }
//      }
//
//      ci += 1
//    }
//  }
//}

//class ClusterCollision {
//
//  // the minimum distance between clusters is already preserved by the pushOutOfWrongCluster-Force
//  def force(data: MetaForce, alpha: Double): Unit = {
//    import data._
//    //TODO: speed up with quadtree?
//    for {
//      js.Tuple2(ai, bi) <- nonIntersectingClusterPairs
//      pa = containmentClusterPolygons(ai)
//      pb = containmentClusterPolygons(bi)
//      pushVector <- pa intersectsMtd pb
//    } {
//      // No weight distributed over nodes, since we want to move the whole cluster with the full speed
//      val aPush = -pushVector * alpha
//      val bPush = pushVector * alpha
//
//      containmentClusterPostIndices(ai).foreach { i =>
//        val i2 = i * 2
//        vel(i2) += aPush.x
//        vel(i2 + 1) += aPush.y
//      }
//
//      containmentClusterPostIndices(bi).foreach { i =>
//        val i2 = i * 2
//        vel(i2) += bPush.x
//        vel(i2 + 1) += bPush.y
//      }
//    }
//  }
//}

//class Clustering {
//
//  val innerVelocityFactor = 0.1
//  def force(data: MetaForce, alpha: Double): Unit = {
//    import data._
//    var ci = 0
//    val cn = containmentClusters.size
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
//        // val targetDistance = collisionRadius(parentI) + (containmentRadius(parentI) - collisionRadius(parentI)) / 2 // stop at center between collisionRadius(parentI) and containmentRadius(parentI)
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
//}
//class MetaForce extends CustomForce[SimPost] {
//  var n: Int = 0
//  var n2: Int = 0
//  var i: Int = 0
//  var i2: Int = 0
//  var nodes = js.Array[SimPost]()
//  var pos: js.Array[Double] = js.Array()
//  var vel: js.Array[Double] = js.Array()
//  var radius: js.Array[Double] = js.Array()
//  var indices: js.Array[Int] = js.Array()
//  var quadtree: Quadtree[Int] = d3.quadtree()
//
//  val nodeIdToIndex = mutable.HashMap.empty[NodeId, Int]
//  var maxRadius = 0.0
//
//  var connections: js.Array[Int] = js.Array()
//  var containments: js.Array[Int] = js.Array()
//  var containmentTest:js.Array[js.Array[Boolean]] = js.Array()
//
//  var containmentClusters: js.Array[ContainmentCluster] = js.Array()
//  var containmentRadius: js.Array[Double] = js.Array()
//  var collisionRadius: js.Array[Double] = js.Array()
//  var postParentCount: js.Array[Int] = js.Array()
//  var containmentClusterParentIndex: js.Array[Int] = js.Array()
//  var containmentClusterChildrenIndices: js.Array[js.Array[Int]] = js.Array()
//  var containmentClusterPostIndices: js.Array[js.Array[Int]] = js.Array()
//  var containmentClusterPolygons: js.Array[ConvexPolygon] = js.Array()
//  var clusterCount: Int = 0
//  var nonIntersectingClusterPairs: js.Array[js.Tuple2[Int, Int]] = js.Array()
//
//  override def initialize(nodes: js.Array[SimPost]): Unit = {
//    /*time(s"initialize: ${nodes.size} nodes")*/ {
//      this.nodes = nodes
//      nodeIdToIndex.clear()
//      nodeIdToIndex ++= nodes.map(_.id).zipWithIndex
//
//      if (nodes.size != n) {
//        n = nodes.size
//        n2 = 2 * n
//        pos = new js.Array(n2)
//        vel = new js.Array(n2)
//        radius = new js.Array(n)
//        indices = (0 until n2 by 2).toJSArray
//      }
//    }
//    updatedNodeSizes() //TODO: is this triggered twice?
//  }
//
//  def setConnections(connections: js.Array[SimConnection]): Unit = {
//    /*time("setConnections")*/ {
//      val m = connections.size
//      this.connections = new js.Array(m * 2)
//      var i = 0
//      while (i < m) {
//        this.connections(i * 2) = nodeIdToIndex(connections(i).source.id)
//        this.connections(i * 2 + 1) = nodeIdToIndex(connections(i).target.id)
//        i += 1
//      }
//    }
//  }
//
//  def setContainments(containments: js.Array[SimContainment]): Unit = {
//    /*time("setConnections")*/ {
//      val m = containments.size
//      this.containments = new js.Array(m * 2)
//      this.postParentCount = new js.Array(n)
//      this.containmentTest = new js.Array(n)
//      var i = 0
//      var j = 0
//      while( i < n) {
//        this.postParentCount(i) = 0
//        this.containmentTest(i) = new js.Array(n)
//        j = 0
//        while(j < n) {
//          this.containmentTest(i)(j) = false
//          j += 1
//        }
//        i += 1
//      }
//      i = 0
//      while (i < m) {
//        val parentIndex = nodeIdToIndex(containments(i).parent.id)
//        val childIndex = nodeIdToIndex(containments(i).child.id)
//        this.containments(i * 2) = parentIndex
//        this.containments(i * 2 + 1) = childIndex
//        this.postParentCount(childIndex) += 1
//        this.containmentTest(parentIndex)(childIndex) = true
//
//        i += 1
//      }
//    }
//  }
//
//  def setContainmentClusters(clusters: js.Array[ContainmentCluster]): Unit = {
//    /*time("setContainmentClusters")*/ {
//      containmentClusters = clusters
//      clusterCount = clusters.size
//      containmentClusterPolygons = new js.Array(clusterCount)
//      nonIntersectingClusterPairs = clusters.toSeq.zipWithIndex.combinations(2).collect {
//        case Seq((a, ai), (b, bi))  if (a.posts intersect b.posts).isEmpty =>
//          js.Tuple2(ai, bi)
//      }.toJSArray
//
//      containmentClusterParentIndex = clusters.map(c => nodeIdToIndex(c.parent.id))
//      containmentClusterChildrenIndices = clusters.map(_.children.map(p => nodeIdToIndex(p.id))(breakOut): js.Array[Int])
//      containmentClusterPostIndices = clusters.map(_.posts.map(p => nodeIdToIndex(p.id))(breakOut): js.Array[Int])
//
//      updatedNodeSizes()
//    }
//  }
//
//  def updatedNodeSizes(): Unit = {
//    /*time("updateNodeSizes")*/ {
//      i = 0
//      radius = new js.Array(n)
//      containmentRadius = new js.Array(n)
//      collisionRadius = new js.Array(n)
//      while(i < n) {
//        radius(i) = nodes(i).radius
//        collisionRadius(i) = nodes(i).collisionRadius
//        containmentRadius(i) = nodes(i).containmentRadius
//        i += 1
//      }
//
//      updateClusterConvexHulls()
//    }
//  }
//
//  def updateClusterConvexHulls(): Unit = {
//    var i = 0
//    while (i < clusterCount) {
//      containmentClusters(i).recalculateConvexHull()
//      containmentClusterPolygons(i) = ConvexPolygon(containmentClusters(i).convexHull.map(p => Vec2(p._1, p._2)))
//      i += 1
//    }
//  }
//
//  def insertNodesIntoQuadtree(): Unit = {
//    quadtree = d3.quadtree(
//      indices,
//      x = (i2: Int) => pos(i2),
//      y = (i2: Int) => pos(i2 + 1)
//    )
//  }
//
//  val rectBound = new RectBound
//  val keepDistance = new KeepDistance
//  val clustering = new Clustering
//  val pushOutOfWrongCluster = new PushOutOfWrongCluster
//  val clusterCollision = new ClusterCollision
//  val connectionDistance = new ConnectionDistance
//  val gravity = new Gravity
//  var updatedInvalidPosition = false
//
//  override def force(alpha: Double): Unit = {
//    /* time("simulation frame") */ {
//      /*time("\nforce.init")*/ {
//        maxRadius = 0.0
//        //read pos + vel from simpost
//        i = 0
//        i2 = 0
//        if (nodes.nonEmpty && (nodes(0).x == js.undefined || nodes(0).x.get.isNaN || nodes(0).x.get == Constants.invalidPosition)) {
//          DevPrintln("initial position!")
//          DevPrintln(InitialPosition.width)
//          DevPrintln(InitialPosition.height)
//        }
//        while (i < n) {
//          if (nodes(i).x == js.undefined || nodes(i).x.get.isNaN || nodes(i).x.get == Constants.invalidPosition) {
//            nodes(i).x = InitialPosition.x(i)
//            updatedInvalidPosition = true
//          }
//          if (nodes(i).y == js.undefined || nodes(i).y.get.isNaN || nodes(i).y.get == Constants.invalidPosition) {
//            nodes(i).y = InitialPosition.y(i)
//            updatedInvalidPosition = true
//          }
//          if (nodes(i).vx == js.undefined || nodes(i).vx.get.isNaN) nodes(i).vx = 0
//          if (nodes(i).vy == js.undefined || nodes(i).vy.get.isNaN) nodes(i).vy = 0
//
//          pos(i2) = nodes(i).x.get
//          pos(i2 + 1) = nodes(i).y.get
//          vel(i2) = nodes(i).vx.get
//          vel(i2 + 1) = nodes(i).vy.get
//          maxRadius = maxRadius max radius(i)
//          i += 1
//          i2 += 2
//        }
//
//        insertNodesIntoQuadtree()
//        if (updatedInvalidPosition) updateClusterConvexHulls()
//        // updateClusterConvexHulls() alse needs to be called on every tick.
//        // But it is called in GraphView.draw() instead
//        // to display the hulls correctly.
//        // they depend on the latest node positions
//        // and therefore need to bee recalculated after "pos += velocity".
//      }
//
//      // apply forces
//      // /*time("gravity")*/ { gravity.force(this, alpha) }
//      /*time("rectBound")*/ { rectBound.force(this, alpha) }
//      /*time("keepDistance")*/ { keepDistance.force(this, alpha, distance = Constants.nodePadding) }
//      // /*time("keepDistance")*/ { keepDistance.force(this, alpha, distance = Constants.nodePadding*3, strength = 0.1) }
//      /*time("clustering")*/ { clustering.force(this, alpha) }
//      /*time("pushOutOfWrongCluster")*/ { pushOutOfWrongCluster.force(this, alpha) }
//      /*time("clusterCollision")*/ { clusterCollision.force(this, alpha) }
//      //TODO: custer - connection collision
//      // /*time("connectionDistance")*/ { connectionDistance.force(this, alpha) }
//
//      /*time("force.apply")*/ {
//        //write pos + vel to simpost
//        i = 0
//        i2 = 0
//        while (i < n) {
//          // currently no force modifies the positions directly
//          // nodes(i).x = pos(i * 2)
//          // nodes(i).y = pos(i * 2 + 1)
//          nodes(i).vx = vel(i2)
//          nodes(i).vy = vel(i2 + 1)
//          i += 1
//          i2 += 2
//        }
//      }
//    }
//    //TODO: render and simulate directly on pos and vel
//    updatedInvalidPosition = false
//  }
//}
//
//class Forces {
//  val gravityX = d3.forceX[SimPost]()
//  val gravityY = d3.forceY[SimPost]()
//  // val repel = d3.forceManyBody[SimPost]()
//  // val collision = d3.forceCollide[SimPost]() //TODO: rectangle collision detection?
//  // val distance = d3.forceCollide[SimPost]()
//  val connection = d3.forceLink[SimPost, SimConnection]()
//  val redirectedConnection = d3.forceLink[SimPost, SimRedirectedConnection]()
//  val containment = d3.forceLink[SimPost, SimContainment]()
//  val collapsedContainment = d3.forceLink[SimPost, SimCollapsedContainment]()
//  //TODO: push posts out of containment clusters they don't belong to
//  val meta = new MetaForce
//}

//object Forces {
//  def apply() = {
//    val forces = new Forces
//
//    // forces.repel.strength((p: SimPost) => -p.radius * 5)
//    // forces.repel.distanceMax(1000)
//    // forces.repel.theta(0.0) // 0 disables approximation
//
//    // forces.collision.radius((p: SimPost) => p.radius)
//    // forces.collision.strength(0.9)
//
//    // forces.distance.radius((p: SimPost) => p.radius + 600)
//    // forces.distance.strength(0.01)
//
//    forces.connection.distance((c: SimConnection) => c.source.radius + Constants.nodePadding + c.target.radius)
//    // forces.connection.strength(0.3)
//    forces.redirectedConnection.distance((c: SimRedirectedConnection) => c.source.radius + Constants.nodePadding + c.target.radius)
//    forces.redirectedConnection.strength(0.3)
//
//    forces.containment.distance((c: SimContainment) => c.parent.radius + Constants.nodePadding + c.child.radius)
//    forces.containment.strength(0.5)
//    forces.collapsedContainment.distance((c: SimCollapsedContainment) => c.parent.radius + Constants.nodePadding + c.child.radius)
//    forces.collapsedContainment.strength(0.01)
//
//    forces.gravityX.strength(0.05)
//    forces.gravityY.strength(0.05)
//
//    forces
//  }
//}
//
//object InitialPosition {
//  var width: Double = 500
//  var height: Double = 500
//
//  val initialRadius = 150
//  val initialAngle = Math.PI * (3 - Math.sqrt(5))
//
//  def strengthX = width / height // longer direction should be farther away
//
//  def x(i: Int) = {
//    val radius = initialRadius * Math.sqrt(i)
//    val angle = i * initialAngle
//    val factor = Math.cos(angle)
//    radius * strengthX * factor
//  }
//
//  def y(i: Int) = {
//    val radius = initialRadius * Math.sqrt(i)
//    val angle = i * initialAngle
//    val factor = Math.sin(angle)
//    radius * factor
//  }
//}
//
//object Simulation {
//  def apply(forces: Forces): Simulation[SimPost] = {
//    val alphaMin = 0.7 // stop simulation earlier (default = 0.001)
//    val ticks = 100 // Default = 300
//    val forceFactor = 0.1
//    d3.forceSimulation[SimPost]()
//      .alphaMin(alphaMin)
//      .alphaDecay(1 - Math.pow(alphaMin, 1.0 / ticks))
//      .velocityDecay(1 - forceFactor) // (1 - velocityDecay) is multiplied before the velocities get applied to the positions https://github.com/d3/d3-force/issues/100
//      .force("meta", forces.meta)
//  }
//}

// TODO: run simulation in tests. jsdom timer bug?
// When running tests with d3-force in jsdom, the d3-timer does not stop itself.
// It should stop when alpha < alphaMin, but is running infinitely, causing a jsdom timeout.
//class D3State(disableSimulation: Boolean = false) {
//  //TODO: dynamic by screen size, refresh on window resize, put into centering force
//  val zoom = d3.zoom().on("zoom.settransform", () => zoomed()).scaleExtent(js.Array(0.01, 10))
//  private def zoomed() = { _transform() = d3.event.asInstanceOf[ZoomEvent].transform }
//  private var _transform: Var[Transform] = Var(d3.zoomIdentity) // stores current pan and zoom
//  def transform:Rx[Transform] = _transform
//
//  val forces = Forces()
//  val simulation = Simulation(forces)
//  if (disableSimulation) simulation.stop()
//}
