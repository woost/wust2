 package wust.webApp.views.graphview

 import d3v4._
 import wust.graph.Connection
 import wust.ids.{Label, PostId}

 class SimConnection(val connection: Connection, val source: SimPost, val target: SimPost)
   extends SimulationLink[SimPost, SimPost] with SimulationLinkImpl[SimPost, SimPost] { //TODO: why both simulationLinkImpl and SimulationLink?
   @inline def sourceId: PostId = connection.sourceId
   @inline def label: Label = connection.label
   @inline def targetId: PostId = connection.targetId
 }
