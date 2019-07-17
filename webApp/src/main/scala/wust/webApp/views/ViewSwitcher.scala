package wust.webApp.views

import wust.css.{Styles, ZIndex}
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
  private def viewToTabInfo(view: View, numMsg: Int, numTasks: Int, numFiles: Int): TabInfo = view match {
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

  private val viewDefs: Array[View.Visible] = Array(
    View.Dashboard,
    View.List,
    View.Kanban,
    View.Table(NodeRole.Task :: Nil),
    View.Graph,
    View.Chat,
    View.Thread,
    View.Content,
    View.Files,
  // View.Gantt,
  // View.Topological,
  )

  def viewCheckboxes = multiCheckbox[View.Visible](
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
        h3("Select views:", marginBottom := "0px"),
        div("(can be changed later)", fontSize.smaller, color.gray, marginBottom := "15px"),
        modifier,
      )
    }

  def multiCheckbox[T: ClassTag](checkboxes: Array[T], description: T => VDomModifier): EmitterBuilder[Seq[T], VDomModifier] = EmitterBuilder.ofModifier { sink =>
    var checkedState = Array.fill(checkboxes.length)(false)
    val changed = PublishSubject[Unit]

    div(
      Styles.flex,
      flexDirection.column,
      fontSize.larger,
      checkboxes.mapWithIndex { (idx, value) =>
        div(
          marginLeft := "10px",
          marginBottom := "10px",
          label(
            Styles.flex,
            alignItems.center,
            input(
              tpe := "checkbox",
              onChange.checked.foreach { checked =>
                checkedState(idx) = checked
                changed.onNext(())
              },
              onClick.stopPropagation --> Observer.empty, // fix safari emitting extra click event onChange
              dsl.checked <-- changed.map(_ => checkedState(idx))
            ),
            description(value),
            cursor.pointer,
          )
        )
      },
      emitter(changed).map(_ => checkedState.flatMapWithIndex { (idx, checked) => if (checked) Array(checkboxes(idx)) else Array.empty }: Seq[T]) --> sink,
    )
  }

  //TODO FocusState?
  @inline def apply(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val currentView = Var[View](View.Empty)
    GlobalState.viewConfig
      .collect { case config if config.page.parentId.contains(channelId) => config.view }
      .foreach { currentView() = _ }
    currentView.foreach { view => GlobalState.urlConfig.update(_.focus(view)) }

    apply(channelId, currentView)
  }
  @inline def apply(channelId: NodeId, currentView: Var[View], initialView: Option[View.Visible] = None): VNode = {
    div.thunk(uniqueKey(channelId.toStringFast))(initialView)(Ownable { implicit ctx => modifier(channelId, currentView, initialView) })
  }

  def selectForm(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val currentView = Var[View](View.Empty)
    GlobalState.viewConfig
      .collect { case config if config.page.parentId.contains(channelId) => config.view }
      .foreach { currentView() = _ }
    currentView.foreach { view => GlobalState.urlConfig.update(_.focus(view)) }

    div.thunkStatic(uniqueKey(channelId.toStringFast))(Ownable { implicit ctx =>
      selector(channelId, currentView, None, Observer.empty)
    })
  }

  private def modifier(channelId: NodeId, currentView: Var[View], initialView: Option[View.Visible])(implicit ctx: Ctx.Owner): VDomModifier = {
    val closeDropdown = PublishSubject[Unit]

    def addNewTabDropdown = div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
      VDomModifier(
        div(freeSolid.faPlus, fontSize := "16px", color := Colors.pageHeaderControl, paddingLeft := "2px", paddingRight := "2px"),
        UI.dropdownMenu(VDomModifier(
          padding := "5px",
          div(cls := "item", display.none), //TODO ui dropdown needs at least one element

          selector(channelId, currentView, initialView, closeDropdown)
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
          singleTab(currentView, viewToTabInfo(view, numMsg = numMsg, numTasks = numTasks, numFiles = numFiles))
        }

        viewTabs :+ addNewViewTab
      },

    )
  }

  private def selector(
    channelId: NodeId,
    currentView: Var[View],
    initialView: Option[View.Visible],
    done: Observer[Unit]
  )(implicit ctx: Ctx.Owner): VDomModifier = {

    val nodeRx = Rx {
      GlobalState.graph().nodesById(channelId)
    }
    val existingViews = Rx {
      val node = nodeRx()
      node.fold(List.empty[View.Visible]) { node =>
        node.views match {
          case None        => ViewHeuristic.bestView(GlobalState.graph(), node, GlobalState.user().id).toList
          case Some(views) => views
        }
      }
    }

    val hasViews = Rx {
      nodeRx().fold(false)(_.views.isDefined)
    }

    //TODO rewrite this in a less sideeffecting way
    currentView.triggerLater { view => addNewView(currentView, done, nodeRx, existingViews, view.asInstanceOf[View.Visible]) }
    initialView.foreach(addNewView(currentView, done, nodeRx, existingViews, _))

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
              onClick.stopPropagation.foreach(addNewView(currentView, done, nodeRx, existingViews, view)),
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
          else Components.removeableList(currentViews, removeSink = Sink.fromFunction(removeView(currentView, done, nodeRx, _))) { view =>
            val info = viewToTabInfo(view, 0, 0, 0)
            VDomModifier(
              marginTop := "8px",
              div(
                cls := "ui button primary compact mini",
                Styles.flex,
                alignItems.center,
                Elements.icon(info.icon),
                view.toString,
                onClick.stopPropagation.foreach { currentView() = view },
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
              onClick.stopPropagation.foreach { resetView(currentView, done, nodeRx) }
            )
          )
        }
      )
    )
  }
  private def resetView(currentView: Var[View], done: Observer[Unit], nodeRx: Rx[Option[Node]]): Unit = {
    done.onNext(())
    val node = nodeRx.now
    node.foreach { node =>
      if (node.views.isDefined) {
        val newNode = node match {
          case n: Node.Content => n.copy(views = None)
          case n: Node.User    => n.copy(views = None)
        }

        val newView = ViewHeuristic.bestView(GlobalState.graph.now, node, GlobalState.user.now.id).getOrElse(View.Empty)
        if (currentView.now != newView) {
          currentView() = newView
        }

        GlobalState.submitChanges(GraphChanges.addNode(newNode))
      }
    }
  }
  private def removeView(currentView: Var[View], done: Observer[Unit], nodeRx: Rx[Option[Node]], view: View.Visible): Unit = {
    done.onNext(())
    val node = nodeRx.now
    node.foreach { node =>
      val currentViews = node.views.getOrElse(Nil)
      val filteredViews = currentViews.filterNot(_ == view)
      val newNode = node match {
        case n: Node.Content => n.copy(views = Some(filteredViews))
        case n: Node.User    => n.copy(views = Some(filteredViews))
      }

      //switch to remaining view
      if (currentView.now == view) {
        val currPosition = currentViews.indexWhere(_ == view)
        val nextPosition = currPosition - 1
        val newView = if (nextPosition < 0) filteredViews.headOption.getOrElse(View.Empty) else filteredViews(nextPosition)
        currentView() = newView
      }

      GlobalState.submitChanges(GraphChanges.addNode(newNode))
    }
  }
  private def addNewView(currentView: Var[View], done: Observer[Unit], nodeRx: Rx[Option[Node]], existingViews: Rx[List[View.Visible]], newView: View.Visible): Unit = {
    if (viewDefs.contains(newView)) { // only allow defined views
      done.onNext(())
      val node = nodeRx.now
      node.foreach { node =>
        val currentViews = existingViews.now

        if (!currentViews.contains(newView)) {
          val newNode = node match {
            case n: Node.Content => n.copy(views = Some(currentViews :+ newView))
            case n: Node.User    => n.copy(views = Some(currentViews :+ newView))
          }

          GlobalState.submitChanges(GraphChanges.addNode(newNode))
        }

        if (currentView.now != newView) {
          currentView() = newView
        }
      }
    }
  }
}
