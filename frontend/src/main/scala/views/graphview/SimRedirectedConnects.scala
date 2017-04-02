package wust.frontend.views.graphview

import org.scalajs.d3v4._
import scalajs.js

import wust.frontend.LocalConnection

class SimRedirectedConnects(val connects: LocalConnection, val source: SimPost, val target: SimPost)
  extends SimulationLink[SimPost, ExtendedD3Node] with SimulationLinkImpl[SimPost, ExtendedD3Node] {
  //TODO: delegert!
  def sourceId = connects.sourceId
  def targetId = connects.targetId
}
