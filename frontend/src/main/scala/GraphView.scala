package frontend

import scalajs.js
import js.JSConverters._
import scala.scalajs.js.annotation._
import org.scalajs.dom._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import vectory._

import graph._
import collection.breakOut
import math._

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

case class ContainmentCluster(parent: Post, children: IndexedSeq[Post]) {
  def positions = (children :+ parent).map(_.pos)
  def convexHull = Algorithms.convexHull(positions)
}

object GraphView extends CustomComponent[Graph]("GraphView") {
  import js.Dynamic.global
  val d3 = global.d3

  val width = 640
  val height = 480

  class Backend($: Scope) extends CustomBackend($) {
    lazy val container = d3.select(component)
    lazy val svg = container.append("svg")
    lazy val html = container.append("div")
    lazy val postElements = html.append("div")
    lazy val respondsToElements = svg.append("g")
    lazy val containmentElements = svg.append("g")
    lazy val containmentHulls = svg.append("g")

    var postData: js.Array[Post] = js.Array()
    var respondsToData: js.Array[RespondsTo] = js.Array()
    var containmentData: js.Array[Contains] = js.Array()
    var containmentClusters: js.Array[ContainmentCluster] = js.Array()

    // val customLinkForce = new CustomLinkForce
    val simulation = d3.forceSimulation()
      .force("center", d3.forceCenter())
      .force("gravityx", d3.forceX())
      .force("gravityy", d3.forceY())
      .force("repel", d3.forceManyBody())
      // .force("respondsTo", customLinkForce())
      .force("respondsTo", d3.forceLink())
      .force("containment", d3.forceLink())
    // .force("collision", d3.forceCollide())

    simulation.on("tick", (e: Event) => {
      draw($.props.runNow())
    })

    override def init(p: Props) = Callback {
      // init lazy vals to set drawing order
      container
      svg
      containmentHulls
      containmentElements
      respondsToElements
      html
      postElements

      container
        .style("position", "relative")
        .style("width", s"${width}px")
        .style("height", s"${height}px")
        .style("border", "1px solid #DDDDDD")
        .style("overflow", "hidden")

      svg
        .style("position", "absolute")
        .attr("width", width)
        .attr("height", height)

      html
        .style("position", "absolute")
        .style("width", s"${width}px")
        .style("height", s"${height}px")
        .style("pointer-events", "none") // pass through to svg (e.g. zoom)
        .style("transform-origin", "top left") // same as svg default

      svg.call(d3.zoom().on("zoom", () => zoomed($.props.runNow())))

      simulation.force("center").x(width / 2).y(height / 2)
      simulation.force("gravityx").x(width / 2)
      simulation.force("gravityy").y(height / 2)

      simulation.force("respondsTo").distance(100)
      simulation.force("repel").strength(-1000)

      simulation.force("gravityx").strength(0.1)
      simulation.force("gravityy").strength(0.1)
    }

    def zoomed(p: Props) {
      val transform = d3.event.transform
      svg.selectAll("g").attr("transform", transform);
      html.style("transform", "translate(" + transform.x + "px," + transform.y + "px) scale(" + transform.k + ")");
    }

    override def update(p: Props, oldProps: Option[Props] = None) = Callback {
      val graph = p
      import graph.posts
      import graph.respondsTos
      import graph.containment

      postData = p.posts.values.toJSArray
      val post = postElements.selectAll("div")
        .data(postData, (p: Post) => p.id)

      respondsToData = p.respondsTos.values.map { e =>
        e.source = posts(e.in)
        e.target = posts.getOrElse(e.out, respondsTos(e.out))
        e
      }.toJSArray
      val respondsTo = respondsToElements.selectAll("line")
        .data(respondsToData, (r: RespondsTo) => r.id)

      containmentData = p.containment.values.map { e =>
        e.source = posts(e.parent)
        e.target = posts(e.child)
        e
      }.toJSArray
      val contains = containmentElements.selectAll("line")
        .data(containmentData, (r: Contains) => r.id)

      containmentClusters = {
        val parents: Seq[Post] = containment.values.map(c => posts(c.parent)).toSeq.distinct
        parents.map(p => new ContainmentCluster(p, graph.children(p).toIndexedSeq)).toJSArray
      }
      val containmentHull = containmentHulls.selectAll("path")
        .data(containmentClusters, (c: ContainmentCluster) => c.parent.id)

      post.enter().append("div")
        .style("background-color", "#EEEEEE")
        .style("border", "1px solid #DDDDDD")
        .style("max-width", "100px")
        .style("position", "absolute")
        .text((post: Post) => post.title)

      post.exit().remove()

      respondsTo.enter().append("line")
        .style("stroke", "#8F8F8F")
      respondsTo.exit().remove()

      contains.enter().append("line")
        .style("stroke", "blue")
      contains.exit().remove()

      containmentHull.enter().append("path")
        .style("stroke", "#0075B8")
        .style("fill", "#00C1FF")
      containmentHull.exit().remove()

      // write rendered dimensions into posts
      // helps for centering
      postElements.selectAll("div").each({ (node: raw.HTMLElement, p: Post) =>
        val rect = node.getBoundingClientRect
        p.size = Vec2(rect.width, rect.height)
        p.centerOffset = p.size / -2
      }: js.ThisFunction)

      simulation.force("respondsTo").strength { (e: RespondsTo) =>
        import p.fullDegree
        val targetDeg = e.target match {
          case p: Post => fullDegree(p)
          case _: RespondsTo => 2
        }
        1.0 / min(fullDegree(e.source), targetDeg)
      }

      simulation.force("containment").strength { (e: Contains) =>
        import p.fullDegree
        1.0 / min(fullDegree(e.source), fullDegree(e.target))
      }

      simulation.nodes(postData)
      simulation.force("respondsTo").links(respondsToData)
      simulation.force("containment").links(containmentData)
      simulation.alpha(1).restart()
    }

    def draw(p: Props) {
      postElements.selectAll("div")
        .style("left", (d: Post) => s"${d.x.get + d.centerOffset.x}px")
        .style("top", (d: Post) => s"${d.y.get + d.centerOffset.y}px")

      respondsToElements.selectAll("line")
        .attr("x1", (d: RespondsTo) => d.source.x)
        .attr("y1", (d: RespondsTo) => d.source.y)
        .attr("x2", (d: RespondsTo) => d.target.x)
        .attr("y2", (d: RespondsTo) => d.target.y)

      containmentElements.selectAll("line")
        .attr("x1", (d: Contains) => d.source.x)
        .attr("y1", (d: Contains) => d.source.y)
        .attr("x2", (d: Contains) => d.target.x)
        .attr("y2", (d: Contains) => d.target.y)

      containmentHulls.selectAll("path")
        .attr("d", (cluster: ContainmentCluster) => cluster.convexHull.map(v => s"${v.x} ${v.y}").mkString("M", "L", "Z"))
    }
  }

  val backendFactory = new Backend(_)
}
