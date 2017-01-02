package frontend

import scalajs.js
import js.JSConverters._
import scala.scalajs.js.annotation._
import org.scalajs.dom._
import raw.HTMLElement

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import vectory._

import graph._
import collection.breakOut
import math._

case class ContainmentCluster(parent: Post, children: IndexedSeq[Post]) {
  def positions: js.Array[js.Array[Double]] = (children :+ parent).map(post => js.Array(post.x.asInstanceOf[Double], post.y.asInstanceOf[Double]))(breakOut)
  def convexHull = d3.polygonHull(positions)
}

object GraphView extends CustomComponent[Graph]("GraphView") {
  import js.Dynamic.global
  val d3js = global.d3

  val width = 640
  val height = 480

  class Backend($: Scope) extends CustomBackend($) {
    lazy val container = d3js.select(component)
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

    val simulation = d3js.forceSimulation()
      .force("center", d3js.forceCenter())
      .force("gravityx", d3js.forceX())
      .force("gravityy", d3js.forceY())
      .force("repel", d3js.forceManyBody())
      .force("collision", d3js.forceCollide())
      .force("respondsTo", d3js.forceLink())
      .force("containment", d3js.forceLink())

    simulation.on("tick", (e: Event) => {
      draw()
    })

    var transform: Transform = d3.zoomIdentity // stores current pan and zoom

    override def init() {
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

      svg.call(d3js.zoom().on("zoom", zoomed _))

      simulation.force("center").x(width / 2).y(height / 2)
      simulation.force("gravityx").x(width / 2)
      simulation.force("gravityy").y(height / 2)

      simulation.force("repel").strength(-1000)
      simulation.force("collision").radius((p: Post) => p.radius)

      simulation.force("respondsTo").distance(100)
      simulation.force("containment").distance(100)

      simulation.force("gravityx").strength(0.1)
      simulation.force("gravityy").strength(0.1)
    }

    def zoomed() {
      transform = d3.event.transform
      svg.selectAll("g").attr("transform", transform)
      html.style("transform", "translate(" + transform.x + "px," + transform.y + "px) scale(" + transform.k + ")")
    }

    def postDragStarted(node: HTMLElement, p: Post) {
      val eventPos = Vec2(d3.event.x, d3.event.y)
      p.dragStart = eventPos
      p.fixedPos = eventPos

      d3js.select(node).style("cursor", "move")
      simulation.alphaTarget(0.7).restart()
    }

    def postDragged(node: HTMLElement, p: Post) {
      val eventPos = Vec2(d3.event.x, d3.event.y)
      p.fixedPos = p.dragStart + (eventPos - p.dragStart) / transform.k
    }

    def postDragEnded(node: HTMLElement, p: Post) {
      d3js.select(node).style("cursor", "default")
      simulation.alphaTarget(0)
    }

    override def update(p: Props, oldProps: Option[Props] = None) {
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
        .text((post: Post) => post.title)
        .style("background-color", "#EEEEEE")
        .style("border", "1px solid #DDDDDD")
        .style("max-width", "100px")
        .style("position", "absolute")
        .style("cursor", "default")
        .style("pointer-events", "auto") // reenable
        .call(d3js.drag()
          .on("start", postDragStarted _: js.ThisFunction)
          .on("drag", postDragged _: js.ThisFunction)
          .on("end", postDragEnded _: js.ThisFunction))
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

      // write rendering data into posts
      // helps for centering and collision
      postElements.selectAll("div").each({ (node: HTMLElement, p: Post) =>
        val rect = node.getBoundingClientRect
        p.size = Vec2(rect.width, rect.height)
        p.centerOffset = p.size / -2
        p.radius = p.size.length / 2
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

    def draw() {
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
        .attr("d", (cluster: ContainmentCluster) => cluster.convexHull.map(p => s"${p(0)} ${p(1)}").mkString("M", "L", "Z"))
    }
  }

  val backendFactory = new Backend(_)
}
