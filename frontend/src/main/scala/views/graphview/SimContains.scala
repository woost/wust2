package wust.frontend.views.graphview

import math._
import scalajs.js
import org.scalajs.d3v4._
import vectory._

import wust.graph._

class SimContains(val contains: Contains, val parent: SimPost, val child: SimPost) extends SimulationLinkImpl[SimPost, SimPost] {
  //TODO: delegert!
  def id = contains.id
  def parentId = contains.parentId
  def childId = contains.childId

  def source = parent
  def target = child
}
