package wust.webApp.views

import cats.data.NonEmptyList
import wust.webUtil.Elements.onClickDefault
import fontAwesome._
import wust.webUtil.tippy
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.ids._
import wust.sdk.Colors
import wust.webApp._
import wust.webApp.state._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, UI}

object ViewSwitcher {
  def viewToTabInfo(view: View, numMsg: Int, numTasks: Int, numFiles: Int): TabInfo = view match {
    case View.Dashboard   => TabInfo(View.Dashboard, Icons.dashboard, "dashboard", 0)
    case View.Chat        => TabInfo(View.Chat, Icons.chat, "messages", numMsg)
    case View.Thread      => TabInfo(View.Thread, Icons.thread, "messages", numMsg)
    case View.List        => TabInfo(View.List, Icons.list, "tasks", numTasks)
    case View.ListWithChat=> TabInfo(View.ListWithChat, freeRegular.faComment, "tasks", numTasks)
    case View.Kanban      => TabInfo(View.Kanban, Icons.kanban, "tasks", numTasks)
    case View.Files       => TabInfo(View.Files, Icons.files, "files", numFiles)
    case View.Graph       => TabInfo(View.Graph, Icons.graph, "nodes", numTasks)
    case view: View.Table => TabInfo(view, Icons.table, "records", (if (view.roles.contains(NodeRole.Task)) numTasks else 0) + (if (view.roles.contains(NodeRole.Message)) numMsg else 0))
    case View.Content     => TabInfo(View.Content, Icons.notes, "notes", 0)
    case View.Gantt       => TabInfo(View.Gantt, Icons.gantt, "tasks", 0)
    case View.Topological => TabInfo(View.Topological, Icons.topological, "tasks", 0)
    case View.Form        => TabInfo(View.Form, Icons.form, "", 0)
    case View.Calendar    => TabInfo(View.Calendar, Icons.calendar, "", 0)
    case view             => TabInfo(view, freeSolid.faSquare, "", 0) //TODO complete icon definitions
  }

  //TODO FocusState?
  def apply(channelId: NodeId)(implicit ctx: Ctx.Owner): EmitterBuilder.Sync[View, VNode] = EmitterBuilder.ofNode[View] { viewSink =>
    {
      val currentView = Var[View](View.Empty)
      GlobalState.viewConfig
        .foreach({
          case config if config.page.parentId.contains(channelId) => currentView() = config.view
          case _ => ()
        }: ViewConfig => Unit)
      currentView.triggerLater { view =>
        GlobalState.urlConfig.update(_.focus(view))
        viewSink.onNext(view)
      }

      apply(channelId, currentView)
    }
  }

  val addViewIcon = freeSolid.faCaretDown
  def apply(channelId: NodeId, currentView: Var[View], initialView: Option[View.Visible] = None)(implicit ctx: Ctx.Owner): VNode = {
    val closeDropdown = SinkSourceHandler.publish[Unit]

    def addNewViewTab = div(
      div(
        padding := "7px 10px",
        div(addViewIcon, fontSize := "16px", color := "white", paddingLeft := "2px", paddingRight := "2px", UI.tooltip := "Add or remove views"),

        tippy.menu(close = closeDropdown, _boundary = "scrollParent") := div(
          color := Colors.fgColor,
          padding := "5px",
          div(cls := "item", display.none), //TODO ui dropdown needs at least one element

          ViewModificationMenu.selector(channelId, currentView, initialView, closeDropdown)
        )
      )
    )

    div(
      marginLeft := "5px",
      Styles.flex,
      justifyContent.center,
      alignItems.flexEnd,
      minWidth.auto,

      Rx {
        val graph = GlobalState.graph()
        graph.idToIdx(channelId).map { nodeIdx =>
          val channelNode = graph.nodes(nodeIdx)
          val userId = GlobalState.userId()

          def bestView = ViewHeuristic.bestView(graph, channelNode, userId).getOrElse(View.Empty)

          val (numMsg, numTasks, numFiles) = if (BrowserDetect.isMobile) (0,0,0) else {
            val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)
            val taskChildrenCount = graph.taskChildrenIdx.sliceLength(nodeIdx)
            val filesCount = graph.pageFilesIdx(nodeIdx).length
            (messageChildrenCount, taskChildrenCount, filesCount)
          }

          VDomModifier(
            channelNode.views.getOrElse(bestView :: Nil).map { view =>
              singleTab(currentView, ViewSwitcher.viewToTabInfo(view, numMsg = numMsg, numTasks = numTasks, numFiles = numFiles))
            },
          )
        }
      },

      GlobalState.presentationMode.map {
        case PresentationMode.Full => addNewViewTab
        case _ => VDomModifier.empty
      }
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
      UI.tooltip :=
        s"${tabInfo.targetView.toString}${if (tabInfo.numItems > 0) s": ${tabInfo.numItems} ${tabInfo.wording}" else ""}"
  }

  private def isActiveTab(currentView: View, tabInfo: TabInfo): Boolean = {
    val tabViewKey = tabInfo.targetView.viewKey
    currentView match {
      case View.Tiled(_, views) => views.exists(_.viewKey == tabViewKey)
      case view                 => view.viewKey == tabViewKey
    }
  }

  def singleTab(currentView: Var[View], tabInfo: TabInfo)(implicit ctx: Ctx.Owner): VNode = div(
    // modifiers
    cls := "viewswitcher-item single",
    modifiers.modActivityStateCssClass(currentView, tabInfo),
    modifiers.modTooltip(tabInfo),

    // VDomModifier.ifTrue(tabInfo.numItems > 0)(span(tabInfo.numItems, paddingLeft := "7px")),

    // actions
    onClickDefault.foreach { e =>
      val clickedView = tabInfo.targetView.asInstanceOf[View.Visible]
      if (e.ctrlKey) {
        currentView.update{ oldView =>
          oldView match {
            case View.Empty                                => clickedView
            case view: View.Visible if view == clickedView => View.Empty
            case view: View.Tiled if view.views.toList.contains(clickedView) =>
              if (view.views.toList.distinct.length == 1) View.Empty
              else view.copy(views = NonEmptyList.fromList(view.views.filterNot(_ == clickedView)).get)
            case view: View.Tiled                          => view.copy(views = view.views :+ clickedView)
            case view: View.Visible if view != clickedView => View.Tiled(ViewOperator.Row, NonEmptyList.of(view, clickedView))
            case view                                      => view
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
