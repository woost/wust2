package wust.frontend.views.graphview

import org.scalajs.d3v4._
import wust.graph.{ConnectableId, Post}
import wust.util.Pipe

import scala.math._

trait SimConnectable {
  def id: ConnectableId
}

class SimPost(val post: Post) extends SimConnectable with ExtendedD3Node with SimulationNodeImpl {
  //TODO: delegert!
  def id = post.id
  def title = post.title

  var color = "red"
  var border = "none"
  var opacity = 1.0

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
    new SimPost(post) ||> { g =>
      g.x = x
      g.y = y
      g.size = size
      g.centerOffset = centerOffset

      g.color = color
      g.border = border
    }
  }
  var draggingPost: Option[SimPost] = None

  //TODO: derive
  override def toString = s"SimPost($post, $title, $x, $y)"
}
