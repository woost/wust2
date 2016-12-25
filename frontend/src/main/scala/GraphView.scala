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

    def nodes = simulation.nodes().asInstanceOf[js.Array[Post]]
    def links = simulation.force("link").links().asInstanceOf[js.Array[RespondsTo]]

    override def init(p: Props) = Callback {
      // init lazy vals
      svg
      respondsToElements
      postElements

      svg
        .attr("width", width)
        .attr("height", height)
        .style("position", "absolute")
        .style("border", "1x solid #DDDDDD")

      postElements
        .style("position", "absolute")
        .style("width", s"${width}px")
        .style("height", s"${height}px")

      simulation.force("center").x(width / 2).y(height / 2)
      simulation.force("gravityx").x(width / 2)
      simulation.force("gravityy").y(height / 2)
    }

    override def update(p: Props, oldProps: Option[Props] = None) = Callback {
      import p.posts

      val postData: js.Array[Post] = p.posts.values.toJSArray
      val post = postElements.selectAll("div")
        .data(postData)

      val respondsToData: js.Array[RespondsTo] = p.respondsTos.values.map { e =>
        //TODO: have these as lazy vals / defs in graph?
        e.source = posts(e.in)
        e.target = posts(e.out)
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
