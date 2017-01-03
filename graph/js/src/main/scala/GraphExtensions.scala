package graph

import scalajs.js
import scala.scalajs.js.annotation._
import vectory._

//TODO: move to d3 facade
trait D3SimulationNode {
  @JSExport var index: js.UndefOr[Int] = js.undefined
  @JSExport def x: js.UndefOr[Double]
  @JSExport def x_=(newX: js.UndefOr[Double])
  @JSExport def y: js.UndefOr[Double]
  @JSExport def y_=(newY: js.UndefOr[Double])
  @JSExport def vx: js.UndefOr[Double]
  @JSExport def vx_=(newVX: js.UndefOr[Double])
  @JSExport def vy: js.UndefOr[Double]
  @JSExport def vy_=(newVX: js.UndefOr[Double])
  @JSExport var fx: js.UndefOr[Double] = js.undefined
  @JSExport var fy: js.UndefOr[Double] = js.undefined

  def pos = for (x <- x; y <- y) yield Vec2(x, y)
  def pos_=(newPos: js.UndefOr[Vec2]) {
    if (newPos.isDefined) {
      x = newPos.get.x
      y = newPos.get.y
    } else {
      x = js.undefined
      y = js.undefined
    }
  }
  def vel = for (vx <- vx; vy <- vy) yield Vec2(vx, vy)
  def vel_=(newVel: js.UndefOr[Vec2]) {
    if (newVel.isDefined) {
      vx = newVel.get.x
      vy = newVel.get.y
    } else {
      vx = js.undefined
      vy = js.undefined
    }
  }
  def fixedPos = for (fx <- fx; fy <- fy) yield Vec2(fx, fy)
  def fixedPos_=(newFixedPos: js.UndefOr[Vec2]) {
    if (newFixedPos.isDefined) {
      fx = newFixedPos.get.x
      fy = newFixedPos.get.y
    } else {
      fx = js.undefined
      fy = js.undefined
    }
  }

  var size: Vec2 = Vec2(0, 0)
  def rect = pos.map { pos => AARect(pos, size) }
  var centerOffset: Vec2 = Vec2(0, 0)
  var radius: Double = 0

  var dragStart = Vec2(0, 0)
}

trait D3SimulationLink[S <: D3SimulationNode, T <: D3SimulationNode] {
  // @JSExport var index: js.UndefOr[Int] = js.undefined
  @JSExport var source: S = _
  @JSExport var target: T = _
}

trait PostPlatformSpecificExtensions extends D3SimulationNode {
  private var _x: js.UndefOr[Double] = js.undefined
  def x = _x
  def x_=(newX: js.UndefOr[Double]) { _x = newX }
  private var _y: js.UndefOr[Double] = js.undefined
  def y = _y
  def y_=(newY: js.UndefOr[Double]) { _y = newY }
  private var _vx: js.UndefOr[Double] = js.undefined
  def vx = _vx
  def vx_=(newVX: js.UndefOr[Double]) { _vx = newVX }
  private var _vy: js.UndefOr[Double] = js.undefined
  def vy = _vy
  def vy_=(newVY: js.UndefOr[Double]) { _vy = newVY }
}

trait RespondsToPlatformSpecificExtensions extends D3SimulationLink[Post, D3SimulationNode] with D3SimulationNode {
  // propagate d3 gets/sets to incident posts
  def x = (source.x.get + target.x.get) / 2
  def x_=(newX: js.UndefOr[Double]) {
    val diff = for (x <- x; newX <- newX) yield (newX - x) / 2
    source.x = for (x <- source.x; diff <- diff) yield x + diff
    target.x = for (x <- target.x; diff <- diff) yield x + diff
  }
  def y = (source.y.get + target.y.get) / 2
  def y_=(newY: js.UndefOr[Double]) {
    val diff = for (y <- y; newY <- newY) yield (newY - y) / 2
    source.y = for (y <- source.y; diff <- diff) yield y + diff
    target.y = for (y <- target.y; diff <- diff) yield y + diff
  }
  def vx = (source.vx.get + target.vx.get) / 2
  def vx_=(newVX: js.UndefOr[Double]) {
    val diff = for (vx <- vx; newVX <- newVX) yield (newVX - vx) / 2
    source.vx = for (vx <- source.vx; diff <- diff) yield vx + diff
    target.vx = for (vx <- target.vx; diff <- diff) yield vx + diff
  }
  def vy = (source.vy.get + target.vy.get) / 2
  def vy_=(newVY: js.UndefOr[Double]) {
    val diff = for (vy <- vy; newVY <- newVY) yield (newVY - vy) / 2
    source.vy = for (vy <- source.vy; diff <- diff) yield vy + diff
    target.vy = for (vy <- target.vy; diff <- diff) yield vy + diff
  }
}

trait ContainsPlatformSpecificExtensions extends D3SimulationLink[Post, Post]
