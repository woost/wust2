package wust.frontend.views.graphview

import org.scalajs.d3v4._
import wust.graph.LocalConnection

class SimRedirectedConnection(val connection: LocalConnection, val source: SimPost, val target: SimPost)
  extends SimulationLinkImpl[SimPost, SimPost] {
  //TODO: delegert!
  def sourceId = connection.sourceId
  def targetId = connection.targetId
}
