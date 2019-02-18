package wust.webApp.views

import clipboard.ClipboardJS
import fontAwesome._
import googleAnalytics.Analytics
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph.{Node, Edge, GraphChanges}
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


object PageHeader {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div.static(keyValue)(Ownable { implicit ctx =>
      VDomModifier(
        cls := "pageheader",

        Rx {
          val graph = state.graph()
          val page = state.page()
          val pageNode = page.parentId.flatMap(graph.nodesByIdGet)
          pageNode.map { pageNode => pageRow(state, pageNode) }
        },
      )
    })
  }

  private def pageRow(state: GlobalState, pageNode: Node)(implicit ctx: Ctx.Owner): VDomModifier = {
    val maxLength = if(BrowserDetect.isPhone) Some(30) else Some(60)

    val commonMods = VDomModifier(
      cls := "pageheader-channeltitle",
    )

    val channelTitle = NodePermission.canWrite(state, pageNode.id).flatMap { canWrite =>
      def writableMod = VDomModifier(
        borderRadius := "3px",
        Components.sidebarNodeFocusMod(state, pageNode.id)
      )

      pageNode.role match {
        case NodeRole.Message | NodeRole.Task =>
          val node =
            if(!canWrite) nodeCard(pageNode, maxLength = maxLength)
            else nodeCard(pageNode, maxLength = maxLength).apply(writableMod)
          Rx(node(commonMods))

        case _ => // usually NodeRole.Project
          Rx {
            val node =
              if(!canWrite) renderNodeData(pageNode.data, maxLength = maxLength)
              else renderNodeData(pageNode.data, maxLength = maxLength).apply(writableMod)
            node(commonMods)
          }

      }
    }


    val channelMembersList = Rx {
      val hasBigScreen = state.screenSize() != ScreenSize.Small
      hasBigScreen.ifTrue[VDomModifier](channelMembers(state, pageNode).apply(marginRight := "10px", lineHeight := "0")) // line-height:0 fixes vertical alignment
    }

    val permissionIndicator = Rx {
      val level = Permission.resolveInherited(state.graph(), pageNode.id)
      div(level.icon, UI.popup("bottom center") := level.description, zIndex := ZIndex.tooltip - 15)
    }

    div(
      paddingTop := "5px",
      paddingLeft := "5px",
      paddingRight := "10px",
      backgroundColor := BaseColors.pageBg.copy(h = hue(pageNode.id)).toHex,

      Styles.flex,
      alignItems.flexEnd,
      flexWrap.wrap,

      div(
        div(
          Styles.flex,
          alignItems.center,
          justifyContent.flexStart,
          flexGrow := 1,
          flexShrink := 2,
          padding := "0 5px",
          nodeAvatar(pageNode, size = 30)(marginRight := "5px", flexShrink := 0),
          channelTitle.map(_(marginRight := "5px")),
          Components.automatedNodesOfNode(state, pageNode),
          channelMembersList,
          permissionIndicator,
          paddingBottom := "5px",
        ),
      ),
      menu(state, pageNode).apply(alignSelf.flexEnd, marginLeft.auto),
    )
  }

  private def menu(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    val isSpecialNode = Rx {
      //TODO we should use the permission system here and/or share code with the settings menu function
      channel.id == state.user().id
    }
    val isBookmarked = PageSettingsMenu.nodeIsBookmarked(state, channel.id)

    val buttonStyle = VDomModifier(Styles.flexStatic, margin := "5px", fontSize := "20px", cursor.pointer)

    div(
      Styles.flex,
      alignItems.center,
      minWidth.auto,
      Rx {
        val hideBookmarkButton = isSpecialNode() || isBookmarked()
        hideBookmarkButton.ifFalse[VDomModifier](PageSettingsMenu.addToChannelsButton(state, channel).apply(
          Styles.flexStatic,
          marginTop := "3px",
          marginBottom := "3px",
        ))
      },
      //      notifyControl(state, channel).apply(buttonStyle),
      state.isFilterActive.map(_.ifTrue[VDomModifier] {
        div(
          Elements.icon(Icons.filter),
          color := "green",
          onClick.stopPropagation(state.defaultTransformations) --> state.graphTransformations,
          cursor.pointer,
          UI.popup("bottom right") := "A filter is active. Click to reset to default.",
        )
      }),
      viewSwitcher(state, channel.id).apply(Styles.flexStatic, alignSelf.flexEnd),
      Rx {
        PageSettingsMenu(state, channel.id).apply(buttonStyle, marginLeft := "15px")
      },
    )
  }

  def channelMembers(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexWrap.wrap,
      registerDragContainer(state),
      Rx {
        val graph = state.graph()
        val nodeIdx = graph.idToIdx(channel.id)
        val members = graph.membersByIndex(nodeIdx)

        members.map(user => div(
          Avatar.user(user.id)(
            marginLeft := "2px",
            width := "22px",
            height := "22px",
            cls := "avatar",
            marginBottom := "2px",
          ),
          Styles.flexStatic,
          cursor.grab,
          UI.popup("bottom center") := Components.displayUserName(user.data)
        )(
          drag(payload = DragItem.User(user.id)),
        ))(breakOut): js.Array[VNode]
      }
    )
  }

  //TODO FocusState?
  def viewSwitcher(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    viewSwitcher(state, channelId, state.view, view => state.urlConfig.update(_.focus(view)))
  }
  def viewSwitcher(state: GlobalState, channelId: NodeId, viewRx: Rx[View.Visible], viewAction: View => Unit)(implicit ctx: Ctx.Owner): VNode = {

    def viewToTabInfo(view: View, numMsg: Int, numTasks: Int, numFiles: Int): TabInfo = view match {
      case View.Dashboard => TabInfo(View.Dashboard, Icons.dashboard, "dashboard", 0)
      case View.Chat => TabInfo(View.Chat, Icons.chat, "messages", numMsg)
      case View.Thread => TabInfo(View.Thread, Icons.thread, "messages", numMsg)
      case View.List => TabInfo(View.List, Icons.list, "tasks", numTasks)
      case View.Kanban => TabInfo(View.Kanban, Icons.kanban, "tasks", numTasks)
      case View.Files => TabInfo(View.Files, Icons.files, "files", numFiles)
      case View.Graph => TabInfo(View.Graph, Icons.graph, "nodes", numTasks)
      case view: View.Table => TabInfo(view, Icons.table, "records", (if (view.roles.contains(NodeRole.Task)) numTasks else 0) + (if (view.roles.contains(NodeRole.Message)) numMsg else 0))
      case view => TabInfo(view, freeSolid.faSquare, "", 0) //TODO complete icon definitions
    }

    val viewDefs: List[View.Visible] =
      View.Dashboard ::
      View.Chat ::
      View.Thread ::
      View.List ::
      View.Kanban ::
      View.Files ::
      View.Graph :: 
      (if (DevOnly.isTrue) View.Table(NodeRole.Message :: Nil) :: View.Table(NodeRole.Task :: Nil) :: Nil else Nil)

    def addNewView(newView: View.Visible) = if (viewDefs.contains(newView)) { // only allow defined views
      val node = state.graph.now.nodesByIdGet(channelId)
      node.foreach { node =>
        val currentViews = node.views match {
          case None        => ViewHeuristic.bestView(state.graph.now, node) :: Nil // no definitions yet, take the current view and additionally a new one
          case Some(views) => views // just add a new view
        }

        if (!currentViews.contains(newView)) {
          val newNode = node match {
            case n: Node.Content => n.copy(views = Some(currentViews :+ newView))
            case n: Node.User    => n.copy(views = Some(currentViews :+ newView))
          }

          if (viewRx.now != newView) {
            viewAction(newView)
          }

          state.eventProcessor.changes.onNext(GraphChanges.addNode(newNode))
        }
      }
    }

    def resetViews() = {
      val node = state.graph.now.nodesByIdGet(channelId)
      node.foreach { node =>
        if (node.views.isDefined) {
          val newNode = node match {
            case n: Node.Content => n.copy(views = None)
            case n: Node.User => n.copy(views = None)
          }

          val newView = ViewHeuristic.bestView(state.graph.now, node)
          if (viewRx.now != newView) {
            viewAction(newView)
          }

          state.eventProcessor.changes.onNext(GraphChanges.addNode(newNode))
        }
      }
    }

    def removeView(view: View.Visible) = {
      val node = state.graph.now.nodesByIdGet(channelId)
      node.foreach { node =>
        val filteredViews = node.views match {
          case None        => Nil
          case Some(views) => views.filterNot(_ == view)
        }
        val newNode = node match {
          case n: Node.Content => n.copy(views = Some(filteredViews))
          case n: Node.User => n.copy(views = Some(filteredViews))
        }

        //switch to remaining view
        val newView = filteredViews.headOption.getOrElse(View.Empty)
        if (viewRx.now == view && viewRx.now != newView) {
          viewAction(newView)
        }

        state.eventProcessor.changes.onNext(GraphChanges.addNode(newNode))
      }
    }

    val node = Rx {
      state.graph().nodesById(channelId)
    }

    def addNewTabDropdown = UI.dropdownMenu(
      padding := "5px",
      div(cls := "item", display.none), //TODO ui dropdown needs at least one element

      Rx {
        val existingViews = node().views match {
          case None        => List(ViewHeuristic.bestView(state.graph(), node()))
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
                cls := "ui button mini",
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
                cls := "ui button mini",
                "Reset to default",
                cursor.pointer,
                onClick.stopPropagation.foreach { resetViews() }
              )
            }
          )
        )
      }
    ).prepend(freeSolid.faEllipsisV)

    val addNewViewTab = customTab(addNewTabDropdown, zIndex := ZIndex.overlayLow)

    viewRx.triggerLater { view => addNewView(view) }

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

        def bestView = graph.nodesByIdGet(channelId).fold[View.Visible](View.Empty)(ViewHeuristic.bestView(graph, _))

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
