package frontend

import scalajs.js
import org.scalajs.dom._

//TODO: external library

@js.native
object d3 extends js.Object {
  def zoomIdentity: Transform = js.native
  def polygonHull(points: js.Array[js.Array[Double]]): js.Array[js.Array[Double]] = js.native

  @js.native
  object event extends js.Object {
    def transform: Transform = js.native
    def x: Double = js.native
    def y: Double = js.native
  }

  def forceSimulation[N](nodes: js.Array[N] = js.Array[N]()): ForceSimulation[N] = js.native
  def forceCenter(x: Double = 0, y: Double = 0): Centering = js.native
  def forceX[N](x: Double = 0): PositioningX[N] = js.native
  def forceY[N](y: Double = 0): PositioningY[N] = js.native
  def forceManyBody[N](): ManyBody[N] = js.native
  def forceCollide[N](radius: Double = 1): Collision[N] = js.native
  def forceLink[L](links: js.Array[L] = js.Array[L]()): Link[L] = js.native
}

@js.native
trait ForceSimulation[N] extends js.Object {
  def force(name: String, force: Force): this.type = js.native
  def force[F <: Force](name: String): F = js.native
  def nodes(nodes: js.Array[N]): this.type = js.native
  def on(typenames: String, listener: js.Function0[Unit]): this.type = js.native
  def on(typenames: String): this.type = js.native
  def find(x: Double, y: Double, radius: Double = Double.PositiveInfinity): js.UndefOr[N] = js.native
  def alpha(alpha: Double): this.type = js.native
  def alphaTarget(target: Double): this.type = js.native
  def alphaTarget(): Double = js.native
  def restart(): this.type = js.native
}

@js.native
trait Force extends js.Object {}

@js.native
trait Centering extends js.Object with Force {
  def x(x: Double): this.type = js.native
  def y(y: Double): this.type = js.native
  def x(): Double = js.native
  def y(): Double = js.native
}

@js.native
trait PositioningX[N] extends js.Object with Force {
  def x(x: Double): this.type = js.native
  def strength(strength: Double): this.type = js.native
}

@js.native
trait PositioningY[N] extends js.Object with Force {
  def y(y: Double): this.type = js.native
  def strength(strength: Double): this.type = js.native
}

@js.native
trait ManyBody[N] extends js.Object with Force {
  def strength(strength: Double): this.type = js.native
}

@js.native
trait Collision[N] extends js.Object with Force {
  def radius(radius: js.Function1[N, Double]): this.type = js.native
}

@js.native
trait Link[L] extends js.Object with Force {
  def distance(distance: Double): this.type = js.native
  def strength(strength: js.Function1[L, Double]): this.type = js.native
  def links(links: js.Array[L]): this.type = js.native
}

@js.native
trait Transform extends js.Object {
  override def toString: String = js.native
  def applyX(x: Double): Double = js.native
  def applyY(x: Double): Double = js.native
  def invertX(x: Double): Double = js.native
  def invertY(x: Double): Double = js.native
  def x: Double = js.native
  def y: Double = js.native
  def k: Double = js.native
  def translate(x: Double, y: Double): Transform
  def scale(k: Double): Transform
}

// trait D3Force[N <: graph.D3SimulationNode, L <: graph.D3SimulationLink] {
//   def force(alpha: Double)
//   def initialize(newNodes: js.Array[N])

//   type F = js.Function1[Double, Unit]
//   def apply(): F = {
//     val f: (F) = force _
//     f.asInstanceOf[js.Dynamic].initialize = initialize _
//     f
//   }
// }

// class CustomLinkForce extends D3Force[Post, RespondsTo] {
//   // ported from
//   // https://github.com/d3/d3-force/blob/master/src/link.js
//   type N = Post
//   type L = RespondsTo

//   implicit def undefOrToRaw[T](undefOr: js.UndefOr[T]): T = undefOr.get

//   private var _links: js.Array[L] = js.Array[L]()
//   def links = _links
//   def links_=(newLinks: js.Array[L]) { _links = newLinks; initialize(nodes) }

//   def strength(link: L, i: Int, links: js.Array[L]) = defaultStrength(link)
//   def distance(link: L, i: Int, links: js.Array[L]) = 100

//   private var strengths: js.Array[Double] = js.Array[Double]()
//   private var distances: js.Array[Double] = js.Array[Double]()
//   private var nodes: js.Array[N] = js.Array[N]()
//   private var degree: js.Array[Int] = js.Array[Int]()
//   private var bias: js.Array[Double] = js.Array[Double]()
//   var iterations = 1

//   def defaultStrength(link: L) = {
//     1.0 // / min(degree(link.source.index), degree(link.target.index));
//   }

//   def force(alpha: Double) {
//     // println(s"force: nodes(${nodes.size}), links(${links.size})")
//     var k = 0
//     var i = 0
//     val n = links.size
//     while (k < iterations) {
//       i = 0
//       while (i < n) {
//         val link = links(i)
//         val source = link.source
//         val target = link.target

//         def jiggle() = scala.util.Random.nextDouble //TODO: what is the original implementation of D3?
//         var x: Double = (target.x + target.vx - source.x - source.vx).asInstanceOf[js.UndefOr[Double]].getOrElse(jiggle())
//         var y: Double = (target.y + target.vy - source.y - source.vy).asInstanceOf[js.UndefOr[Double]].getOrElse(jiggle())

//         var l = sqrt(x * x + y * y)
//         l = (l - distances(i)) / l * alpha * strengths(i)
//         x *= l
//         y *= l

//         var b = bias(i)
//         target.vx -= x * b
//         target.vy -= y * b
//         b = 1 - b
//         source.vx += x * b
//         source.vy += y * b
//         i += 1
//       }
//       k += 1
//     }
//   }

//   def initialize(newNodes: js.Array[N]) {
//     nodes = newNodes
//     // println(s"initialize:  nodes(${nodes.size}), links(${links.size})")
//     if (nodes.isEmpty) return ;

//     var i = 0
//     val n = nodes.size
//     val m = links.size

//     i = 0
//     degree = Array.fill(n)(0).toJSArray
//     while (i < m) {
//       val link = links(i)
//       link.index = i;
//       degree(link.source.index) += 1
//       degree(link.target.index) += 1
//       i += 1
//     }

//     i = 0
//     bias = new js.Array[Double](m)
//     while (i < m) {
//       val link = links(i)
//       bias(i) = degree(link.source.index).toDouble / (degree(link.source.index) + degree(link.target.index))
//       i += 1
//     }

//     strengths = new js.Array[Double](m)
//     initializeStrength()
//     distances = new js.Array[Double](m)
//     initializeDistance()
//   }

//   def initializeStrength() {
//     if (nodes.isEmpty) return ;

//     var i = 0
//     val n = links.size
//     while (i < n) {
//       strengths(i) = strength(links(i), i, links)
//       i += 1
//     }
//   }

//   def initializeDistance() {
//     if (nodes.isEmpty) return ;

//     var i = 0
//     val n = links.size
//     while (i < n) {
//       distances(i) = distance(links(i), i, links);
//       i += 1
//     }
//   }
// }
