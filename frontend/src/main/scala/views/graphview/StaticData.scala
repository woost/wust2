package views.graphview

import d3v4._
import org.scalajs.dom.html
import vectory.Vec2
import views.graphview.VisualizationType.{Containment, Edge, Tag}
import wust.frontend.Color.baseColor
import wust.frontend.ColorPost
import wust.frontend.views.graphview.Constants
import wust.graph.{Post, _}
import wust.ids.{Label, PostId}
import wust.util.time.time
import Math._

import scala.Double.NaN
import scala.collection.mutable.ArrayBuffer
import scala.collection.{breakOut, mutable}

/*
 * Data, which is only recalculated once per graph update and stays the same during the simulation
 */
class StaticData(
                  val nodeCount: Int,
                  val edgeCount: Int,
                  val containmentCount: Int,

                  var posts: Array[Post],
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
                  val border: Array[String],
                  val nodeReservedArea:Array[Double], //TODO: rename to reservedArea
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
              ) {
  def this(nodeCount: Int, edgeCount: Int, containmentCount:Int) = this(
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
    border = new Array(nodeCount),
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
  )
}

class AdjacencyMatrix(nodeCount: Int) {
  private val data = new mutable.BitSet(nodeCount * nodeCount) //TODO: Avoid Quadratic Space complexity when over threshold!
  @inline private def index(source:Int, target:Int): Int = source*nodeCount + target
  @inline def set(source:Int, target:Int):Unit = data.add(index(source, target))
  @inline def apply(source:Int, target:Int):Boolean = data(index(source, target))
}


class EulerSet(val parent: PostId, val children: Array[PostId], val depth: Int) {
  val allNodes: Array[PostId] = children :+ parent
}

class GraphTopology(
                     val graph: Graph,
                     val posts: Array[Post]
                   )

object StaticData {
  import ForceSimulation.log

  def apply(graphTopology: GraphTopology, selection: Selection[Post], transform: Transform, labelVisualization:PartialFunction[Label, VisualizationType]): StaticData = {
    time(log(s"calculateStaticData[${selection.size()}]")) {
      import graphTopology.{graph, posts}

      val PartitionedConnections(edges,containments,tags) = partitionConnections(graph.connections, labelVisualization)
      println("edges: " + edges.mkString(", "))
      println("containments: " + containments.mkString(", "))

      val nodeCount = posts.length
      val edgeCount = edges.length
      val containmentCount = containments.length
      val staticData = new StaticData(nodeCount = nodeCount,edgeCount = edgeCount,containmentCount = containmentCount)
      staticData.posts = posts
      val scale = transform.k

      @inline def sq(x:Double) = x * x

      var maxRadius = 0.0
      var reservedArea = 0.0
      selection.each[html.Element] { (node: html.Element, post: Post, i: Int) =>
        staticData.bgColor(i) = ColorPost.computeColor(graph, post.id).toString
        staticData.border(i) = if(graph.hasChildren(post.id)) s"10px solid ${baseColor(post.id)}" else "1px solid #DFDFDF"
        // we set the style here, because the border can affect the size of the element
        // and we want to capture that in the post size
        d3.select(node)
          .style("background-color", staticData.bgColor(i))
          .style("border", staticData.border(i))

        val rect = node.getBoundingClientRect
        val width = rect.width / scale
        val height = rect.height / scale
        staticData.width(i) = width
        staticData.height(i) = height
        staticData.centerOffsetX(i) = width / -2.0
        staticData.centerOffsetY(i) = height / -2.0
        staticData.radius(i) = Vec2.length(width, height) / 2.0
        maxRadius = maxRadius max staticData.radius(i)
        staticData.collisionRadius(i) = staticData.radius(i) + Constants.nodePadding * 0.5
        staticData.containmentRadius(i) = staticData.collisionRadius(i)

        staticData.nodeParentCount(i) = graph.parents(post.id).size //TODO: faster?

        val area = sq(staticData.collisionRadius(i) * 2) // bounding square of bounding circle
        staticData.nodeReservedArea(i) = area
        reservedArea += area
      }
      staticData.maxRadius = maxRadius
      staticData.reservedArea = reservedArea

      val postIdToIndex = createPostIdToIndexMap(posts)

      var i = 0
      while (i < edgeCount) {
        staticData.source(i) = postIdToIndex(edges(i).sourceId)
        staticData.target(i) = postIdToIndex(edges(i).targetId)
        i += 1
      }

      i = 0
      while(i < containmentCount) {
        val child = postIdToIndex(containments(i).sourceId)
        val parent = postIdToIndex(containments(i).targetId)
        staticData.containmentChild(i) = child
        staticData.containmentParent(i) = parent
        staticData.containmentTest.set(child, parent)
        i += 1
      }


      val eulerSets:Array[EulerSet] = {
        //        staticData.parent.map{ pId =>
        //          val p = posts(pId)
        //
        //        }
        graph.allParentIdsTopologicallySortedByChildren.map { p =>
          new EulerSet(
            parent = p,
            children = graph.descendants(p).toArray,
            depth = graph.childDepth(p)
          )
        }(breakOut)
      }


      i = 0
      val eulerSetCount = eulerSets.length
      staticData.eulerSetCount = eulerSetCount
      staticData.eulerSetAllNodes = new Array[Array[Int]](eulerSetCount)
      staticData.eulerSetChildren = new Array[Array[Int]](eulerSetCount)
      staticData.eulerSetParent = new Array[Int](eulerSetCount)
      staticData.eulerSetArea = new Array[Double](eulerSetCount)
      staticData.eulerSetRadius = new Array[Double](eulerSetCount)
      staticData.eulerSetColor = new Array[String](eulerSetCount)
      while(i < eulerSetCount) {
        staticData.eulerSetChildren(i) = eulerSets(i).children.map(postIdToIndex)
        staticData.eulerSetAllNodes(i) = eulerSets(i).allNodes.map(postIdToIndex)
        staticData.eulerSetParent(i) = postIdToIndex(eulerSets(i).parent)
        val aribtraryFactor = 1.3
        staticData.eulerSetArea(i) = eulerSets(i).allNodes.map{ pid =>
          val pi = postIdToIndex(pid)
          staticData.nodeReservedArea(pi)
        }.sum * aribtraryFactor
        staticData.eulerSetRadius(i) = sqrt(staticData.eulerSetArea(i)/PI) // a = pi*r^2 solved by r = sqrt(a/pi)

        val color = baseColor(eulerSets(i).parent)
        color.opacity = 0.8
        staticData.eulerSetColor(i) = color.toString

        i += 1
      }
      staticData
    }
  }

  def createPostIdToIndexMap(posts: Array[Post]): collection.Map[PostId, Int] = {
    var i = 0
    val n = posts.length
    val map = new mutable.HashMap[PostId, Int]()
    while (i < n) {
      map(posts(i).id) = i
      i += 1
    }
    map
  }

  private case class PartitionedConnections(
                                     edges:Array[Connection],
                                     containments:Array[Connection],
                                     tags:Array[Connection]
                                   )

  private def partitionConnections(connections:Iterable[Connection], labelVisualization:PartialFunction[Label, VisualizationType]):PartitionedConnections = {
    val edgeBuilder = ArrayBuffer.empty[Connection]
    val containmentBuilder = ArrayBuffer.empty[Connection]
    val tagBuilder = ArrayBuffer.empty[Connection]

    def separator = labelVisualization.lift

    connections.foreach { connection =>
      separator(connection.label).foreach{
        case Edge => edgeBuilder += connection
        case Containment => containmentBuilder += connection
        case Tag => tagBuilder += connection
      }
    }

    PartitionedConnections(
      containments = containmentBuilder.toArray,
      edges = edgeBuilder.toArray,
      tags = tagBuilder.toArray
    )
  }
}
