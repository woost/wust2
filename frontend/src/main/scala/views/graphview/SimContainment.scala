 package wust.frontend.views.graphview

 import org.scalajs.d3v4._
 import wust.graph.{LocalConnection, _}
 import wust.ids.{Label, PostId}

 class SimContainment(val connection: Connection, val parent: SimPost, val child: SimPost) extends SimulationLinkImpl[SimPost, SimPost] {
   @inline def sourceId: PostId = connection.sourceId
   @inline def label: Label = connection.label
   @inline def targetId: PostId = connection.targetId

   @inline def source: SimPost = parent
   @inline def target: SimPost = child
 }

 class SimCollapsedContainment(val connection: LocalConnection, val parent: SimPost, val child: SimPost) extends SimulationLinkImpl[SimPost, SimPost] {
   @inline def sourceId: PostId = connection.sourceId
   @inline def label: Label = connection.label
   @inline def targetId: PostId = connection.targetId

   @inline def source: SimPost = parent
   @inline def target: SimPost = child
 }
