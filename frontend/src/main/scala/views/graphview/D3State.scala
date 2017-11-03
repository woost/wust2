package wust.frontend.views.graphview

import org.scalajs.d3v4._
import org.scalajs.dom.window

import scala.scalajs.js

class Forces {
  val gravityX = d3.forceX[SimPost]()
  val gravityY = d3.forceY[SimPost]()
  val repel = d3.forceManyBody[SimPost]()
  val collision = d3.forceCollide[SimPost]() //TODO: rectangle collision detection?
  val connection = d3.forceLink[SimPost, SimConnection]()
  val redirectedConnection = d3.forceLink[SimPost, SimRedirectedConnection]()
  val containment = d3.forceLink[SimPost, SimContainment]()
  val collapsedContainment = d3.forceLink[SimPost, SimCollapsedContainment]()
  //TODO: push posts out of containment clusters they don't belong to
}
object Forces {
  def apply(height: Int, width: Int) = {
    val forces = new Forces

    forces.gravityX.x(width / 2)
    forces.gravityY.y(height / 2)

    forces.repel.strength(-2200)
    forces.collision.radius((p: SimPost) => p.collisionRadius min 150) //TODO: global constant
    forces.collision.strength(0.1)

    forces.connection.distance(200)
    forces.connection.strength(0.3)
    forces.redirectedConnection.distance(200)
    forces.redirectedConnection.strength(0.2)

    forces.containment.distance(100)
    forces.containment.strength(0.1)
    forces.collapsedContainment.distance(400)
    forces.collapsedContainment.strength(0.3)

    forces.gravityX.strength(0.05)
    forces.gravityY.strength(0.05)

    forces
  }
}

object Simulation {
  def apply(forces: Forces): Simulation[SimPost] = d3.forceSimulation[SimPost]()
    .alphaMin(0.05) // stop simulation earlier (default = 0.001)
    .force("gravityx", forces.gravityX)
    .force("gravityy", forces.gravityY)
    .force("repel", forces.repel)
    .force("collision", forces.collision)
    .force("connection", forces.connection)
    .force("redirectedConnection", forces.redirectedConnection)
    .force("containment", forces.containment)
    .force("collapsedContainment", forces.collapsedContainment)
}

// TODO: run simulation in tests. jsdom timer bug?
// When running tests with d3-force in jsdom, the d3-timer does not stop itself.
// It should stop when alpha < alphaMin, but is running infinitely, causing a jsdom timeout.
class D3State(disableSimulation: Boolean = false) {
  //TODO: dynamic by screen size, refresh on window resize, put into centering force
  private val width = window.innerWidth.toInt
  private val height = window.innerHeight.toInt

  val zoom = d3.zoom().on("zoom.settransform", () => zoomed()).scaleExtent(js.Array(0.1, 10))
  //TODO why does this not work on 2.12, works on 2.11. maybe scalajs function implicit?
  // private def zoomed() { _transform = d3.event.asInstanceOf[ZoomEvent].transform }
  private def zoomed() = { _transform = d3.event.asInstanceOf[ZoomEvent].transform }
  private var _transform: Transform = d3.zoomIdentity // stores current pan and zoom
  def transform = _transform

  val forces = Forces(height, width)
  val simulation = Simulation(forces)
  if (disableSimulation) simulation.stop()
}
