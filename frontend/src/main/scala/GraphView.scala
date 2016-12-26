package frontend

import scalajs.js
import js.JSConverters._
import scala.scalajs.js.annotation._
import org.scalajs.dom._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import graph._
import collection.breakOut
import math._

class CustomLinkForce[N <: graph.D3SimulationNode, L <: graph.D3SimulationLink] {
  // ported from
  // https://github.com/d3/d3-force/blob/master/src/link.js

  implicit def undefOrToRaw[T](undefOr: js.UndefOr[T]): T = undefOr.get

  private var _links: js.Array[L] = js.Array[L]()
  def links = _links
  def links_=(newLinks: js.Array[L]) { _links = newLinks; initialize(nodes) }
  def id(node: N) = node.index
  // TODO: call initializeStrength/Distance when setting? Or not necessary because only settable by overriding?
  def strength(link: L, i: Int, links: js.Array[L]) = defaultStrength(link)
  private var strengths: js.Array[Double] = js.Array[Double]()
  def distance(link: L, i: Int, links: js.Array[L]) = 30
  private var distances: js.Array[Double] = js.Array[Double]()
  private var nodes: js.Array[N] = js.Array[N]()
  private var count: js.Array[Int] = js.Array[Int]()
  private var bias: js.Array[Double] = js.Array[Double]()
  var iterations = 1

  def defaultStrength(link: L) = {
    1.0 / min(count(link.source.index), count(link.target.index));
  }

  private def force(alpha: Double) {
    println(s"force: nodes(${nodes.size}), links(${links.size})")
    var k = 0
    var i = 0
    val n = links.size
    while (k < iterations) {
      i = 0
      while (i < n) {
        val link = links(i)
        val source = link.source
        val target = link.target

        def jiggle() = scala.util.Random.nextDouble
        console.log(source.x, source.vx)
        console.log(target.x, target.vx)
        var x: Double = (target.x.asInstanceOf[js.Dynamic] + target.vx.asInstanceOf[js.Dynamic] - source.x.asInstanceOf[js.Dynamic] - source.vx.asInstanceOf[js.Dynamic]).asInstanceOf[js.UndefOr[Double]].getOrElse(jiggle())
        var y: Double = (target.y.asInstanceOf[js.Dynamic] + target.vy.asInstanceOf[js.Dynamic] - source.y.asInstanceOf[js.Dynamic] - source.vy.asInstanceOf[js.Dynamic]).asInstanceOf[js.UndefOr[Double]].getOrElse(jiggle())
        console.log(x, y)
        var l = sqrt(x * x + y * y)
        console.log(s"l: $l")
        console.log(s"distances(i): ${distances(i)}")
        console.log(distances)
        l = (l - distances(i)) / l * alpha * strengths(i)
        console.log(s"l: $l")
        x *= l
        y *= l
        var b = bias(i)
        console.log(s"b: $b")
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

  private def initialize(newNodes: js.Array[N]) {
    nodes = newNodes
    println(s"initialize:  nodes(${nodes.size}), links(${links.size})")
    if (nodes.isEmpty) return ;

    var i = 0
    val n = nodes.size
    val m = links.size
    val nodeById: Map[Int, N] = nodes.map(n => id(n).get -> n).toMap //(breakOut)

    i = 0
    count = Array.fill(n)(0).toJSArray
    while (i < m) {
      val link = links(i)
      link.index = i;
      if (link.source == null) link.source = nodeById(link.source.asInstanceOf[Int])
      if (link.target == null) link.target = nodeById(link.target.asInstanceOf[Int])
      count(link.source.index) += 1
      count(link.target.index) += 1
      i += 1
    }

    i = 0
    bias = new js.Array[Double](m)
    while (i < m) {
      val link = links(i)
      bias(i) = count(link.source.index).toDouble / (count(link.source.index) + count(link.target.index))
      i += 1
    }

    strengths = new js.Array[Double](m)
    initializeStrength()
    distances = new js.Array[Double](m)
    console.log(s"after array init $m")
    println(distances.toSeq)
    initializeDistance()
    console.log(distances)
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
    console.log(distances)
  }

  type F = js.Function1[Double, Unit]
  def apply(): F = {
    val f: (F) = force _
    f.asInstanceOf[js.Dynamic].initialize = initialize _
    f
  }
}

object GraphView extends CustomComponent[Graph]("GraphView") {
  import js.Dynamic.global
  val d3 = global.d3

  val width = 640
  val height = 480

  class Backend($: Scope) extends CustomBackend($) {
    lazy val svg = d3.select(component).append("svg")
    lazy val postElements = d3.select(component).append("div")
    lazy val respondsToElements = svg.append("g")

    var postData: js.Array[Post] = js.Array()
    var respondsToData: js.Array[RespondsTo] = js.Array()

    val customLinkForce = new CustomLinkForce[Post, RespondsTo]
    val simulation = d3.forceSimulation()
      .force("center", d3.forceCenter())
      .force("gravityx", d3.forceX())
      .force("gravityy", d3.forceY())
      .force("repel", d3.forceManyBody())
      .force("link", customLinkForce())
    // .force("respondsToPositions", setRespondsToPositions _)
    // .force("collision", d3.forceCollide())

    // def setRespondsToPositions() {
    // implicit def undefOrToRaw[T](undefOr: js.UndefOr[T]): T = undefOr.get
    //   def setPosition(r: RespondsTo) {
    //     r.target match {
    //       case target: RespondsTo => setPosition(target)
    //       case _ =>
    //     }
    //     println(r.target.x, r.target.y)
    //
    //     r.x = (r.source.x + r.target.x) / 2
    //     r.y = (r.source.y + r.target.y) / 2
    //   }

    //   for (respondsTo <- respondsToData) {
    //     setPosition(respondsTo)
    //   }
    // }

    simulation.on("tick", (e: Event) => {
      draw($.props.runNow())
    })

    override def init(p: Props) = Callback {
      // init lazy vals
      svg
      respondsToElements
      postElements

      svg
        .attr("width", width)
        .attr("height", height)
        .style("position", "absolute")
        .style("border", "1px solid #DDDDDD")

      postElements
        .style("position", "absolute")
        .style("width", s"${width}px")
        .style("height", s"${height}px")

      simulation.force("center").x(width / 2).y(height / 2)
      simulation.force("gravityx").x(width / 2)
      simulation.force("gravityy").y(height / 2)

      // simulation.force("link").distance(100)
      simulation.force("repel").strength(-1000)
    }

    override def update(p: Props, oldProps: Option[Props] = None) = Callback {
      import p.posts
      import p.respondsTos

      postData = p.posts.values.toJSArray
      val post = postElements.selectAll("div")
        .data(postData)

      respondsToData = p.respondsTos.values.map { e =>
        e.source = posts(e.in)
        e.target = posts(e.out) //.getOrElse(e.out, respondsTos(e.out))
        e
      }.toJSArray
      val respondsTo = respondsToElements.selectAll("line")
        .data(respondsToData)

      post.enter()
        .append("div")
        .style("background-color", "#EEEEEE")
        .style("border", "1px solid #DDDDDD")
        .style("max-width", "100px")
        .style("position", "absolute")
        .text((post: Post) => post.title)

      post.exit()
        .remove()

      respondsTo.enter()
        .append("line")
        .attr("stroke", "#8F8F8F")

      respondsTo.exit()
        .remove()

      simulation.nodes(postData)
      customLinkForce.links = respondsToData
      simulation.alpha(1).restart()
    }

    def draw(p: Props) {
      import p.posts

      val post = postElements.selectAll("div")
      val respondsTo = respondsToElements.selectAll("line")

      post
        .style("left", (d: Post) => s"${d.x}px")
        .style("top", (d: Post) => s"${d.y}px")

      respondsTo
        .attr("x1", (d: RespondsTo) => d.source.x)
        .attr("y1", (d: RespondsTo) => d.source.y)
        .attr("x2", (d: RespondsTo) => d.target.x)
        .attr("y2", (d: RespondsTo) => d.target.y)
    }
  }

  val backendFactory = new Backend(_)
}
