package wust.webApp.views

import wust.webApp.state.FeatureState
import monix.reactive.Observer
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids.{Feature, _}
import wust.sdk.Colors
import wust.util.collection._
import wust.webApp.Icons
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.state.{FocusPreference, FocusState, GlobalState, TraverseState}
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable, UI, Elements}

object TaskNodeCard {

  val maxLength = 300 // TODO: use text-overflow:ellipsis instead.

  def renderThunk(

    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty),
    showCheckbox:Boolean = false,
    isDone:Boolean = false, //TODO decide here reactively based on parent and focusState...
    inOneLine: Boolean = false,
    isCompact: Boolean = false,
    compactChildren: Boolean = false,
    dragTarget: NodeId => DragTarget = DragItem.Task.apply,
    dragPayload: NodeId => DragPayload = DragItem.Task.apply,
  ): VNode = div.thunkStatic(nodeId.toStringFast)(Ownable { implicit ctx =>

    val nodeIdx = GlobalState.graph.map(_.idToIdxOrThrow(nodeId))
    val parentIdx = GlobalState.graph.map(_.idToIdxOrThrow(traverseState.parentId))
    val node = Rx {
      GlobalState.graph().nodes(nodeIdx())
    }
    val isDeletedNow = Rx {
      GlobalState.graph().isDeletedNowIdx(nodeIdx(), parentIdx())
    }
    val isExpanded = Rx {
      GlobalState.graph().isExpanded(GlobalState.userId(), nodeIdx()).getOrElse(false)
    }

    val childStats = Rx { NodeDetails.ChildStats.from(nodeIdx(), GlobalState.graph(), GlobalState.filteredGraph()) }

    val buttonBar = {
      /// @return a Builder for a menu item which takes a boolean specifying whether it should be compressed or not
      def menuItem(shortName : String,
        longDesc : String,
        icon : VDomModifier,
        action : VDomModifier) = {
        div(
          cls := "item",
          span(cls := "icon", icon),
          action,
          cursor.pointer,
          UI.tooltip("left center") := longDesc
        )
      }

      def deleteOrUndelete(childId: ChildId, parentId: ParentId) = {
        if (isDeletedNow.now) GraphChanges.undelete(childId, parentId) else GraphChanges.delete(childId, parentId)
      }
      def toggleDeleteClickAction(): Unit = {
        val graph = GlobalState.graph.now
        val focusedIdx = graph.idToIdxOrThrow(focusState.focusedId)
        val stageParents = graph.getRoleParentsIdx(nodeIdx.now, NodeRole.Stage).filter(graph.workspacesForParent(_).contains(focusedIdx)).viewMap(graph.nodeIds)
        val hasMultipleStagesInFocusedNode = stageParents.exists(_ != traverseState.parentId)
        val removeFromWorkspaces = if (hasMultipleStagesInFocusedNode) GraphChanges.empty else deleteOrUndelete(ChildId(nodeId), ParentId(focusState.focusedId))

        val changes = removeFromWorkspaces merge deleteOrUndelete(ChildId(nodeId), ParentId(traverseState.parentId))

        if(isDeletedNow.now) {
          GlobalState.submitChanges(changes)
          selectedNodeIds.update(_ - nodeId)
          FeatureState.use(Feature.UndeleteTaskInChecklist)
        } else {
          Elements.confirm("Delete this task?") {
            GlobalState.submitChanges(changes)
            selectedNodeIds.update(_ - nodeId)
            FeatureState.use(Feature.DeleteTaskInChecklist)
          }
        }
      }
      def toggleDelete = {
        Rx {
          val text = if (isDeletedNow()) "Recover" else "Archive"
          menuItem(
            text, text, if (isDeletedNow()) Icons.undelete else Icons.delete,
            onClick.stopPropagation foreach toggleDeleteClickAction()
          )
        }
      }
      def expand = menuItem(
        "Expand", "Expand", Icons.expand,
        onClick.stopPropagation.foreach{
          val changes = GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), GlobalState.user.now.id)
          GlobalState.submitChanges(changes)

          focusState.view match {
            case View.List => FeatureState.use(Feature.ExpandTaskInChecklist)
            case View.Kanban => FeatureState.use(Feature.ExpandTaskInKanban)
            case _ =>
          }
        }
      )

      def collapse = menuItem(
        "Collapse", "Collapse", Icons.collapse,
        onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(false), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes)
      def toggleExpand = Rx {
        @inline def largerOnMobile = VDomModifier.ifTrue(BrowserDetect.isMobile)(fontSize := "24px", paddingTop := "5px", color := "#D9D9D9", backgroundColor := Colors.nodecardBg)
        (if (isExpanded()) collapse else expand).apply(largerOnMobile)
      }

      div(
        cls := "buttonbar",
        VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
        DragComponents.drag(DragItem.DisableDrag),
        Styles.flex,
        toggleExpand,
        VDomModifier.ifTrue(!BrowserDetect.isMobile)(toggleDelete)
      )
    }

    VDomModifier(
      Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, nodeId),
      Components.showHoveredNode( nodeId),
      UnreadComponents.readObserver( nodeId, marginTop := "7px"),
      VDomModifier.ifTrue(showCheckbox)(
        node.map(Components.taskCheckbox( _, traverseState.parentId :: Nil).apply(float.left, marginRight := "5px"))
      ),

      node.map { node =>
        Components.nodeCardMod(

          node,
          maxLength = Some(maxLength),
          contentInject = VDomModifier(
            VDomModifier.ifTrue(isDone)(textDecoration.lineThrough),
            VDomModifier.ifTrue(inOneLine)(alignItems.flexStart, NodeDetails.tagsPropertiesAssignments(nodeId), marginRight := "40px"), // marginRight to not interfere with button bar...
            VDomModifier.ifNot(showCheckbox)(
              marginLeft := "2px"
            )
          ),
          nodeInject = VDomModifier.ifTrue(inOneLine)(marginRight := "10px")
        )
      },

      Rx {
        VDomModifier.ifTrue(isDeletedNow())(cls := "node-deleted")
      },

      VDomModifier.ifTrue(isDone)(opacity := 0.6),
      DragComponents.drag(payload = dragPayload(nodeId), target = dragTarget(nodeId)),

      // fixes unecessary scrollbar, when card has assignment
      overflow.hidden,

      VDomModifier.ifNot(inOneLine)(div(
        margin := "0 3px",
        marginLeft := s"${if(isCompact) CommonStyles.taskPaddingCompactPx else CommonStyles.taskPaddingPx}px",
        alignItems.center,
        NodeDetails.tagsPropertiesAssignments(nodeId),
        Rx {
          VDomModifier.ifTrue(childStats().isEmpty)(marginBottom := "3px")
        },
      )),
      NodeDetails.cardFooter(nodeId, childStats, isExpanded),

      Rx {
        val graph = GlobalState.graph()
        VDomModifier.ifTrue(isExpanded())(
          ListView.fieldAndList( focusState.copy(isNested = true, focusedId = nodeId), traverseState.step(nodeId), inOneLine = inOneLine, isCompact = isCompact || compactChildren).apply(
            paddingBottom := "3px",
            onClick.stopPropagation.discard,
            DragComponents.drag(DragItem.DisableDrag),
          ).apply(paddingLeft := "15px"),
          paddingBottom := "0px",
        )
      },

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "3px", right := "3px"), // distance to not interefere with sidebar-focus box-shadow around node
    )
  })

}
