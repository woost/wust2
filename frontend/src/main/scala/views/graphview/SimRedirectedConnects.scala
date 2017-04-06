package wust.frontend.views.graphview

import org.scalajs.d3v4._
import scalajs.js

import wust.graph.LocalConnection

class SimRedirectedConnects(val connects: LocalConnection, val source: SimPost, val target: SimPost)
  extends SimulationLinkImpl[SimPost, SimPost] {
  //TODO: delegert!
  def sourceId = connects.sourceId
  def targetId = connects.targetId
}
