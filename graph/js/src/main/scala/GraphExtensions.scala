package graph

import scalajs.js
import scala.scalajs.js.annotation._

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
}

trait D3SimulationLink {
  // @JSExport var index: js.UndefOr[Int] = js.undefined
  @JSExport var source: D3SimulationNode = null
  @JSExport var target: D3SimulationNode = null
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

trait RespondsToPlatformSpecificExtensions extends D3SimulationLink with D3SimulationNode {
  //TODO: narrower type for source:
  // @JSExport override var source: Post = null

  // propagate d3 gets/sets to incident posts
  def x = (source.x.get + target.x.get) / 2
  def x_=(newX: Double) {
    val diff = newX - x.get
    source.x = source.x.get + diff / 2
    target.x = target.x.get + diff / 2
  }
  def y = (source.y.get + target.y.get) / 2
  def y_=(newY: Double) {
    val diff = newY - y.get
    source.y = source.y.get + diff / 2
    target.y = target.y.get + diff / 2
  }
  def vx = (source.vx.get + target.vx.get) / 2
  def vx_=(newVX: Double) {
    val diff = newVX - vx.get
    source.vx = source.vx.get + diff / 2
    target.vx = target.vx.get + diff / 2
  }
  def vy = (source.vy.get + target.vy.get) / 2
  def vy_=(newVY: Double) {
    val diff = newVY - vy.get
    source.vy = source.vy.get + diff / 2
    target.vy = target.vy.get + diff / 2
  }
}

trait ContainsPlatformSpecificExtensions extends D3SimulationLink
