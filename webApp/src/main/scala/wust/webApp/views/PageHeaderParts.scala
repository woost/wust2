package wust.webApp.views

import outwatch.dom.dsl._
import outwatch.dom.{VDomModifier, _}
import rx._
import wust.ids.View
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._

object PageHeaderParts {

  /// Parameters that make out a tab
  final case class TabInfo(
    targetView: View,
    icon: VDomModifier,
    wording: String,
    numItems: Int
  )

  /// helper functions that return VDomModifier's
  private object modifiers {

    /// @return A class modifier, setting "active" or "inactive"
    def modActivityStateCssClass(currentView: Rx[View], tabInfo: TabInfo)(implicit ctx:Ctx.Owner) = Rx {
      if (isActiveTab(currentView(), tabInfo))
        cls := "active"
      else
        cls := "inactive"
    }

    /// @return A tooltip modifier
    def modTooltip(tabInfo: TabInfo): VDomModifier =
      UI.tooltip("bottom left") :=
        s"${tabInfo.targetView.toString}${if (tabInfo.numItems > 0) s": ${tabInfo.numItems} ${tabInfo.wording}" else ""}"
  }

  private def isActiveTab(currentView: View, tabInfo: TabInfo): Boolean = {
    val tabViewKey = tabInfo.targetView.viewKey
    currentView match {
      case View.Tiled(_, views) => views.exists(_.viewKey == tabViewKey)
      case view                 => view.viewKey == tabViewKey
    }
  }

  def tabSkeleton(currentView: Var[View], tabInfo: TabInfo)(implicit ctx:Ctx.Owner): BasicVNode = {
    div(
      // modifiers
      cls := "viewswitcher-item",
      modifiers.modActivityStateCssClass(currentView, tabInfo),
      modifiers.modTooltip(tabInfo),

      // actions
      onClick.stopPropagation.foreach { e =>
        currentView() = tabInfo.targetView
      },

      // content
      div(cls := "fa-fw", tabInfo.icon),
    )
  }

  /// @return a single iconized tab for switching to the respective view
  def singleTab(currentView: Var[View], tabInfo: TabInfo)(implicit ctx:Ctx.Owner) = {
    tabSkeleton(currentView, tabInfo).apply(
      cls := "single",
    // VDomModifier.ifTrue(tabInfo.numItems > 0)(span(tabInfo.numItems, paddingLeft := "7px")),
    )
  }
}
