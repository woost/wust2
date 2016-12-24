package frontend

import scalajs.js
import js.JSConverters._
import scala.scalajs.js.annotation._
import org.scalajs.dom._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import api.graph._
import pharg._

object GraphView extends CustomComponent[Graph]("GraphView") {
  import js.Dynamic.global
  val d3 = global.d3

  val width = 640
  val height = 480

  class Backend($: Scope) extends CustomBackend($) {
    lazy val svg = d3.select(component).append("svg")
    lazy val vertices = svg.append("g")
    lazy val edges = svg.append("g")

    val simulation = d3.forceSimulation()
      .force("center", d3.forceCenter())
      .force("gravityx", d3.forceX())
      .force("gravityy", d3.forceY())
      .force("repel", d3.forceManyBody())
      .force("link", d3.forceLink())
    // .force("collision", d3.forceCollide())

    simulation.on("tick", (e: Event) => {
      draw($.props.runNow())
    })

    def nodes = simulation.nodes().asInstanceOf[js.Array[Id]]
    def links = simulation.force("link").links().asInstanceOf[js.Array[Edge[Id]]]

    override def init(p: Props) = Callback {
      // init lazy vals
      svg
      edges
      vertices

      svg
        .attr("width", width)
        .attr("height", height)
        .style("border", "1x solid #DDDDDD")

      simulation.force("center").x(width / 2).y(height / 2)
      simulation.force("gravityx").x(width / 2)
      simulation.force("gravityy").y(height / 2)
    }

    @ScalaJSDefined
    class D3Vertex(
      var x: js.UndefOr[Double],
      var y: js.UndefOr[Double]
    ) extends js.Object

    @ScalaJSDefined
    class D3Edge(
      val source: D3Vertex,
      val target: D3Vertex
    ) extends js.Object

    override def update(p: Props, oldProps: Option[Props] = None) = Callback {
      val vertexSeq = p.vertices.toIndexedSeq
      val vertexData = vertexSeq.map(v => new D3Vertex(js.undefined, js.undefined)).toJSArray
      val circle = vertices.selectAll("circle")
        .data(vertexData)

      val edgeData = p.edges.map(e => new D3Edge(vertexData(vertexSeq.indexOf(e.in)), vertexData(vertexSeq.indexOf(e.out)))).toJSArray
      val edge = edges.selectAll("line")
        .data(edgeData)

      circle.enter()
        .append("circle")
        .attr("r", 5)
        .attr("fill", "#48D7FF")

      circle.exit()
        .remove()

      edge.enter()
        .append("line")
        .attr("stroke", "#8F8F8F")

      edge.exit()
        .remove()

      simulation.nodes(vertexData)
      simulation.force("link").links(edgeData)
      simulation.alpha(1).restart()
    }

    def draw(p: Props) {
      val vertex = vertices.selectAll("circle")
      val edge = edges.selectAll("line")

      vertex
        .attr("cx", (d: D3Vertex) => d.x)
        .attr("cy", (d: D3Vertex) => d.y)

      edge
        .attr("x1", (d: D3Edge) => d.source.x)
        .attr("y1", (d: D3Edge) => d.source.y)
        .attr("x2", (d: D3Edge) => d.target.x)
        .attr("y2", (d: D3Edge) => d.target.y)
    }
  }

  val backendFactory = new Backend(_)
}

abstract class CustomComponent[_Props](componentName: String = "CustomComponent") {
  type Props = _Props
  type Scope = BackendScope[Props, Unit]

  abstract class CustomBackend($: Scope) {
    def render(p: Props) = <.div(^.ref := "component")
    lazy val component = Ref[raw.HTMLElement]("component")($).get

    def init(p: Props) = Callback.empty
    def update(p: Props, oldProps: Option[Props] = None) = Callback.empty
    def cleanup() = Callback.empty
  }
  val backendFactory: Scope => CustomBackend

  protected val component = ReactComponentB[Props](componentName)
    .backend(backendFactory(_))
    .render(c => c.backend.render(c.props))
    .componentDidMount(c => c.backend.init(c.props) >> c.backend.update(c.props, None))
    .componentWillReceiveProps(c => c.$.backend.update(c.nextProps, Some(c.currentProps)))
    .shouldComponentUpdate(_ => false) // let our custom code handle the update instead
    .componentWillUnmount(c => c.backend.cleanup())
    .build

  def apply(p: Props) = component(p)
}
