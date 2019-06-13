package wust.webApp.views.graphview

import d3v4._
import flatland._
import org.scalajs.dom.html
import vectory.Vec2
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util.algorithm
import wust.util.time.time
import wust.webApp.views.graphview.ForceSimulationConstants._
import wust.webApp.views.graphview.VisualizationType.{Containment, Edge, Tag}

import scala.Double.NaN
import scala.collection.mutable.ArrayBuffer
import scala.collection.{breakOut, mutable}
import scala.scalajs.js

/*
 * Data, which is only recalculated once per graph update and stays the same during the simulation
 */
class StaticData(
    //TODO: many lookups are already provided by GraphLookup. Use them.
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
    val nodeReservedArea: Array[Double],
    var totalReservedArea: Double,

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
    var eulerSetDisjunctSetPairs: Array[(Int,Int)],
    var eulerSetAllNodes: Array[Array[Int]],
    var eulerSetArea: Array[Double],
    var eulerSetColor: Array[String],
    var eulerSetStrokeColor: Array[String],
    var eulerSetDepth: Array[Int],

    var eulerZoneCount: Int,
    var eulerZones:Array[Set[Int]],
    var eulerZoneNodes: NestedArrayInt,
    var eulerZoneArea: Array[Double],
    var eulerZoneNeighbourhoods:InterleavedArrayInt,

    var eulerSetConnectedComponentCount:Int,
    var eulerSetConnectedComponentNodes: NestedArrayInt,
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
    totalReservedArea = NaN,
    source = new Array(edgeCount),
    target = new Array(edgeCount),
    containmentChild = new Array(containmentCount),
    containmentParent = new Array(containmentCount),
    containmentTest = new AdjacencyMatrix(nodeCount),
    eulerSetCount = -1,
    eulerSetParent = null,
    eulerSetChildren = null,
    eulerSetDisjunctSetPairs = null,
    eulerSetAllNodes = null,
    eulerSetArea = null,
    eulerSetColor = null,
    eulerSetStrokeColor = null,
    eulerSetDepth = null,

    eulerZoneCount = -1,
    eulerZones = null,
    eulerZoneNodes = null,
    eulerZoneArea = null,
    eulerZoneNeighbourhoods = null,

    eulerSetConnectedComponentCount = -1,
    eulerSetConnectedComponentNodes = null,
  )
}

class AdjacencyMatrix(nodeCount: Int) {
  private val data = new mutable.BitSet(nodeCount * nodeCount) //TODO: Avoid Quadratic Space complexity when over threshold!
  @inline private def index(source: Int, target: Int): Int = source * nodeCount + target
  @inline def set(source: Int, target: Int): Unit = data.add(index(source, target))
  @inline def apply(source: Int, target: Int): Boolean = data(index(source, target))
}

class EulerSet(val parent: Int, val children: Array[Int], val depth: Int) {
  val allNodes: Array[Int] = children :+ parent
}

object StaticData {
  import ForceSimulation.log

  def apply(
      graph: Graph,
      selection: d3.Selection[Node],
      transform: d3.Transform,
      labelVisualization: PartialFunction[EdgeData.Type, VisualizationType]
  ): StaticData = {
    time(log(s"calculateStaticData[${selection.size()}]")) {

      val PartitionedConnections(edges, containments, tags) =
        partitionConnections(graph.edges, labelVisualization)
//      println("edges: " + edges.mkString(", "))
//      println("containments: " + containments.mkString(", "))

      val nodeCount = graph.nodes.length
      val edgeCount = edges.length
      val containmentCount = containments.length
      val staticData = new StaticData(
        nodeCount = nodeCount,
        edgeCount = edgeCount,
        containmentCount = containmentCount
      )
      staticData.posts = graph.nodes
      val scale = transform.k

      @inline def sq(x: Double) = x * x

      var maxRadius = 0.0
      var reservedArea = 0.0
      selection.each[html.Element] { (elem: html.Element, node: Node, i: Int) =>
        if(graph.nodesByIdOrThrow(node.id).role == NodeRole.Tag) {
          staticData.bgColor(i) = tagColor(node.id).toCSS
          staticData.nodeCssClass(i) = "graphnode-tag"
        } else {
          staticData.nodeCssClass(i) = "nodecard node"
        }

        // we set the style here, because the border can affect the size of the element
        // and we want to capture that in the post size
        @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
        val elemSelection = d3.select(elem)
          .asInstanceOf[js.Dynamic]
          .classed("tag nodecard", false)
          .classed(staticData.nodeCssClass(i), true)
        if(staticData.bgColor(i) != null)
          elemSelection.style("background-color", staticData.bgColor(i))

        val rect = elem.getBoundingClientRect
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

        staticData.nodeParentCount(i) = graph.parents(node.id).size //TODO: faster?

        val area = sq(staticData.collisionRadius(i) * 2) // bounding square of bounding circle
        staticData.nodeReservedArea(i) = area
        reservedArea += area
      }
      staticData.maxRadius = maxRadius
      staticData.totalReservedArea = reservedArea

      var i = 0
      while (i < edgeCount) {
        staticData.source(i) = graph.idToIdxOrThrow(edges(i).sourceId)
        staticData.target(i) = graph.idToIdxOrThrow(edges(i).targetId)
        i += 1
      }

      i = 0
      while (i < containmentCount) {
        val child = graph.idToIdxOrThrow(containments(i).targetId)
        val parent = graph.idToIdxOrThrow(containments(i).sourceId)
        staticData.containmentChild(i) = child
        staticData.containmentParent(i) = parent
        staticData.containmentTest.set(child, parent)
        i += 1
      }

      val eulerSets: Array[EulerSet] = {
        graph.allParentIdsTopologicallySortedByChildren.map { nodeIdx =>
          val depth = graph.childDepth(nodeIdx)
          new EulerSet(
            parent = nodeIdx,
            children = graph.descendantsIdx(nodeIdx),
            depth = depth
          )
        }(breakOut)
      }

      {
        // create connected components of eulersets
        val used = ArraySet.create(eulerSets.length)
        val current = mutable.ArrayBuilder.make[Int]
        val eulerSetConnectedComponentsBuilder = mutable.ArrayBuilder.make[Array[Int]]

        def add(i:Int):Unit = {
          if(used.containsNot(i)) {
            val eulerSet = eulerSets(i)
            current ++= eulerSet.allNodes
            used += i
            eulerSets.foreachIndexAndElement { (otherI,otherEulerSet) =>
              if( graph.parentsIdx(otherEulerSet.parent).toSet == graph.parentsIdx(eulerSet.parent).toSet && (eulerSet.allNodes intersect otherEulerSet.allNodes).nonEmpty)
                add(otherI)
            }
          }
        }
        eulerSets.foreachIndex{ i =>
          if(used.containsNot(i)) {
            add(i)
            eulerSetConnectedComponentsBuilder += current.result()
            current.clear()
          }
        }

        val eulerSetConnectedComponents = eulerSetConnectedComponentsBuilder.result()
        staticData.eulerSetConnectedComponentCount = eulerSetConnectedComponents.length
        staticData.eulerSetConnectedComponentNodes = NestedArrayInt(eulerSetConnectedComponents)
        println(s"COUNT: ${eulerSetConnectedComponents.length}")
      }

      {
      // constructing the dual graph of the euler diagram
      // each zone represents a node,
      // every two zones which are separated by a line of the euler diagram become an edge
        // for each node, the set of parent nodes identifies its zone
        println(graph.toDetailedString)
        val (eulerZones, eulerZoneNodes, edges) = algorithm.eulerDiagramDualGraph(graph.parentsIdx, graph.childrenIdx, graph.nodes.indices.filter(idx => graph.nodes(idx).role == NodeRole.Tag).toSet)
        staticData.eulerZoneCount = eulerZones.length
        staticData.eulerZones = eulerZones
        staticData.eulerZoneNodes = eulerZoneNodes
        staticData.eulerZoneNeighbourhoods = edges

        staticData.eulerZoneArea = new Array[Double](eulerZones.length)
        eulerZoneNodes.foreachIndexAndSlice { (i,zoneNodes) =>
          val arbitraryFactor = 1.3
          staticData.eulerZoneArea(i) = zoneNodes.map { nodeIdx =>
            staticData.nodeReservedArea(nodeIdx)
          }.sum * arbitraryFactor
        }

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
      staticData.eulerSetColor = new Array[String](eulerSetCount)
      staticData.eulerSetStrokeColor = new Array[String](eulerSetCount)
      staticData.eulerSetDepth = new Array[Int](eulerSetCount)
      while (i < eulerSetCount) {
        staticData.eulerSetChildren(i) = eulerSets(i).children
        staticData.eulerSetAllNodes(i) = eulerSets(i).allNodes
        staticData.eulerSetParent(i) = eulerSets(i).parent
        staticData.eulerSetDepth(i) = eulerSets(i).depth

        val arbitraryFactor = 1.3
        staticData.eulerSetArea(i) = eulerSets(i).allNodes.map { pid =>
          val pi = pid
          staticData.nodeReservedArea(pi)
        }.sum * arbitraryFactor

        val color = d3.lab(eulerBgColor(graph.nodeIds(eulerSets(i).parent)).toHex) //TODO: use d3.rgb or make colorado handle opacity
        color.opacity = 0.35
        staticData.eulerSetColor(i) = color.toString
        staticData.eulerSetStrokeColor(i) = eulerBgColor(graph.nodeIds(eulerSets(i).parent)).toHex

        i += 1
      }

      staticData.eulerSetDisjunctSetPairs = (for {
        i <- 0 until eulerSetCount
        j <- 0 until eulerSetCount
        if i > j && staticData.eulerSetChildren(i).intersect(staticData.eulerSetChildren(j)).isEmpty
      } yield (i,j)).toArray


      staticData
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
  private final case class PartitionedConnections(
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
