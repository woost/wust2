package graph

import scalajs.js
import scala.scalajs.js.annotation._

trait D3Coordinates {
  // reserve field names for d3
  @JSExport var x: js.UndefOr[Double] = js.undefined
  @JSExport var y: js.UndefOr[Double] = js.undefined
  @JSExport var vx: js.UndefOr[Double] = js.undefined
  @JSExport var vy: js.UndefOr[Double] = js.undefined
  @JSExport var fx: js.UndefOr[Double] = js.undefined
  @JSExport var fy: js.UndefOr[Double] = js.undefined
}

trait PostPlatformSpecificExtensions extends D3Coordinates
trait RespondsToPlatformSpecificExtensions extends D3Coordinates {
  // reserve field names for d3
  @JSExport var source: Post = null
  @JSExport var target: D3Coordinates = null
}
