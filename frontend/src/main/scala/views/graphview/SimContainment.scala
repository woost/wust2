 package wust.frontend.views.graphview

 import delegert.delegert
 import org.scalajs.d3v4._
 import wust.graph.{LocalConnection, _}

 class SimContainment(@delegert(vals) val containment: Connection, val parent: SimPost, val child: SimPost) extends SimulationLinkImpl[SimPost, SimPost] {
   def source = parent
   def target = child
 }

 class SimCollapsedContainment(@delegert(vals) val containment: LocalConnection, val parent: SimPost, val child: SimPost) extends SimulationLinkImpl[SimPost, SimPost] {
   def source = parent
   def target = child
 }
