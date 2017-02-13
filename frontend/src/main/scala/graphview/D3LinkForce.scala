package frontend.graphview

import scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.d3v4._
import js.JSConverters._
import math._
import graph._

// @ScalaJSDefined
// trait D3Force[N <: SimulationNode] {
//   def force(alpha: Double)
//   def initialize(newNodes: js.Array[N])

//   type F = js.Function1[Double, Unit]
//   def apply(): F = {
//     val f: (F) = force _
//     f.asInstanceOf[js.Dynamic].initialize = initialize _
//     f
//   }
// }

@ScalaJSDefined
class CustomLinkForce[N <: SimulationNode, L <: SimulationLink[_ <: N, _ <: N]] extends Force[N] {

  type F = js.Function1[Double, Unit]
  def forJavaScriptIdiots(): F = {
    val f: F = force _
    f.asInstanceOf[js.Dynamic].initialize = initialize _
    f
  }

  // ported from
  // https://github.com/d3/d3-force/blob/master/src/link.js
  implicit def undefOrToRaw[T](undefOr: js.UndefOr[T]): T = undefOr.get

  private var _links: js.Array[L] = js.Array[L]()
  def links = _links
  def links_=(newLinks: js.Array[L]) { _links = newLinks; initialize(nodes) }

  var strength = (link: L, i: Int, links: js.Array[L]) => defaultStrength(link)
  var distance = (link: L, i: Int, links: js.Array[L]) => 100

  private var strengths: js.Array[Double] = js.Array[Double]()
  private var distances: js.Array[Double] = js.Array[Double]()
  private var nodes: js.Array[N] = js.Array[N]()
  private var degree: js.Array[Int] = js.Array[Int]()
  private var bias: js.Array[Double] = js.Array[Double]()
  var iterations = 1

  def defaultStrength(link: L) = {
    1.0 // / min(degree(link.source.index), degree(link.target.index));
  }

  def force(alpha: Double) {
    // println(s"force: nodes(${nodes.size}), links(${links.size})")
    var k = 0
    var i = 0
    val n = links.size
    while (k < iterations) {
      i = 0
      while (i < n) {
        val link = links(i)
        val source = link.source
        val target = link.target

        def jiggle() = scala.util.Random.nextDouble //TODO: what is the original implementation of D3?
        var x: Double = (target.x + target.vx - source.x - source.vx).asInstanceOf[js.UndefOr[Double]].getOrElse(jiggle())
        var y: Double = (target.y + target.vy - source.y - source.vy).asInstanceOf[js.UndefOr[Double]].getOrElse(jiggle())

        var l = sqrt(x * x + y * y)
        l = (l - distances(i)) / l * alpha * strengths(i)
        x *= l
        y *= l

        var b = bias(i)
        target.vx -= x * b
        target.vy -= y * b
        b = 1 - b
        source.vx += x * b
        source.vy += y * b
        i += 1
      }
      k += 1
    }
  }

  def initialize(newNodes: js.Array[N]) {
    nodes = newNodes
    //TODO: initialize is called too often:
    // - on every node and on every link update => set both at the same time
    // println(s"initialize:  nodes(${nodes.size}), links(${links.size})")
    if (nodes.isEmpty) return ;

    var i = 0
    val n = nodes.size
    val m = links.size

    i = 0
    degree = Array.fill(n)(0).toJSArray
    while (i < m) {
      val link = links(i)
      link.index = i;
      degree(link.source.index) += 1
      degree(link.target.index) += 1
      i += 1
    }

    i = 0
    bias = new js.Array[Double](m)
    while (i < m) {
      val link = links(i)
      bias(i) = degree(link.source.index).toDouble / (degree(link.source.index) + degree(link.target.index))
      i += 1
    }

    strengths = new js.Array[Double](m)
    initializeStrength()
    distances = new js.Array[Double](m)
    initializeDistance()
  }

  def initializeStrength() {
    if (nodes.isEmpty) return ;

    var i = 0
    val n = links.size
    while (i < n) {
      strengths(i) = strength(links(i), i, links)
      i += 1
    }
  }

  def initializeDistance() {
    if (nodes.isEmpty) return ;

    var i = 0
    val n = links.size
    while (i < n) {
      distances(i) = distance(links(i), i, links);
      i += 1
    }
  }
}
