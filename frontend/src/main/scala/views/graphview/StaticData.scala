package views.graphview

import wust.frontend.views.graphview.Constants
import wust.graph.Post

import scala.Double.NaN

class AdjacencyMatrix(nodeCount: Int) {
  private val matrix = new Array[Boolean](nodeCount * nodeCount)
  @inline private def index(source:Int, target:Int): Int = source*nodeCount + target

  @inline def update(source:Int, target:Int, value:Boolean):Unit = {
    matrix(index(source, target)) = value
  }

  @inline def apply(source:Int, target:Int):Boolean = {
    matrix(index(source, target))
  }
}

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
                  val bgColor: Array[String],
                  val border: Array[String],
                  var reservedArea: Double,

                  // Edges
                  val source: Array[Int],
                  val target: Array[Int],

                  // Euler
                  val child: Array[Int],
                  val parent: Array[Int],
                  var eulerSetColor: Array[String],
                  var eulerSets: Array[Array[Int]],
                  val containmentTest: AdjacencyMatrix
              ) {
  def this(nodeCount: Int, edgeCount: Int, containmentCount:Int) = this(
    nodeCount = nodeCount,
    edgeCount = edgeCount,
    containmentCount = containmentCount,

    posts = null,
    indices = Array.tabulate(nodeCount)(identity),
    width = Array.fill(nodeCount)(NaN),
    height = Array.fill(nodeCount)(NaN),
    centerOffsetX = Array.fill(nodeCount)(NaN),
    centerOffsetY = Array.fill(nodeCount)(NaN),
    radius = Array.fill(nodeCount)(NaN),
    maxRadius = NaN,
    collisionRadius = Array.fill(nodeCount)(NaN),
    containmentRadius = Array.fill(nodeCount)(NaN),
    bgColor = Array.fill(nodeCount)("green"),
    border = Array.fill(nodeCount)("red"),
    reservedArea = NaN,

    source = Array.fill(edgeCount)(-1),
    target = Array.fill(edgeCount)(-1),

    child = Array.fill(containmentCount)(-1),
    parent = Array.fill(containmentCount)(-1),
    eulerSetColor = null,
    eulerSets = null,
    containmentTest = new AdjacencyMatrix(nodeCount), //TODO: Quadratic Space complexity!
  )
}
