package wust.webApp.views

import cats.data.NonEmptyList
import cats.syntax._
import cats.implicits._
import wust.css.{ Styles, ZIndex }
import flatland._
import fontAwesome._
import monix.reactive.Observer
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.css.Styles
import wust.graph.{ GraphChanges, Node }
import wust.ids._
import wust.sdk.Colors
import wust.webApp._
import wust.webApp.state._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, Elements, Ownable, UI }

import scala.reflect.ClassTag

object ViewSwitcher {
  def viewToTabInfo(view: View, numMsg: Int, numTasks: Int, numFiles: Int): TabInfo = view match {
    case View.Dashboard   => TabInfo(View.Dashboard, Icons.dashboard, "dashboard", 0)
    case View.Chat        => TabInfo(View.Chat, Icons.chat, "messages", numMsg)
    case View.Thread      => TabInfo(View.Thread, Icons.thread, "messages", numMsg)
    case View.List        => TabInfo(View.List, Icons.list, "tasks", numTasks)
    case View.Kanban      => TabInfo(View.Kanban, Icons.kanban, "tasks", numTasks)
    case View.Files       => TabInfo(View.Files, Icons.files, "files", numFiles)
    case View.Graph       => TabInfo(View.Graph, Icons.graph, "nodes", numTasks)
    case view: View.Table => TabInfo(view, Icons.table, "records", (if (view.roles.contains(NodeRole.Task)) numTasks else 0) + (if (view.roles.contains(NodeRole.Message)) numMsg else 0))
    case View.Content     => TabInfo(View.Content, Icons.notes, "notes", 0)
    case View.Gantt       => TabInfo(View.Gantt, Icons.gantt, "tasks", 0)
    case View.Topological => TabInfo(View.Topological, Icons.topological, "tasks", 0)
    case view             => TabInfo(view, freeSolid.faSquare, "", 0) //TODO complete icon definitions
  }

  //TODO FocusState?
  def apply(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val currentView = Var[View](View.Empty)
    GlobalState.viewConfig
      .collect { case config if config.page.parentId.contains(channelId) => config.view }
      .foreach { currentView() = _ }
    currentView.triggerLater { view => GlobalState.urlConfig.update(_.focus(view)) }

    apply(channelId, currentView)
  }

  def apply(channelId: NodeId, currentView: Var[View], initialView: Option[View.Visible] = None): VNode = {
    div.thunk(uniqueKey(channelId.toStringFast))(initialView)(Ownable { implicit ctx => modifier(channelId, currentView, initialView) })
  }

  def modifier(channelId: NodeId, currentView: Var[View], initialView: Option[View.Visible])(implicit ctx: Ctx.Owner): VDomModifier = {
    val closeDropdown = PublishSubject[Unit]

    def addNewTabDropdown = div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        div(freeSolid.faPlus, fontSize := "16px", color := Colors.pageHeaderControl, paddingLeft := "2px", paddingRight := "2px"),
        UI.dropdownMenu(VDomModifier(
          padding := "5px",
          div(cls := "item", display.none), //TODO ui dropdown needs at least one element

          ViewModificationMenu.selector(channelId, currentView, initialView, closeDropdown)
        ), close = closeDropdown, dropdownModifier = cls := "top left")
      )
    })

    val addNewViewTab = div(
      cls := "viewswitcher-item",
      addNewTabDropdown,
      zIndex := ZIndex.overlayLow
    )

    VDomModifier(
      marginLeft := "5px",
      Styles.flex,
      justifyContent.center,
      alignItems.flexEnd,
      minWidth.auto,

      Rx {
        val graph = GlobalState.graph()
        val channelNode = graph.nodesById(channelId)
        val user = GlobalState.user()

        def bestView = graph.nodesById(channelId).flatMap(ViewHeuristic.bestView(graph, _, user.id)).getOrElse(View.Empty)

        val nodeIdx = graph.idToIdx(channelId)
        val (numMsg, numTasks, numFiles) = (for {
          nodeIdx <- nodeIdx
          if !BrowserDetect.isPhone
        } yield {
          val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)
          val taskChildrenCount = graph.taskChildrenIdx.sliceLength(nodeIdx)
          val filesCount = graph.pageFilesIdx(nodeIdx).length
          (messageChildrenCount, taskChildrenCount, filesCount)
        }) getOrElse ((0, 0, 0))

        VDomModifier(
          channelNode.flatMap(_.views).getOrElse(bestView :: Nil).map { view =>
            singleTab(currentView, ViewSwitcher.viewToTabInfo(view, numMsg = numMsg, numTasks = numTasks, numFiles = numFiles))
          },
          addNewViewTab
        )
      },

    )
  }

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
    def modActivityStateCssClass(currentView: Rx[View], tabInfo: TabInfo)(implicit ctx: Ctx.Owner) = Rx {
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

  def tabSkeleton(currentView: Var[View], tabInfo: TabInfo)(implicit ctx: Ctx.Owner): BasicVNode = {
    div(
      // modifiers
      cls := "viewswitcher-item",
      modifiers.modActivityStateCssClass(currentView, tabInfo),
      modifiers.modTooltip(tabInfo),

      // actions
      onClick.stopPropagation.foreach { e =>
        val clickedView = tabInfo.targetView.asInstanceOf[View.Visible]
        if (e.ctrlKey) {
          currentView.update{ oldView =>
            oldView match {
              case View.Empty => clickedView
              case view: View.Visible if view == clickedView => View.Empty
              case view: View.Tiled if view.views.toList.contains(clickedView) =>
                if (view.views.toList.distinct.length == 1) View.Empty
                else view.copy(views = NonEmptyList.fromList(view.views.filterNot(_ == clickedView)).get)
              case view: View.Tiled   => view.copy(views = view.views :+ clickedView)
              case view: View.Visible if view != clickedView => View.Tiled(ViewOperator.Row, NonEmptyList.of(view, clickedView))
              case view => view
            }
          }
        } else {
          currentView() = clickedView
        }
      },

      // content
      div(cls := "fa-fw", tabInfo.icon),
    )
  }

  /// @return a single iconized tab for switching to the respective view
  def singleTab(currentView: Var[View], tabInfo: TabInfo)(implicit ctx: Ctx.Owner) = {
    tabSkeleton(currentView, tabInfo).apply(
      cls := "single",
    // VDomModifier.ifTrue(tabInfo.numItems > 0)(span(tabInfo.numItems, paddingLeft := "7px")),
    )
  }
}
