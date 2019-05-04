package wust.webApp.views

import cats.effect.IO
import clipboard.ClipboardJS
import fontAwesome._
import googleAnalytics.Analytics
import monix.reactive.Observable
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
    View.Chat,
    View.Content,
    View.Kanban,
    View.Table(NodeRole.Task :: Nil),
    View.Thread,
    View.Files,
    View.Graph,
    // View.Gantt,
    View.Topological,
  )

  def viewCheckboxes = {
    multiCheckbox[View.Visible](
      viewDefs,
      view => span(
        marginLeft := "4px",
        viewToTabInfo(view, 0, 0, 0).icon,
        span(marginLeft := "4px", view.toString)
      )
    )
  }

  case class Checkbox(icon: IconDefinition, description: String)
  def multiCheckbox[T](checkboxes: Array[T], description: T => VDomModifier): EmitterBuilder[Seq[T], VDomModifier] = EmitterBuilder.ofModifier { sink =>
    var checkedState = Array.fill(checkboxes.length)(false)
    val changed = PublishSubject[Unit]

    div(
      cls := "ui segment",
      width := "100%",
      h2("Select views:"),
      div(
        Styles.flex,
        flexDirection.column,
        fontSize.larger,
        checkboxes.zipWithIndex.map {
          case (value, idx) =>
            div(
              marginLeft := "10px",
              marginBottom := "10px",
              label(
                Styles.flex,
                alignItems.center,
                input(
                  tpe := "checkbox",
                  onInput.checked.foreach { checked =>
                    checkedState(idx) = checked
                    changed.onNext(())
                  },
                  dsl.checked <-- changed.map(_ => checkedState(idx))
                ),
                description(value),
                cursor.pointer,
              )
            )
        },
      ),
      div(width := "100%", fontSize.smaller, textAlign.right, color.gray, "(can be changed later)"),
      emitter(changed).map(_ => checkedState.zipWithIndex.flatMap { case (checked, idx) => if (checked) Some(checkboxes(idx)) else None }) --> sink,
    )
  }


  //TODO FocusState?
  def apply(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    apply(state, channelId, state.view, view => state.urlConfig.update(_.focus(view)))
  }
  def apply(state: GlobalState, channelId: NodeId, viewRx: Rx[View.Visible], viewAction: View => Unit, initialView: Option[View.Visible] = None)(implicit ctx: Ctx.Owner): VNode = {
    val closeDropdown = PublishSubject[Unit]

    def addNewView(newView: View.Visible) = if (viewDefs.contains(newView)) { // only allow defined views
      closeDropdown.onNext(())
      val node = state.graph.now.nodesByIdGet(channelId)
      node.foreach { node =>
        val currentViews = node.views match {
          case None        => ViewHeuristic.bestView(state.graph.now, node).toList // no definitions yet, take the current view and additionally a new one
          case Some(views) => views // just add a new view
        }

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
    }

    def resetViews() = {
      closeDropdown.onNext(())
      val node = state.graph.now.nodesByIdGet(channelId)
      node.foreach { node =>
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
    }

    def removeView(view: View.Visible) = {
      closeDropdown.onNext(())
      val node = state.graph.now.nodesByIdGet(channelId)
      node.foreach { node =>
        val existingViews = node.views.getOrElse(Nil)
        val filteredViews = existingViews.filterNot(_ == view)
        val newNode = node match {
          case n: Node.Content => n.copy(views = Some(filteredViews))
          case n: Node.User => n.copy(views = Some(filteredViews))
        }

        //switch to remaining view
        if (viewRx.now == view) {
          val currPosition = existingViews.indexWhere(_ == view)
          val nextPosition = currPosition - 1
          val newView = if (nextPosition < 0) filteredViews.headOption.getOrElse(View.Empty) else filteredViews(nextPosition)
          viewAction(newView)
        }

        state.eventProcessor.changes.onNext(GraphChanges.addNode(newNode))
      }
    }

    val node = Rx {
      state.graph().nodesById(channelId)
    }

    def addNewTabDropdown = div(
      div(freeSolid.faEllipsisV, padding := "5px 10px 5px 10px"),
      UI.dropdownMenu(VDomModifier(
        padding := "5px",
        div(cls := "item", display.none), //TODO ui dropdown needs at least one element

        Rx {
          val existingViews = node().views match {
            case None        => ViewHeuristic.bestView(state.graph(), node()).toList
            case Some(views) => views
          }
          val possibleViews = viewDefs.filterNot(existingViews.contains)

          VDomModifier(
            div(
              Styles.flex,
              flexDirection.column,
              alignItems.flexEnd,
              possibleViews.map { view =>
                val info = viewToTabInfo(view, 0, 0, 0)
                div(
                  marginBottom := "5px",
                  cls := "ui button compact mini",
                  Elements.icon(info.icon),
                  view.toString,
                  onClick.stopPropagation.foreach(addNewView(view)),
                  cursor.pointer
                )
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

              if (existingViews.isEmpty) div("Nothing, yet.")
              else Components.removeableList(existingViews, removeSink = Sink.fromFunction(removeView)) { view =>
                val info = viewToTabInfo(view, 0, 0, 0)
                VDomModifier(
                  marginTop := "8px",
                  div(
                    Styles.flex,
                    alignItems.center,
                    Elements.icon(info.icon),
                    view.toString
                  )
                )
              },

              node().views.map { _ =>
                div(
                  alignSelf.flexEnd,
                  marginLeft := "auto",
                  marginTop := "10px",
                  cls := "ui button compact mini",
                  "Reset to default",
                  cursor.pointer,
                  onClick.stopPropagation.foreach { resetViews() }
                )
              }
            )
          )
        }
      ), close = closeDropdown, dropdownModifier = cls := "top left")
    )

    val addNewViewTab = customTab(addNewTabDropdown, zIndex := ZIndex.overlayLow).apply(padding := "0px")

    viewRx.triggerLater { view => addNewView(view) }
    initialView.foreach(addNewView(_))

    div(
      marginLeft := "5px",
      Styles.flex,
      justifyContent.center,
      alignItems.flexEnd,
      minWidth.auto,

      Rx {
        val currentView = viewRx()
        val pageStyle = PageStyle.ofNode(Some(channelId))
        val graph = state.graph()
        val channelNode = graph.nodesByIdGet(channelId)

        def bestView = graph.nodesByIdGet(channelId).flatMap(ViewHeuristic.bestView(graph, _)).getOrElse(View.Empty)

        val (numMsg, numTasks, numFiles) = if(!BrowserDetect.isPhone) {
          val nodeIdx = graph.idToIdx(channelId)
          val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)
          val taskChildrenCount = graph.taskChildrenIdx.sliceLength(nodeIdx)
          val filesCount = graph.pageFiles(channelId).length
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
}
