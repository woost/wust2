package wust.webApp.views.graphview

import d3v4._
import flatland._
import org.scalajs.dom.CanvasRenderingContext2D
import vectory._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object DebugDraw {
  val fullCircle = 2 * Math.PI

  def draw(
    simData: SimulationData,
    staticData: StaticData,
    canvasContext: CanvasRenderingContext2D,
    planeDimension: PlaneDimension
  ): Unit = {

    val polyLine = d3.line().curve(d3.curveLinearClosed).context(canvasContext)

    // count in simData can be zero
    val eulerSetCount = simData.eulerSetCollisionPolygon.length
    val eulerZoneCount = simData.eulerZoneCollisionPolygon.length
    val eulerSetConnectedComponentCount = simData.eulerSetConnectedComponentCollisionPolygon.length

    val nodeCount = simData.n

    drawOrigin(canvasContext)
    drawPlaneDimension(canvasContext, planeDimension)


    // for every node
    canvasContext.lineWidth = 1
    loop (nodeCount) { i =>
      val x = simData.x(i)
      val y = simData.y(i)

      // radius
      canvasContext.strokeStyle = "rgba(0,0,0,1.0)"
      canvasContext.beginPath()
      canvasContext.arc(x, y, staticData.radius(i), startAngle = 0, endAngle = fullCircle)
      canvasContext.stroke()
      canvasContext.closePath()

      // collision radius
      canvasContext.strokeStyle = "rgba(0,0,0,0.3)"
      canvasContext.beginPath()
      canvasContext.arc(x, y, staticData.collisionRadius(i), startAngle = 0, endAngle = fullCircle)
      canvasContext.stroke()
      canvasContext.closePath()
    }

    // for every containment
    //    i = 0
    //    canvasContext.lineWidth = 20
    //    while (i < containmentCount) {
    //      val child = staticData.child(i)
    //      val parent = staticData.parent(i)
    //      val grad= canvasContext.createLinearGradient(simData.x(child), simData.y(child), simData.x(parent), simData.y(parent))
    //      grad.addColorStop(0, "rgba(255,255,255,0.1)") // child
    //      grad.addColorStop(1, "rgba(255,255,255,0.5)") // parent
    //      canvasContext.strokeStyle = grad
    //      canvasContext.beginPath()
    //      canvasContext.moveTo(simData.x(child), simData.y(child))
    //      canvasContext.lineTo(simData.x(parent), simData.y(parent))
    //      canvasContext.stroke()
    //      canvasContext.closePath()
    //      i += 1
    //    }

    //     for every containment cluster
    loop (eulerSetCount) { i =>
      canvasContext.strokeStyle = "rgba(128,128,128,0.6)"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      polyLine(simData.eulerSetCollisionPolygon(i).map(v => js.Tuple2(v.x, v.y)).toJSArray)
      canvasContext.stroke()

      // tangents
      // canvasContext.strokeStyle = "rgba(128,128,0,0.6)"
      // canvasContext.lineWidth = 2
      // simData.eulerSetConvexHullTangents(i).sliding(2,2).foreach { case Seq(a,b) =>
      //   canvasContext.beginPath()
      //   canvasContext.moveTo(a.x,a.y)
      //   canvasContext.lineTo(b.x,b.y)
      //   canvasContext.stroke()
      // }

      // eulerSet geometricCenter
      canvasContext.strokeStyle = "#000"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      canvasContext.arc(
        simData.eulerSetGeometricCenter(i).x,
        simData.eulerSetGeometricCenter(i).y,
        10,
        startAngle = 0,
        endAngle = fullCircle
      )
      canvasContext.stroke()
      canvasContext.closePath()

      // Axis aligned bounding box
      drawAARect(canvasContext, rect = simData.eulerSetCollisionPolygonAABB(i), color = "rgba(0,0,0,0.1)", lineWidth = 3)
    }

    loop (eulerSetConnectedComponentCount) { i =>
      canvasContext.strokeStyle = "rgba(0,0,255,0.6)"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      polyLine(simData.eulerSetConnectedComponentCollisionPolygon(i).map(v => js.Tuple2(v.x, v.y)).toJSArray)
      canvasContext.stroke()

      // Axis aligned bounding box
      drawAARect(canvasContext, rect = simData.eulerSetConnectedComponentCollisionPolygonAABB(i), color = "rgba(0,0,255,0.1)", lineWidth = 3)
    }

    loop (eulerZoneCount) { i =>
      canvasContext.fillStyle = "rgba(255,255,255,0.4)"
      canvasContext.lineWidth = 1
      canvasContext.beginPath()
      polyLine(simData.eulerZoneCollisionPolygon(i).map(v => js.Tuple2(v.x, v.y)).toJSArray)
      canvasContext.fill()

      // tangents
      // canvasContext.strokeStyle = "rgba(128,128,0,0.6)"
      // canvasContext.lineWidth = 2
      // simData.eulerZoneConvexHullTangents(i).sliding(2,2).foreach { case Seq(a,b) =>
      //   canvasContext.beginPath()
      //   canvasContext.moveTo(a.x,a.y)
      //   canvasContext.lineTo(b.x,b.y)
      //   canvasContext.stroke()
      // }

      // eulerZone geometricCenter
      // canvasContext.strokeStyle = "#000"
      // canvasContext.lineWidth = 3
      // canvasContext.beginPath()
      // canvasContext.arc(
      //   simData.eulerZoneGeometricCenter(i).x,
      //   simData.eulerZoneGeometricCenter(i).y,
      //   10,
      //   startAngle = 0,
      //   endAngle = fullCircle
      // )
      // canvasContext.stroke()
      // canvasContext.closePath()

      // Axis aligned bounding box
      drawAARect(canvasContext, rect = simData.eulerZoneCollisionPolygonAABB(i), color = "rgba(0,0,0,0.1)", lineWidth = 1)
    }

  }

  def drawOrigin(context: CanvasRenderingContext2D): Unit = {
    context.strokeStyle = "#000"
    context.lineWidth = 1
    context.beginPath()

    context.moveTo(0,-10)
    context.lineTo(0,10)

    context.moveTo(-10,0)
    context.lineTo(10,0)

    context.stroke()
    context.closePath()
  }

  def drawPlaneDimension(context: CanvasRenderingContext2D, planeDimension: PlaneDimension): Unit = {
    context.strokeStyle = "rgba(0,0,0,1)"
    context.lineWidth = 1

    val hw = planeDimension.simWidth / 2
    val hh = planeDimension.simHeight / 2

    context.beginPath()
    context.moveTo(-hw, -hh)
    context.lineTo(hw, -hh)
    context.lineTo(hw, hh)
    context.lineTo(-hw, hh)
    context.lineTo(-hw, -hh)
    context.stroke()
    context.closePath()

    val spacing = 10
    context.strokeStyle = "rgba(0,0,0,0.2)"
    context.beginPath()
    context.moveTo(-hw+10, -hh+10)
    context.lineTo(hw-10, -hh+10)
    context.lineTo(hw-10, hh-10)
    context.lineTo(-hw+10, hh-10)
    context.lineTo(-hw+10, -hh+10)
    context.stroke()
    context.closePath()

  }

  def drawAARect(context: CanvasRenderingContext2D, rect: AARect, color: String, lineWidth: Int): Unit = {
    context.strokeStyle = color
    context.lineWidth = lineWidth
    context.beginPath()
    val vertices = rect.verticesCCW
    context.moveTo(vertices(0).x, vertices(0).y)
    context.lineTo(vertices(1).x, vertices(1).y)
    context.lineTo(vertices(2).x, vertices(2).y)
    context.lineTo(vertices(3).x, vertices(3).y)
    context.lineTo(vertices(0).x, vertices(0).y)
    context.stroke()
  }
}
