package views.graphview

import java.lang.Math._

import d3v4._
import org.scalajs.dom.html
import vectory.Vec2
import views.graphview.ForceSimulationConstants._
import views.graphview.VisualizationType.{Containment, Edge, Tag}
import wust.graph.{Node, _}
import wust.ids._
import wust.sdk.NodeColor._
import wust.util.time.time

import scala.Double.NaN
import scala.collection.mutable.ArrayBuffer
import scala.collection.{breakOut, mutable}
import scala.scalajs.js

/*
 * Data, which is only recalculated once per graph update and stays the same during the simulation
 */
class StaticData(
    val nodeCount: Int,
    val edgeCount: Int,
    val containmentCount: Int,
    var posts: Array[Node],
    val indices: Array[Int],
    val width: Array[Double],
    val height: Array[Double],
    val centerOffsetX: Array[Double],
    val centerOffsetY: Array[Double],
    val radius: Array[Double],
    var maxRadius: Double,
    val collisionRadius: Array[Double],
    val containmentRadius: Array[Double], // TODO: still needed?
    val nodeParentCount: Array[Int],
    val bgColor: Array[String],
    val nodeCssClass: Array[String],
    val nodeReservedArea: Array[Double], //TODO: rename to reservedArea
    var reservedArea: Double, //TODO: rename to totalReservedArea

    // Edges
    val source: Array[Int], //TODO: Rename to edgeSource
    val target: Array[Int], //TODO: Rename to edgeTarget

    // Euler
    val containmentChild: Array[Int],
    val containmentParent: Array[Int],
    val containmentTest: AdjacencyMatrix,
    var eulerSetCount: Int,
    var eulerSetParent: Array[Int],
    var eulerSetChildren: Array[Array[Int]],
    var eulerSetAllNodes: Array[Array[Int]],
    var eulerSetArea: Array[Double],
    var eulerSetRadius: Array[Double],
    var eulerSetColor: Array[String],
    var eulerSetDepth: Array[Int],
) {
  def this(nodeCount: Int, edgeCount: Int, containmentCount: Int) = this(
    nodeCount = nodeCount,
    edgeCount = edgeCount,
    containmentCount = containmentCount,
    posts = null,
    indices = Array.tabulate(nodeCount)(identity),
    width = new Array(nodeCount),
    height = new Array(nodeCount),
    centerOffsetX = new Array(nodeCount),
    centerOffsetY = new Array(nodeCount),
    radius = new Array(nodeCount),
    maxRadius = NaN,
    collisionRadius = new Array(nodeCount),
    containmentRadius = new Array(nodeCount),
    nodeParentCount = new Array(nodeCount),
    bgColor = new Array(nodeCount),
    nodeCssClass = new Array(nodeCount),
    nodeReservedArea = new Array(nodeCount),
    reservedArea = NaN,
    source = new Array(edgeCount),
    target = new Array(edgeCount),
    containmentChild = new Array(containmentCount),
    containmentParent = new Array(containmentCount),
    containmentTest = new AdjacencyMatrix(nodeCount),
    eulerSetCount = -1,
    eulerSetParent = null,
    eulerSetChildren = null,
    eulerSetAllNodes = null,
    eulerSetArea = null,
    eulerSetRadius = null,
    eulerSetColor = null,
    eulerSetDepth = null,
  )
}

class AdjacencyMatrix(nodeCount: Int) {
  private val data = new mutable.BitSet(nodeCount * nodeCount) //TODO: Avoid Quadratic Space complexity when over threshold!
  @inline private def index(source: Int, target: Int): Int = source * nodeCount + target
  @inline def set(source: Int, target: Int): Unit = data.add(index(source, target))
  @inline def apply(source: Int, target: Int): Boolean = data(index(source, target))
}

class EulerSet(val parent: NodeId, val children: Array[NodeId], val depth: Int) {
  val allNodes: Array[NodeId] = children :+ parent
}

class GraphTopology(
    val graph: Graph,
    val posts: Array[Node]
)

object StaticData {
  import ForceSimulation.log

  def apply(
      graphTopology: GraphTopology,
      selection: Selection[Node],
      transform: Transform,
      labelVisualization: PartialFunction[EdgeData.Type, VisualizationType]
  ): StaticData = {
    time(log(s"calculateStaticData[${selection.size()}]")) {
      import graphTopology.{graph, posts}

      val PartitionedConnections(edges, containments, tags) =
        partitionConnections(graph.edges, labelVisualization)
//      println("edges: " + edges.mkString(", "))
//      println("containments: " + containments.mkString(", "))

      val nodeCount = posts.length
      val edgeCount = edges.length
      val containmentCount = containments.length
      val staticData = new StaticData(
        nodeCount = nodeCount,
        edgeCount = edgeCount,
        containmentCount = containmentCount
      )
      staticData.posts = posts
      val scale = transform.k

      @inline def sq(x: Double) = x * x

      var maxRadius = 0.0
      var reservedArea = 0.0
      selection.each[html.Element] { (node: html.Element, post: Node, i: Int) =>
        if(graph.hasChildren(post.id)) {
          staticData.bgColor(i) = nodeColorWithContext(graph, post.id).toCSS
          staticData.nodeCssClass(i) = "tag"
        } else {
          staticData.bgColor(i) = "#FEFEFE" // bgcolor of nodecard
          staticData.nodeCssClass(i) = "nodecard"
        }

        // we set the style here, because the border can affect the size of the element
        // and we want to capture that in the post size
        d3.select(node)
          .style("background-color", staticData.bgColor(i))
          .asInstanceOf[js.Dynamic].classed("tag nodecard", false)
          .classed(staticData.nodeCssClass(i), true)

        val rect = node.getBoundingClientRect
        val width = rect.width / scale
        val height = rect.height / scale
        staticData.width(i) = width
        staticData.height(i) = height
        staticData.centerOffsetX(i) = width / -2.0
        staticData.centerOffsetY(i) = height / -2.0
        staticData.radius(i) = Vec2.length(width, height) / 2.0 + nodePadding
        maxRadius = maxRadius max staticData.radius(i)
        staticData.collisionRadius(i) = staticData.radius(i) + nodeSpacing * 0.5
        staticData.containmentRadius(i) = staticData.collisionRadius(i)

        staticData.nodeParentCount(i) = graph.parents(post.id).size //TODO: faster?

        val area = sq(staticData.collisionRadius(i) * 2) // bounding square of bounding circle
        staticData.nodeReservedArea(i) = area
        reservedArea += area
      }
      staticData.maxRadius = maxRadius
      staticData.reservedArea = reservedArea

      val nodeIdToIndex = createNodeIdToIndexMap(posts)

      var i = 0
      while (i < edgeCount) {
        staticData.source(i) = nodeIdToIndex(edges(i).sourceId)
        staticData.target(i) = nodeIdToIndex(edges(i).targetId)
        i += 1
      }

      i = 0
      while (i < containmentCount) {
        val child = nodeIdToIndex(containments(i).sourceId)
        val parent = nodeIdToIndex(containments(i).targetId)
        staticData.containmentChild(i) = child
        staticData.containmentParent(i) = parent
        staticData.containmentTest.set(child, parent)
        i += 1
      }

      val eulerSets: Array[EulerSet] = {
        //        staticData.parent.map{ pId =>
        //          val p = posts(pId)
        //
        //        }
        graph.allParentIdsTopologicallySortedByChildren.map { nodeIdx =>
          new EulerSet(
            parent = graph.nodeIds(nodeIdx),
            children = graph.descendantsIdx(nodeIdx).map(graph.nodeIds),
            depth = graph.childDepth(graph.nodeIds(nodeIdx))
          )
        }(breakOut)
      }

      //TODO: collapsed euler sets
      // val rxCollapsedContainmentCluster = Rx {
      //   val graph = rxDisplayGraph().graph
      //   val nodeIdToSimPost = rxNodeIdToSimPost()

      //   val children: Map[NodeId, Seq[NodeId]] = rxDisplayGraph().collapsedContainments.groupBy(_.targetId).mapValues(_.map(_.sourceId)(breakOut))
      //   val parents: Iterable[NodeId] = children.keys

      //   parents.map { p =>
      //     new ContainmentCluster(
      //       parent = nodeIdToSimPost(p),
      //       children = children(p).map(p => nodeIdToSimPost(p))(breakOut),
      //       depth = graph.childDepth(p)
      //     )
      //   }.toJSArray
      // }

      i = 0
      val eulerSetCount = eulerSets.length
      staticData.eulerSetCount = eulerSetCount
      staticData.eulerSetAllNodes = new Array[Array[Int]](eulerSetCount)
      staticData.eulerSetChildren = new Array[Array[Int]](eulerSetCount)
      staticData.eulerSetParent = new Array[Int](eulerSetCount)
      staticData.eulerSetArea = new Array[Double](eulerSetCount)
      staticData.eulerSetRadius = new Array[Double](eulerSetCount)
      staticData.eulerSetColor = new Array[String](eulerSetCount)
      staticData.eulerSetDepth = new Array[Int](eulerSetCount)
      while (i < eulerSetCount) {
        staticData.eulerSetChildren(i) = eulerSets(i).children.map(nodeIdToIndex)
        staticData.eulerSetAllNodes(i) = eulerSets(i).allNodes.map(nodeIdToIndex)
        staticData.eulerSetParent(i) = nodeIdToIndex(eulerSets(i).parent)
        staticData.eulerSetDepth(i) = eulerSets(i).depth

        val aribtraryFactor = 1.3
        staticData.eulerSetArea(i) = eulerSets(i).allNodes.map { pid =>
          val pi = nodeIdToIndex(pid)
          staticData.nodeReservedArea(pi)
        }.sum * aribtraryFactor
        staticData.eulerSetRadius(i) = sqrt(staticData.eulerSetArea(i) / PI) // a = pi*r^2 solved by r = sqrt(a/pi)

        val color = d3.lab(eulerBgColor(eulerSets(i).parent).toHex) //TODO: use d3.rgb or make colorado handle opacity
        color.opacity = 0.8
        staticData.eulerSetColor(i) = color.toString

        i += 1
      }
      staticData
    }
  }

  def createNodeIdToIndexMap(posts: Array[Node]): collection.Map[NodeId, Int] = {
    var i = 0
    val n = posts.length
    val map = new mutable.HashMap[NodeId, Int]()
    while (i < n) {
      map(posts(i).id) = i
      i += 1
    }
    map
  }

  private case class PartitionedConnections(
      edges: Array[Edge],
      containments: Array[Edge],
      tags: Array[Edge]
  )

  private def partitionConnections(
      connections: Iterable[Edge],
      labelVisualization: PartialFunction[EdgeData.Type, VisualizationType]
  ): PartitionedConnections = {
    val edgeBuilder = ArrayBuffer.empty[Edge]
    val containmentBuilder = ArrayBuffer.empty[Edge]
    val tagBuilder = ArrayBuffer.empty[Edge]

    def separator = labelVisualization.lift

    connections.foreach { connection =>
      separator(connection.data.tpe).foreach {
        case Edge        => edgeBuilder += connection
        case Containment => containmentBuilder += connection
        case Tag         => tagBuilder += connection
      }
    }

    PartitionedConnections(
      containments = containmentBuilder.toArray,
      edges = edgeBuilder.toArray,
      tags = tagBuilder.toArray
    )
  }
}
