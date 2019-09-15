package wust.webApp.views

import wust.css.{ Styles, ZIndex }
import flatland._
import fontAwesome._
import outwatch.reactive._
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.graph.{ GraphChanges, Node }
import wust.ids.{ Feature, _ }
import wust.sdk.Colors
import wust.webApp._
import wust.webApp.state._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, Elements, Ownable, UI }

import scala.reflect.ClassTag

object ViewModificationMenu {
  def selectForm(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val currentView = Var[View](View.Empty)
    GlobalState.viewPage
      .foreach({
        case config if config.page.parentId.contains(channelId) => currentView() = config.view
        case _ => ()
      }: ViewPage => Unit)
    currentView.triggerLater { view => GlobalState.urlConfig.update(_.focus(view)) }

    div.thunkStatic(uniqueKey(channelId.toStringFast))(Ownable { implicit ctx =>
      selector(channelId, currentView, None, SinkObserver.empty)
    })
  }

  def selector(
    channelId: NodeId,
    currentView: Var[View],
    initialView: Option[View],
    done: SinkObserver[Unit]
  )(implicit ctx: Ctx.Owner): VDomModifier = {

    val nodeRx = Rx {
      GlobalState.graph().nodesById(channelId)
    }
    val existingViews = Rx {
      val node = nodeRx()
      node.fold(List.empty[View]) { node =>
        node.schema.views match {
          case None        => ViewHeuristic.bestView(GlobalState.graph(), node, GlobalState.userId()).toList
          case Some(views) => views.map(_.view)
        }
      }
    }

    val hasViews = Rx {
      nodeRx().fold(false)(_.views.isDefined)
    }

    //TODO rewrite this in a less sideeffecting way
    currentView.triggerLater { view => addNewView(currentView, done, nodeRx, existingViews, view.asInstanceOf[View]) }
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
          val possibleViews = View.selectableList.filterNot(currentViews.contains)
          possibleViews.map { view =>
            val info = ViewSwitcher.viewToTabInfo(view, 0, 0, 0)
            div(
              marginTop := "8px",
              cls := "ui button compact mini",
              Elements.icon(info.icon),
              view.toString,
              onClick.stopPropagation.foreach{
                addNewView(currentView, done, nodeRx, existingViews, view)
                trackAddViewFeature(view)
              },
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
          else Components.removeableList(currentViews, removeSink = SinkObserver.create(removeView(currentView, done, nodeRx, _))) { view =>
            val info = ViewSwitcher.viewToTabInfo(view, 0, 0, 0)
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
  private def resetView(currentView: Var[View], done: SinkObserver[Unit], nodeRx: Rx[Option[Node]]): Unit = {
    done.onNext(())
    val node = nodeRx.now
    node.foreach { node =>
      if (node.views.isDefined) {
        val newNode = node match {
          case n: Node.Content => n.copy(schema = n.schema.copy(views = None))
          case n: Node.User    => n.copy(schema = n.schema.copy(views = None))
        }

        val newView = ViewHeuristic.bestView(GlobalState.graph.now, node, GlobalState.user.now.id).getOrElse(View.Empty)
        if (currentView.now != newView) {
          currentView() = newView
        }

        GlobalState.submitChanges(GraphChanges.addNode(newNode))
      }
    }
  }
  private def removeView(currentView: Var[View], done: SinkObserver[Unit], nodeRx: Rx[Option[Node]], view: View): Unit = {
    done.onNext(())
    val node = nodeRx.now
    node.foreach { node =>
      val currentViews = node.views.getOrElse(Nil)
      val filteredViews = currentViews.filterNot(_.view == view)
      val newNode = node match {
        case n: Node.Content => n.copy(schema = n.schema.copy(views = Some(filteredViews)))
        case n: Node.User    => n.copy(schema = n.schema.copy(views = Some(filteredViews)))
      }

      //switch to remaining view
      if (currentView.now == view) {
        val currPosition = currentViews.indexWhere(_ == view)
        val nextPosition = currPosition - 1
        val newView:NodeView = if (nextPosition < 0) filteredViews.headOption.getOrElse(NodeView(View.Empty)) else filteredViews(nextPosition)
        currentView() = newView.view
      }

      GlobalState.submitChanges(GraphChanges.addNode(newNode))
    }
  }

  //TODO: gets triggered 3 times when adding a view. Should only trigger once
  private def addNewView(currentView: Var[View], done: SinkObserver[Unit], nodeRx: Rx[Option[Node]], existingViews: Rx[List[View]], newView: View): Unit = {
    scribe.info(s"ViewModificationMenu.addNewView($newView)")
    if (View.selectableList.contains(newView)) { // only allow defined views
      done.onNext(())
      val node = nodeRx.now
      node.foreach { node =>
        val currentViews = existingViews.now

        if (!currentViews.contains(newView)) {
          val newViews = currentViews :+ newView
          val newNode = node match {
            case n: Node.Content => n.copy(schema = n.schema.replaceViews(newViews))
            case n: Node.User    => n.copy(schema = n.schema.replaceViews(newViews))
          }

          GlobalState.submitChanges(GraphChanges.addNode(newNode))
        }

        if (currentView.now != newView) {
          currentView() = newView
        }
      }

    }
  }

  def trackAddViewFeature(view: View): Unit = {
    view match {
      case View.List      => FeatureState.use(Feature.AddChecklistView)
      case View.Chat      => FeatureState.use(Feature.AddChatView)
      case View.Kanban    => FeatureState.use(Feature.AddKanbanView)
      case View.Content   => FeatureState.use(Feature.AddNotesView)
      case View.Dashboard => FeatureState.use(Feature.AddDashboardView)
      case other          =>
    }
  }
}
