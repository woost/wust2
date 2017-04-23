package wust.frontend.views.graphview

import org.scalajs.d3v4._

object RedirectedConnectionLineSelection extends DataSelection[SimRedirectedConnects] {
  override val tag = "line"
  override def enterAppend(line: Selection[SimRedirectedConnects]) {
    line
      .style("stroke", "#8F8F8F")
      .style("stroke-dasharray", "10 5")
  }

  override def draw(line: Selection[SimRedirectedConnects]) {
    line
      .attr("x1", (e: SimRedirectedConnects) => e.source.x)
      .attr("y1", (e: SimRedirectedConnects) => e.source.y)
      .attr("x2", (e: SimRedirectedConnects) => e.target.x)
      .attr("y2", (e: SimRedirectedConnects) => e.target.y)
  }
}
