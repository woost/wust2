 package wust.webApp.views.graphview

 import d3v4.SimulationNode
 import d3v4._
 import org.scalajs.dom.raw.HTMLElement
 import vectory._

 import scala.scalajs.js

 trait ExtendedD3Node extends SimulationNode {
   def pos = for (x <- x; y <- y) yield Vec2(x, y)
   def pos_=(newPos: js.UndefOr[Vec2]): Unit = {
     if (newPos.isDefined) {
       x = newPos.get.x
       y = newPos.get.y
     } else {
       x = js.undefined
       y = js.undefined
     }
   }
   def vel = for (vx <- vx; vy <- vy) yield Vec2(vx, vy)
   def vel_=(newVel: js.UndefOr[Vec2]): Unit = {
     if (newVel.isDefined) {
       vx = newVel.get.x
       vy = newVel.get.y
     } else {
       vx = js.undefined
       vy = js.undefined
     }
   }
   def fixedPos = for (fx <- fx; fy <- fy) yield Vec2(fx, fy)
   def fixedPos_=(newFixedPos: js.UndefOr[Vec2]): Unit = {
     if (newFixedPos.isDefined) {
       fx = newFixedPos.get.x
       fy = newFixedPos.get.y
     } else {
       fx = js.undefined
       fy = js.undefined
     }
   }

   def recalculateSize(node: HTMLElement, scale: Double): Unit = {
     val rect = node.getBoundingClientRect
     size = Vec2(rect.width, rect.height) / scale
     radius = size.length / 2
     centerOffset = size / -2
     collisionRadius = radius + Constants.nodePadding * 0.5
     containmentRadius = collisionRadius
   }

   var size: Vec2 = Vec2(0, 0)
   // def rect = pos.map { pos => AARect(pos, size) }
   var centerOffset: Vec2 = Vec2(0, 0)
   var radius: Double = 0
   var collisionRadius: Double = 0
   def collisionArea:Double = Math.PI * collisionRadius * collisionRadius
   def collisionBoundingSquareArea:Double = 4 * collisionRadius * collisionRadius
   var containmentRadius:Double = 0
   def containmentArea:Double = Math.PI * containmentRadius * containmentRadius
   def containmentBoundingSquareArea:Double = Math.PI * containmentRadius * containmentRadius

   var dragStart = Vec2(0, 0)
 }
