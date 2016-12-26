package graph

import scalajs.js
import scala.scalajs.js.annotation._

trait D3SimulationNode {
  @JSExport var index: js.UndefOr[Int] = js.undefined
  @JSExport var x: js.UndefOr[Double] = js.undefined
  @JSExport var y: js.UndefOr[Double] = js.undefined
  @JSExport var vx: js.UndefOr[Double] = js.undefined
  @JSExport var vy: js.UndefOr[Double] = js.undefined
  @JSExport var fx: js.UndefOr[Double] = js.undefined
  @JSExport var fy: js.UndefOr[Double] = js.undefined
}

trait D3SimulationLink {
  @JSExport var index: js.UndefOr[Int] = js.undefined
  @JSExport var source: D3SimulationNode = null
  @JSExport var target: D3SimulationNode = null
}

trait PostPlatformSpecificExtensions extends D3SimulationNode
trait RespondsToPlatformSpecificExtensions extends D3SimulationLink {
  //TODO: @JSExport override var source: Post = null
}
