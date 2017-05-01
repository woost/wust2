package wust.frontend.views.graphview

import org.scalajs.d3v4._
import wust.graph.{LocalContainment, _}

class SimContainment(val containment: Containment, val parent: SimPost, val child: SimPost) extends SimulationLinkImpl[SimPost, SimPost] {
  //TODO: delegert!
  def id = containment.id
  def parentId = containment.parentId
  def childId = containment.childId

  def source = parent
  def target = child
}

class SimCollapsedContainment(val containment: LocalContainment, val parent: SimPost, val child: SimPost) extends SimulationLinkImpl[SimPost, SimPost] {
  //TODO: delegert!
  def parentId = containment.parentId
  def childId = containment.childId

  def source = parent
  def target = child
}
