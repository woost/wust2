 package wust.frontend.views.graphview

 import org.scalajs.d3v4._
 import wust.graph.LocalConnection
 import wust.ids.{Label, PostId}

 class SimRedirectedConnection(val connection: LocalConnection, val source: SimPost, val target: SimPost)
   extends SimulationLinkImpl[SimPost, SimPost] {
  @inline def sourceId: PostId = connection.sourceId
  @inline def label: Label = connection.label
  @inline def targetId: PostId = connection.targetId
 }
