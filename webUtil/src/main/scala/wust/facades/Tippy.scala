package wust.facades.tippy

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation._
import org.scalajs.dom.Element

import outwatch.dom._
import outwatch.dom.dsl.{ label, _ }
import outwatch.dom.helpers.AttributeBuilder
import outwatch.ext.monix._
import outwatch.reactive._
import outwatch.reactive.handler._

@js.native
@JSImport("tippy.js", JSImport.Default)
object tippy extends js.Object {
  def apply(element: Element, options: TippyProps): TippyInstance = js.native
}

trait TippyPlugin extends js.Object
object TippyPlugin {
  // https://atomiks.github.io/tippyjs/plugins/
  @js.native
  @JSImport("tippy.js", "followCursor")
  object followCursor extends TippyPlugin

  @js.native
  @JSImport("tippy.js", "sticky")
  object sticky extends TippyPlugin
}

trait TippyProps extends js.Object {
  // https://atomiks.github.io/tippyjs/all-props
  var content: js.UndefOr[String | Element] = js.undefined
  var appendTo: js.UndefOr[String | Element] = js.undefined
  var boundary: js.UndefOr[String | Element] = js.undefined
  var flipOnUpdate: js.UndefOr[Boolean] = js.undefined
  var interactive: js.UndefOr[Boolean] = js.undefined
  var theme: js.UndefOr[String] = js.undefined
  var placement: js.UndefOr[String] = js.undefined
  var zIndex: js.UndefOr[Int] = js.undefined
  var ignoreAttributes: js.UndefOr[Boolean] = js.undefined
  var showOnCreate: js.UndefOr[Boolean] = js.undefined
  var delay: js.UndefOr[Double | js.Tuple2[Any, Any]] = js.undefined
  var hideOnClick: js.UndefOr[Boolean] = js.undefined
  var trigger: js.UndefOr[String] = js.undefined

  var plugins: js.UndefOr[js.Array[TippyPlugin]] = js.undefined
  var sticky: js.UndefOr[Boolean] = js.undefined // requires sticky plugin
}

@js.native
trait TippyInstance extends js.Object {
  // https://atomiks.github.io/tippyjs/methods/
  def destroy(): Unit = js.native
}
