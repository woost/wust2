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
