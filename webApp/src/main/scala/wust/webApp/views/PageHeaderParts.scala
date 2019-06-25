package wust.webApp.views

import outwatch.dom.dsl._
import outwatch.dom.{VDomModifier, _}
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._
import wust.ids.View
import wust.sdk.Colors
import wust.webApp.state.PageStyle

object PageHeaderParts {
  /// Required parameters from the outer context
  final case class TabContextParms(
    currentView : View,
    pageStyle : PageStyle,
    switchViewFn : (View) => Unit
  )

  /// Parameters that make out a tab
  final case class TabInfo(targetView : View,
                     icon : VDomModifier,
                     wording : String,
                     numItems : Int)

  /// helper functions that return VDomModifier's
  object modifiers {

    /// @return A class modifier, setting "active" or "inactive"
    def modActivityStateCssClass(currentView: View, tabInfo : TabInfo) =
      cls := (if (isActiveTab(currentView, tabInfo)) "active"
              else "inactive")

    /// @return A color modifier, setting the color matching the currently viewed topic
    def modTopicBackgroundColor(currentView: View, pageStyle: PageStyle, tabInfo : TabInfo) = {
      VDomModifier.ifTrue(isActiveTab(currentView, tabInfo))(
        backgroundColor := Colors.contentBg,
      )
    }

    /// @return A tooltip modifier
    def modTooltip(tabInfo : TabInfo) =
      UI.tooltip("bottom left") :=
        s"${tabInfo.targetView.toString}${if (tabInfo.numItems > 0) s": ${tabInfo.numItems} ${tabInfo.wording}" else ""}"
  }

  def isActiveTab(currentView: View, tabInfo: TabInfo) =
    currentView.viewKey == tabInfo.targetView.viewKey

  /// @return Various VDomModifier instances related to all tab types
  def commonModifiers(parms : TabContextParms, tabInfo : TabInfo) = Seq(
    modifiers.modActivityStateCssClass(parms.currentView, tabInfo),
    modifiers.modTopicBackgroundColor(parms.currentView, parms.pageStyle, tabInfo),
    modifiers.modTooltip(tabInfo),
  )

  /// @return Most basic tab element that is further refined inside e.g. singleTab / doubleTab
  def tabSkeleton(parms : TabContextParms, tabInfo : TabInfo) = {
    div(
      // modifiers
      cls := "viewswitcher-item",
      commonModifiers(parms, tabInfo),

      // actions
      onClick.stopPropagation foreach parms.switchViewFn(tabInfo.targetView),

      // content
      div(cls := "fa-fw", tabInfo.icon),
    )
  }

  def customTab = {
    div(
      cls := "viewswitcher-item"
    )
  }

  /// @return a single iconized tab for switching to the respective view
  def singleTab(parms : TabContextParms, tabInfo : TabInfo) = {
    tabSkeleton(parms, tabInfo)(
      cls := "single",
      // VDomModifier.ifTrue(tabInfo.numItems > 0)(span(tabInfo.numItems, paddingLeft := "7px")),
    )
  }
}
