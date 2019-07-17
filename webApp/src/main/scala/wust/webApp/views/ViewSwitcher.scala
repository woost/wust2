package wust.webApp.views

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
import wust.webApp.views.PageHeaderParts.{ TabInfo, singleTab }
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
    currentView.foreach { view => GlobalState.urlConfig.update(_.focus(view)) }

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

        val viewTabs = channelNode.flatMap(_.views).getOrElse(bestView :: Nil).map { view =>
          singleTab(currentView, ViewSwitcher.viewToTabInfo(view, numMsg = numMsg, numTasks = numTasks, numFiles = numFiles))
        }

        viewTabs :+ addNewViewTab
      },

    )
  }

}
