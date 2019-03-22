package wust.webApp.views.pageheader

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import wust.css.ZIndex
import wust.ids.View
import wust.util._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.UI


object components {
  /// Required parameters from the outer context
  case class TabContextParms(
    currentView : View,
    pageStyle : PageStyle,
    switchViewFn : (View) => Unit
  )

  /// Parameters that make out a tab
  case class TabInfo(targetView : View,
                     icon : IconDefinition,
                     wording : String,
                     numItems : Int)

  /// helper functions that return VDomModifier's
  object modifiers {

    /// @return A class modifier, setting "active" or "inactive"
    def modActivityStateCssClass(currentView: View, tabInfo : TabInfo) =
      cls := (if (isActiveTab(currentView, tabInfo)) "active"
              else "inactive")

    /// @return A zIndex modifier, adjusting it based on activity
    def modActivityZIndex(currentView: View, tabInfo : TabInfo) =
      (if (isActiveTab(currentView, tabInfo))
         // 1500 is the value that is set by the tooltip normally
         // and allows it to be displayed
         zIndex := 1500
       else
         // set it to auto: this lets the shadow from main-viewrender overlap the tab
         zIndex.auto)

    /// @return A color modifier, setting the color matching the currently viewed topic
    def modTopicBackgroundColor(currentView: View, pageStyle: PageStyle, tabInfo : TabInfo) = {
      VDomModifier.ifTrue(isActiveTab(currentView, tabInfo))(
        backgroundColor := pageStyle.bgLightColor,
        borderBottomColor := pageStyle.bgLightColor)
    }

    /// @return A tooltip modifier
    def modTooltip(tabInfo : TabInfo) =
      UI.tooltip("bottom left") :=
        s"${tabInfo.targetView.toString}${(tabInfo.numItems > 0).ifTrue[String](
                                            s": ${tabInfo.numItems} ${tabInfo.wording}")}"
  }

  def isActiveTab(currentView: View, tabInfo: TabInfo) =
    currentView.viewKey == tabInfo.targetView.viewKey

  /// @return Various VDomModifier instances related to all tab types
  def commonModifiers(parms : TabContextParms, tabInfo : TabInfo) = Seq(
    modifiers.modActivityStateCssClass(parms.currentView, tabInfo),
    modifiers.modTopicBackgroundColor(parms.currentView, parms.pageStyle, tabInfo),
    modifiers.modTooltip(tabInfo),
    modifiers.modActivityZIndex(parms.currentView, tabInfo),
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
      cls := "viewswitcher-item single",
      backgroundColor := "rgba(255, 255, 255, 0.8)"
    )
  }

  /// @return a single iconized tab for switching to the respective view
  def singleTab(parms : TabContextParms, tabInfo : TabInfo) = {
    tabSkeleton(parms, tabInfo)(
      cls := "single",
      // VDomModifier.ifTrue(tabInfo.numItems > 0)(span(tabInfo.numItems, paddingLeft := "7px")),
    )
  }

  /// @return like singleTab, but two iconized tabs grouped together visually to switch the current view
  def doubleTab(parms : TabContextParms, leftTabInfo : TabInfo, rightTabInfo : TabInfo) = {
    val numItems = scala.math.max(leftTabInfo.numItems, rightTabInfo.numItems)
    VDomModifier (
      tabSkeleton(parms, leftTabInfo)(
        cls := "double left",
        /// tooltip sets zIndex to 1500. We need to increase it, since the right tab would otherwise hide
        /// this tabs shadow when it is active
        zIndex := ZIndex.tooltip + 10,
      ),
      tabSkeleton(parms, rightTabInfo)(
        cls := "double right",
        VDomModifier.ifTrue(numItems > 0)(
          span(leftTabInfo.numItems, paddingLeft := "7px")),
        ),
      )
  }

}

