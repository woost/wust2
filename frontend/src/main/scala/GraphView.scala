package frontend

import scalajs.js
import js.JSConverters._
import scala.scalajs.js.annotation._
import org.scalajs.dom._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import graph._
import collection.breakOut

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

    val simulation = d3.forceSimulation()
      .force("center", d3.forceCenter())
      .force("gravityx", d3.forceX())
      .force("gravityy", d3.forceY())
      .force("repel", d3.forceManyBody())
      .force("link", d3.forceLink())
      .force("respondsToPositions", setRespondsToPositions _)
    // .force("collision", d3.forceCollide())

    def setRespondsToPositions() {
      for (respondsTo <- respondsToData) {
        respondsTo.x = (respondsTo.source.x.asInstanceOf[Double] + respondsTo.target.x.asInstanceOf[Double]) / 2
        respondsTo.y = (respondsTo.source.y.asInstanceOf[Double] + respondsTo.target.y.asInstanceOf[Double]) / 2
      }
    }

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

      simulation.force("link").distance(100)
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
        e.target = posts.getOrElse(e.out, respondsTos(e.out))
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
      simulation.force("link").links(respondsToData)
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
