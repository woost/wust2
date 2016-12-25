package graph

import scalajs.js
import scala.scalajs.js.annotation._

trait PostPlatformSpecificExtensions {
  // reserve field names for d3
  @JSExport var x: js.UndefOr[Double] = js.undefined
  @JSExport var y: js.UndefOr[Double] = js.undefined
}
trait RespondsToPlatformSpecificExtensions {
  // reserve field names for d3
  @JSExport var source: Post = null
  @JSExport var target: Post = null
}
