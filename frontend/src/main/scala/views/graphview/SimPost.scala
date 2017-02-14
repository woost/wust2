package frontend.views.graphview

import graph.Post
import math._
import org.scalajs.d3v4._

class SimPost(val post: Post) extends ExtendedD3Node with SimulationNodeImpl {
  //TODO: delegert!
  def id = post.id
  def title = post.title

  var color = "red"
  var border = "none"

  var dragClosest: Option[SimPost] = None
  var isClosest = false
  var dropAngle = 0.0
  def dropIndex(n: Int) = {
    val positiveAngle = (dropAngle + 2.5 * Pi) % (2 * Pi) // 0 degree is at 12 o'clock, proceeding clockwise (conform to d3.pie())
    val stepSize = 2 * Pi / n
    val index = (positiveAngle / stepSize).toInt
    index
  }

  def newDraggingPost = {
    val g = new SimPost(post)
    g.x = x
    g.y = y
    g.size = size
    g.centerOffset = centerOffset
    g.color = color
    g
  }
  var draggingPost: Option[SimPost] = None

  //TODO: derive
  override def toString = s"SimPost($post, $title, $x, $y)"
}
