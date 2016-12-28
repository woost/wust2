package graph

import scalajs.js
import scala.scalajs.js.annotation._
import vectory._

trait D3SimulationNode {
  @JSExport var index: js.UndefOr[Int] = js.undefined
  @JSExport def x: js.UndefOr[Double]
  @JSExport def x_=(newX: Double)
  @JSExport def y: js.UndefOr[Double]
  @JSExport def y_=(newY: Double)
  @JSExport def vx: js.UndefOr[Double]
  @JSExport def vx_=(newVX: Double)
  @JSExport def vy: js.UndefOr[Double]
  @JSExport def vy_=(newVX: Double)
  @JSExport var fx: js.UndefOr[Double] = js.undefined
  @JSExport var fy: js.UndefOr[Double] = js.undefined

  def pos = Vec2(x.get, y.get)
  def pos_(newPos: Vec2) { x = newPos.x; y = newPos.y }
  def vel = Vec2(x.get, y.get)
  def vel_(newVel: Vec2) { x = newVel.x; y = newVel.y }
  def fix = Vec2(fx.get, fy.get)
  def fix_(newFix: Vec2) { x = newFix.x; y = newFix.y }

  var size: Vec2 = Vec2(1, 1)
  var centerOffset: Vec2 = Vec2(0, 0)
}

trait D3SimulationLink[S <: D3SimulationNode, T <: D3SimulationNode] {
  // @JSExport var index: js.UndefOr[Int] = js.undefined
  @JSExport var source: S = _
  @JSExport var target: T = _
}

trait PostPlatformSpecificExtensions extends D3SimulationNode {
  private var _x: js.UndefOr[Double] = js.undefined
  def x = _x
  def x_=(newX: Double) { _x = newX }
  private var _y: js.UndefOr[Double] = js.undefined
  def y = _y
  def y_=(newY: Double) { _y = newY }
  private var _vx: js.UndefOr[Double] = js.undefined
  def vx = _vx
  def vx_=(newVX: Double) { _vx = newVX }
  private var _vy: js.UndefOr[Double] = js.undefined
  def vy = _vy
  def vy_=(newVY: Double) { _vy = newVY }
}

trait RespondsToPlatformSpecificExtensions extends D3SimulationLink[Post, D3SimulationNode] with D3SimulationNode {
  // propagate d3 gets/sets to incident posts
  def x = (source.x.get + target.x.get) / 2
  def x_=(newX: Double) {
    val diff = (newX - x.get) / 2
    source.x = source.x.get + diff
    target.x = target.x.get + diff
  }
  def y = (source.y.get + target.y.get) / 2
  def y_=(newY: Double) {
    val diff = (newY - y.get) / 2
    source.y = source.y.get + diff
    target.y = target.y.get + diff
  }
  def vx = (source.vx.get + target.vx.get) / 2
  def vx_=(newVX: Double) {
    val diff = (newVX - vx.get) / 2
    source.vx = source.vx.get + diff
    target.vx = target.vx.get + diff
  }
  def vy = (source.vy.get + target.vy.get) / 2
  def vy_=(newVY: Double) {
    val diff = (newVY - vy.get) / 2
    source.vy = source.vy.get + diff
    target.vy = target.vy.get + diff
  }
}

trait ContainsPlatformSpecificExtensions extends D3SimulationLink[Post, Post]
