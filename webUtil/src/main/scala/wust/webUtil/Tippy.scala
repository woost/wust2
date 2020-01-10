package wust.webUtil

import org.scalajs.dom.Element
import outwatch.dom._
import outwatch.dom.dsl.{ label, _ }
import outwatch.dom.helpers.AttributeBuilder
import outwatch.ext.monix._
import outwatch.reactive._
import outwatch.reactive.handler._
import scala.scalajs.js
import wust.webUtil.outwatchHelpers._
import org.scalajs.dom.console
import org.scalajs.dom.html
import outwatch.reactive._

package object tippy {

  import scala.util.Try

  import wust.facades.tippy._

  private val defaultTheme = "" // see https://atomiks.github.io/tippyjs/#themes and https://atomiks.github.io/tippyjs/themes/ and add line in webpack.base.common.js
  private val defaultZIndex = 18000

  def tooltipMod(props: TippyProps, triggerHide: SourceStream[Unit] = SourceStream.empty): VDomModifier = managedElement.asHtml{ elem =>
    val tippyInstance = tippy(elem, props)
    val hideSubscription = triggerHide.foreach(_ => tippyInstance.hide())
    Subscription{ () =>
      Try(tippyInstance.destroy())
      elem.asInstanceOf[js.Dynamic].updateDynamic("_tippy")(js.undefined)
      hideSubscription.cancel()
    }
  }

  //TODO: how to abstract over the tooltip content type?
  // The props and function arguments are all exactly the same.
  // the content we want to pass as an attribute is either String or VNode.
  // It needs to be converted to tippy content, which is: String | Element

  def tooltip(
    _placement: String = "top",
    _boundary: String = "scrollParent",
    permanent: Boolean = false,
    _sticky: Boolean = false
  ): AttributeBuilder[String, VDomModifier] = str => tooltipMod(new TippyProps {
    content = str

    ignoreAttributes = true // increases performance by not parsing data-attributes of content
    theme = defaultTheme
    zIndex = defaultZIndex

    placement = _placement
    boundary = _boundary

    if (permanent) {
      showOnCreate = true
      hideOnClick = false
      trigger = "manual"
    }

    if (_sticky) {
      plugins = js.Array[TippyPlugin](TippyPlugin.sticky)
      sticky = true
    }
  })

  def tooltipHtml(
    _placement: String = "top",
    _boundary: String = "scrollParent",
    permanent: Boolean = false,
    _sticky: Boolean = false
  ): AttributeBuilder[VNode, VDomModifier] = node => tooltipMod(new TippyProps {
    content = (() => node.render): js.Function0[Element]

    ignoreAttributes = true // increases performance by not parsing data-attributes of content
    theme = defaultTheme
    zIndex = defaultZIndex

    placement = _placement
    boundary = _boundary

    if (permanent) {
      showOnCreate = true
      hideOnClick = false
      trigger = "manual"
    }

    if (_sticky) {
      plugins = js.Array[TippyPlugin](TippyPlugin.sticky)
      sticky = true
    }
  })

  def menu(
    _placement: String = "bottom",
    _boundary: String = "window",
    close: SourceStream[Unit] = SourceStream.empty
  ): AttributeBuilder[VNode, VDomModifier] = node => VDomModifier(
    tooltipMod(new TippyProps {
      content = (() => node.render): js.Function0[Element]

      ignoreAttributes = true // increases performance by not parsing data-attributes of content
      theme = "light-border"
      zIndex = defaultZIndex

      trigger = "click"
      interactive = true
      duration = 0

      placement = _placement
      boundary = _boundary
    }, close),
    cursor.pointer
  )
}
