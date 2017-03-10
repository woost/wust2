package wust.frontend.views.graphview

import math._
import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.d3v4._
import org.scalajs.dom
import rx._
import vectory._

import wust.frontend._
import wust.graph._
import wust.util.collection._

object ConnectionLineSelection extends DataSelection[SimConnects] {
  override val tag = "line"
  override def enterAppend(line: Selection[SimConnects]) {
    line
      .style("stroke", "#8F8F8F")
  }

  override def draw(line: Selection[SimConnects]) {
    line
      .attr("x1", (e: SimConnects) => e.source.x)
      .attr("y1", (e: SimConnects) => e.source.y)
      .attr("x2", (e: SimConnects) => e.target.x)
      .attr("y2", (e: SimConnects) => e.target.y)
  }
}

object ConnectionElementSelection extends DataSelection[SimConnects] {
  override val tag = "div"
  override def enterAppend(element: Selection[SimConnects]) {
    element
      .attr("title", (e: SimConnects) => e.id)
      .style("position", "absolute")
      .style("font-size", "20px")
      .style("margin-left", "-0.5ex")
      .style("margin-top", "-0.5em")
      .text("\u00d7")
      .style("pointer-events", "auto") // parent has pointer-events disabled, enable explicitly for the x button.
      .style("cursor", "pointer")
      .on("click", { (e: SimConnects) =>
        import autowire._
        import boopickle.Default._

        println(s"\nDelete Connection: ${e.id}")
        Client.api.deleteConnection(e.id).call()
      })
  }

  override def draw(element: Selection[SimConnects]) {
    element
      .style("left", (e: SimConnects) => s"${e.x.get}px")
      .style("top", (e: SimConnects) => s"${e.y.get}px")
  }
}
