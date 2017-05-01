package wust.frontend.views.graphview

import org.scalajs.d3v4._

class Forces {
  val gravityX = d3.forceX[SimPost]()
  val gravityY = d3.forceY[SimPost]()
  val repel = d3.forceManyBody[SimPost]()
  val collision = d3.forceCollide[SimPost]() //TODO: rectangle collision detection?
  val connection = d3.forceLink[ExtendedD3Node, SimConnection]()
  val redirectedConnection = d3.forceLink[SimPost, SimRedirectedConnection]()
  // val connection = new CustomLinkForce[ExtendedD3Node, SimConnection]
  val containment = d3.forceLink[SimPost, SimContainment]()
  val collapsedContainment = d3.forceLink[SimPost, SimCollapsedContainment]()
  //TODO: push posts out of containment clusters they don't belong to
}
object Forces {
  def apply(height: Int, width: Int) = {
    val forces = new Forces

    forces.gravityX.x(width / 2)
    forces.gravityY.y(height / 2)

    forces.repel.strength(-1500)
    forces.collision.radius((p: SimPost) => p.collisionRadius)

    forces.connection.distance(130)
    forces.redirectedConnection.distance(150)
    forces.containment.distance(100)
    forces.collapsedContainment.distance(100)
    forces.collapsedContainment.strength(0.01)

    forces.gravityX.strength(0.1)
    forces.gravityY.strength(0.1)

    forces
  }
}

object Simulation {
  def apply(forces: Forces): Simulation[SimPost] = d3.forceSimulation[SimPost]()
    .force("gravityx", forces.gravityX)
    .force("gravityy", forces.gravityY)
    .force("repel", forces.repel)
    .force("collision", forces.collision)
    .force("connection", forces.connection.asInstanceOf[Link[SimPost, SimulationLink[SimPost, SimPost]]])
    .force("redirectedConnection", forces.redirectedConnection)
    // .force("connection", forces.connection.forJavaScriptIdiots().asInstanceOf[Force[SimPost]])
    .force("containment", forces.containment)
    .force("collapsedContainment", forces.collapsedContainment)
}

// TODO: run simulation in tests. jsdom timer bug?
// When running tests with d3-force in jsdom, the d3-timer does not stop itself.
// It should stop when alpha < alphaMin, but is running infinitely, causing a jsdom timeout.
class D3State(disableSimulation: Boolean = false) {
  //TODO: dynamic by screen size, refresh on window resize, put into centering force
  private val width = 640
  private val height = 480

  var transform: Transform = d3.zoomIdentity // stores current pan and zoom
  val forces = Forces(height, width)
  val simulation = Simulation(forces)
  if (disableSimulation) simulation.stop()
}
