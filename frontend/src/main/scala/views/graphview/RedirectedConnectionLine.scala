// package wust.frontend.views.graphview

// import org.scalajs.d3v4._

// object RedirectedConnectionLineSelection extends DataSelection[SimRedirectedConnection] {
//   override val tag = "line"
//   override def enterAppend(line: Selection[SimRedirectedConnection]) {
//     line
//       .style("stroke", "#8F8F8F")
//       .style("stroke-dasharray", "10 5")
//   }

//   override def draw(line: Selection[SimRedirectedConnection]) {
//     line
//       .attr("x1", (e: SimRedirectedConnection) => e.source.x)
//       .attr("y1", (e: SimRedirectedConnection) => e.source.y)
//       .attr("x2", (e: SimRedirectedConnection) => e.target.x)
//       .attr("y2", (e: SimRedirectedConnection) => e.target.y)
//   }
// }
