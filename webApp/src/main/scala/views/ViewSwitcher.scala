package wust.webApp.views

import wust.sdk.Colors
import cats.effect.IO
import clipboard.ClipboardJS
import fontAwesome._
import googleAnalytics.Analytics
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph.{Edge, GraphChanges, Node}
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor.hue
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.{Navigator, ShareData}
import wust.webApp.outwatchHelpers._
import wust.webApp.search.Search
import wust.webApp.state._
import wust.webApp.views.Components.{renderNodeData, _}

import scala.collection.breakOut
import scala.scalajs.js
import scala.util.{Failure, Success}
import pageheader.components.{TabContextParms, TabInfo, customTab, doubleTab, singleTab}


object ViewSwitcher {
  private def viewToTabInfo(view: View, numMsg: Int, numTasks: Int, numFiles: Int): TabInfo = view match {
    case View.Dashboard => TabInfo(View.Dashboard, Icons.dashboard, "dashboard", 0)
    case View.Chat => TabInfo(View.Chat, Icons.chat, "messages", numMsg)
    case View.Thread => TabInfo(View.Thread, Icons.thread, "messages", numMsg)
    case View.List => TabInfo(View.List, Icons.list, "tasks", numTasks)
    case View.Kanban => TabInfo(View.Kanban, Icons.kanban, "tasks", numTasks)
    case View.Files => TabInfo(View.Files, Icons.files, "files", numFiles)
    case View.Graph => TabInfo(View.Graph, Icons.graph, "nodes", numTasks)
    case view: View.Table => TabInfo(view, Icons.table, "records", (if (view.roles.contains(NodeRole.Task)) numTasks else 0) + (if (view.roles.contains(NodeRole.Message)) numMsg else 0))
    case View.Content => TabInfo(View.Content, Icons.notes, "notes", 0)
    case View.Gantt => TabInfo(View.Gantt, Icons.gantt, "tasks", 0)
    case View.Topological => TabInfo(View.Topological, Icons.topological, "tasks", 0)
    case view => TabInfo(view, freeSolid.faSquare, "", 0) //TODO complete icon definitions
  }

  private val viewDefs: Array[View.Visible] = Array(
    View.Dashboard,
    View.List,
    View.Kanban,
    View.Chat,
    View.Content,
    View.Table(NodeRole.Task :: Nil),
    View.Thread,
    View.Files,
    View.Graph,
    // View.Gantt,
    View.Topological,
  )

  def viewCheckboxes = UI.multiCheckbox[View.Visible](
    viewDefs,
    view => span(
      marginLeft := "4px",
      viewToTabInfo(view, 0, 0, 0).icon,
      span(marginLeft := "4px", view.toString)
    ),
  ).mapResult { modifier =>
    VDomModifier(
      width := "100%",
      padding := "20px 10px",
      h2("Select views:"),
      modifier,
      div(width := "100%", fontSize.smaller, textAlign.right, color.gray, "(can be changed later)"),
    )
  }

  //TODO FocusState?
  @inline def apply(state: GlobalState, channelId: NodeId): VNode = {
    apply(state, channelId, state.view, view => state.urlConfig.update(_.focus(view)))
  }
  @inline def apply(state: GlobalState, channelId: NodeId, viewRx: Rx[View.Visible], viewAction: View => Unit, initialView: Option[View.Visible] = None): VNode = {
    div.thunk(uniqueKey(channelId.toStringFast))(initialView)(Ownable { implicit ctx => modifier(state, channelId, viewRx, viewAction, initialView) })
  }

  def selectForm(state: GlobalState, channelId: NodeId): VNode = {
    div.thunkStatic(uniqueKey(channelId.toStringFast))(Ownable { implicit ctx => selector(state, channelId, state.view, view => state.urlConfig.update(_.focus(view)), None, Observer.empty) })
  }

  private def modifier(state: GlobalState, channelId: NodeId, viewRx: Rx[View.Visible], viewAction: View => Unit, initialView: Option[View.Visible])(implicit ctx: Ctx.Owner): VDomModifier = {
    val closeDropdown = PublishSubject[Unit]

    def addNewTabDropdown = div.thunkStatic(uniqueKey)(Ownable { implicit ctx => VDomModifier(
      div(freeSolid.faEllipsisV, color := Colors.pageHeaderControl, paddingLeft := "2px", paddingRight := "2px"),
      UI.dropdownMenu(VDomModifier(
        padding := "5px",
        div(cls := "item", display.none), //TODO ui dropdown needs at least one element

        selector(state, channelId, viewRx, viewAction, initialView, closeDropdown)
      ), close = closeDropdown, dropdownModifier = cls := "top left")
    )})

    val addNewViewTab = customTab(addNewTabDropdown, zIndex := ZIndex.overlayLow)

    VDomModifier(
      marginLeft := "5px",
      Styles.flex,
      justifyContent.center,
      alignItems.flexEnd,
      minWidth.auto,

      Rx {
        val currentView = viewRx()
        val pageStyle = PageStyle.ofNode(Some(channelId))
        val graph = state.graph()
        val channelNode = graph.nodesById(channelId)

        def bestView = graph.nodesById(channelId).flatMap(ViewHeuristic.bestView(graph, _)).getOrElse(View.Empty)

        val (numMsg, numTasks, numFiles) = if(!BrowserDetect.isPhone) {
          val nodeIdx = graph.idToIdxOrThrow(channelId)
          val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)
          val taskChildrenCount = graph.taskChildrenIdx.sliceLength(nodeIdx)
          val filesCount = graph.pageFilesIdx(nodeIdx).length
          (messageChildrenCount, taskChildrenCount, filesCount)
        } else (0, 0, 0)

        val parms = TabContextParms(
          currentView,
          pageStyle,
          (targetView : View) => {
            viewAction(targetView)
            Analytics.sendEvent("viewswitcher", "switch", targetView.viewKey)
          }
        )

        val viewTabs = channelNode.flatMap(_.views).getOrElse(bestView :: Nil).map { view =>
          singleTab(parms, viewToTabInfo(view, numMsg = numMsg, numTasks = numTasks, numFiles = numFiles))
        }

        viewTabs :+ addNewViewTab
      },

    )
  }

  private def selector(state: GlobalState, channelId: NodeId, viewRx: Rx[View.Visible], viewAction: View => Unit, initialView: Option[View.Visible], done: Observer[Unit])(implicit ctx: Ctx.Owner): VDomModifier = {
    val nodeRx = Rx {
      state.graph().nodesByIdOrThrow(channelId)
    }

    val existingViews = Rx {
      val node = nodeRx()
      node.views match {
        case None        => ViewHeuristic.bestView(state.graph(), node).toList
        case Some(views) => views
      }
    }

    val hasViews = Rx {
      nodeRx().views.isDefined
    }

    def addNewView(newView: View.Visible) : Unit = if (viewDefs.contains(newView)) { // only allow defined views
      done.onNext(())
      val node = nodeRx.now
      val currentViews = existingViews.now

      if (!currentViews.contains(newView)) {
        val newNode = node match {
          case n: Node.Content => n.copy(views = Some(currentViews :+ newView))
          case n: Node.User    => n.copy(views = Some(currentViews :+ newView))
        }

        state.eventProcessor.changes.onNext(GraphChanges.addNode(newNode))
      }

      if (viewRx.now != newView) {
        viewAction(newView)
      }
    }

    def resetViews(): Unit = {
      done.onNext(())
      val node = nodeRx.now
      if (node.views.isDefined) {
        val newNode = node match {
          case n: Node.Content => n.copy(views = None)
          case n: Node.User => n.copy(views = None)
        }

        val newView = ViewHeuristic.bestView(state.graph.now, node).getOrElse(View.Empty)
        if (viewRx.now != newView) {
          viewAction(newView)
        }

        state.eventProcessor.changes.onNext(GraphChanges.addNode(newNode))
      }
    }

    def removeView(view: View.Visible): Unit = {
      done.onNext(())
      val node = nodeRx.now
      val currentViews = node.views.getOrElse(Nil)
      val filteredViews = currentViews.filterNot(_ == view)
      val newNode = node match {
        case n: Node.Content => n.copy(views = Some(filteredViews))
        case n: Node.User => n.copy(views = Some(filteredViews))
      }

      //switch to remaining view
      if (viewRx.now == view) {
        val currPosition = currentViews.indexWhere(_ == view)
        val nextPosition = currPosition - 1
        val newView = if (nextPosition < 0) filteredViews.headOption.getOrElse(View.Empty) else filteredViews(nextPosition)
        viewAction(newView)
      }

      state.eventProcessor.changes.onNext(GraphChanges.addNode(newNode))
    }

    //TODO rewrite this in a less sideeffecting way
    viewRx.triggerLater { view => addNewView(view) }
    initialView.foreach(addNewView(_))

    VDomModifier(
      div(
        Styles.flex,
        flexDirection.column,
        alignItems.flexStart,
        padding := "5px",

        b(
          "Select a view:",
        ),


        Rx {
          val currentViews = existingViews()
          val possibleViews = viewDefs.filterNot(currentViews.contains)
          possibleViews.map { view =>
            val info = viewToTabInfo(view, 0, 0, 0)
            div(
              marginTop := "8px",
              cls := "ui button compact mini",
              Elements.icon(info.icon),
              view.toString,
              onClick.stopPropagation.foreach(addNewView(view)),
              cursor.pointer
            )
          }
        }
      ),

      div(
        Styles.flex,
        flexDirection.column,
        alignItems.center,
        width := "100%",
        marginTop := "20px",
        padding := "5px",

        b(
          "Current views:",
          alignSelf.flexStart
        ),

        Rx {
          val currentViews = existingViews()
          if (currentViews.isEmpty) div("Nothing, yet.")
          else Components.removeableList(currentViews, removeSink = Sink.fromFunction(removeView)) { view =>
            val info = viewToTabInfo(view, 0, 0, 0)
            VDomModifier(
              marginTop := "8px",
              div(
                cls := "ui button primary compact mini",
                Styles.flex,
                alignItems.center,
                Elements.icon(info.icon),
                view.toString,
                onClick.stopPropagation.foreach { viewAction(view) },
                cursor.pointer,
              )
            )
          }
        },

        Rx {
          VDomModifier.ifTrue(hasViews())(
            div(
              alignSelf.flexEnd,
              marginLeft := "auto",
              marginTop := "10px",
              cls := "ui button compact mini",
              "Reset to default",
              cursor.pointer,
              onClick.stopPropagation.foreach { resetViews() }
            )
          )
        }
      )
    )
  }
}
