package wust.webApp.views

import wust.webUtil.Elements.onClickDefault
import wust.sdk.{ Colors, NodeColor, BaseColors }
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._
import wust.webApp.StagingOnly
import wust.css.Styles
import wust.graph.{ GraphChanges, Node }
import wust.ids.{ Feature, _ }
import wust.webApp.state._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Elements, Ownable }

object ViewModificationMenu {
  def selectForm(channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    div.thunkStatic(uniqueKey(channelId.toStringFast))(Ownable { implicit ctx =>
      val currentView = Var[View](GlobalState.viewConfig.now.view)
      GlobalState.viewConfig.triggerLater { config =>
        if (currentView.now != config.view) currentView() = config.view
      }
      currentView.triggerLater { view =>
        if (view != GlobalState.viewConfig.now.view) GlobalState.urlConfig.update(_.focus(view))
      }

      selector(channelId, currentView, None, SinkObserver.empty)
    })
  }

  def selector(
    channelId: NodeId,
    currentView: Var[View],
    initialView: Option[View.Visible],
    done: SinkObserver[Unit]
  )(implicit ctx: Ctx.Owner): VDomModifier = {

    val nodeRx = Rx {
      GlobalState.graph().nodesByIdAs[Node.Content](channelId)
    }
    val existingViews = Rx {
      val node = nodeRx()
      node.fold(List.empty[View.Visible]) { node =>
        node.views match {
          case None        => ViewHeuristic.bestView(GlobalState.graph(), node, GlobalState.userId()).toList
          case Some(views) => views
        }
      }
    }

    //TODO rewrite this in a less sideeffecting way
    initialView.foreach(addNewView(currentView, done, nodeRx, existingViews, _))
    currentView.triggerLater { view => addNewView(currentView, done, nodeRx, existingViews, view.asInstanceOf[View.Visible]) }

    val stagingOnlyViews = List(View.Calendar)
    val selectableList = {
      if(StagingOnly.isTrue)
        View.selectableList
      else
        View.selectableList.diff(stagingOnlyViews)
    }

    VDomModifier(
      div(
        Styles.flex,
        flexDirection.column,
        alignItems.flexStart,
        padding := "5px",

        div(
          width := "100%",
          Styles.flex,
          b("Add a view:"),
          Rx{ nodeRx().map(node => ColorMenu.menuIcon(BaseColors.pageBg, node).apply(marginLeft.auto)) },
        ),

        Rx {
          val currentViews = existingViews()
          val possibleViews = selectableList.filterNot(currentViews.contains)
          possibleViews.map { view =>
            val info = ViewSwitcher.viewToTabInfo(view, 0, 0, 0)
            div(
              marginTop := "8px",
              cls := "ui basic button compact mini",
              Elements.icon(info.icon),
              view.toString,
              onClickDefault.foreach{
                addNewView(currentView, done, nodeRx, existingViews, view)
                trackAddViewFeature(view)
              },
              cursor.pointer
            )
          }
        }
      ),

      Rx {
        val currentViews = existingViews()
        VDomModifier.ifTrue(currentViews.nonEmpty)(
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

            Components.removeableList(currentViews, removeSink = SinkObserver.create(removeView(currentView, done, nodeRx, _))) { view =>
              val info = ViewSwitcher.viewToTabInfo(view, 0, 0, 0)
              VDomModifier(
                marginTop := "8px",
                div(
                  fontSize.medium,
                  Styles.flex,
                  alignItems.center,
                  div(cls := "fa-fw", info.icon, marginRight := "0.5em"),
                  view.toString,
                )
              )
            },

            div(
              marginTop := "10px",
              fontSize.small,
              opacity := 0.5,
              "Removing a view will not delete its data.",
              alignSelf.flexStart
            ),
          )
        )
      },
    )
  }

  private def removeView(currentView: Var[View], done: SinkObserver[Unit], nodeRx: Rx[Option[Node]], view: View.Visible): Unit = {
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

  //TODO: gets triggered 3 times when adding a view. Should only trigger once
  private def addNewView(currentView: Var[View], done: SinkObserver[Unit], nodeRx: Rx[Option[Node]], existingViews: Rx[List[View.Visible]], newView: View.Visible): Unit = {
    scribe.info(s"ViewModificationMenu.addNewView($newView)")
    if (View.selectableList.contains(newView)) { // only allow defined views
      done.onNext(())
      // Introduce async boundary: close dropdown before applying change (feels snappier)
      Elements.defer {
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
