package frontend.views.graphview

import org.scalajs.d3v4._

import frontend._

class Forces {
  val center = d3.forceCenter[SimPost]()
  val gravityX = d3.forceX[SimPost]()
  val gravityY = d3.forceY[SimPost]()
  val repel = d3.forceManyBody[SimPost]()
  val collision = d3.forceCollide[SimPost]() //TODO: rectangle collision detection?
  // val connection = d3.forceLink[ExtendedD3Node, SimConnects]()
  val connection = new CustomLinkForce[ExtendedD3Node, SimConnects]
  val containment = d3.forceLink[SimPost, SimContains]()
  //TODO: push posts out of containment clusters they don't belong to
}
object Forces {
  def apply(height: Int, width: Int) = {
    val forces = new Forces

    forces.center.x(width / 2).y(height / 2)
    forces.gravityX.x(width / 2)
    forces.gravityY.y(height / 2)

    forces.repel.strength(-1000)
    forces.collision.radius((p: SimPost) => p.collisionRadius)

    // forces.connection.distance(100)
    forces.containment.distance(100)

    forces.gravityX.strength(0.1)
    forces.gravityY.strength(0.1)

    forces
  }
}

object Simulation {
  def apply(forces: Forces): Simulation[SimPost] = d3.forceSimulation[SimPost]()
    .force("center", forces.center)
    .force("gravityx", forces.gravityX)
    .force("gravityy", forces.gravityY)
    .force("repel", forces.repel)
    .force("collision", forces.collision)
    // .force("connection", forces.connection.asInstanceOf[Link[SimPost, SimulationLink[SimPost, SimPost]]])
    .force("connection", forces.connection.forJavaScriptIdiots().asInstanceOf[Force[SimPost]])
    .force("containment", forces.containment)
}

class D3State {
  //TODO: dynamic by screen size, refresh on window resize, put into centering force
  private val width = 640
  private val height = 480

  var transform: Transform = d3.zoomIdentity // stores current pan and zoom
  val forces = Forces(height, width)
  val simulation = Simulation(forces)
}
