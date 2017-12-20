 package wust.frontend.views.graphview

 import delegert.delegert
 import org.scalajs.d3v4._
 import wust.graph.Connection

 class SimConnection(@delegert(vals) val connection: Connection, val source: SimPost, val target: SimPost)
   extends SimulationLink[SimPost, SimPost] with SimulationLinkImpl[SimPost, SimPost] { //TODO: why both simulationLinkImpl and SimulationLink?
 }
