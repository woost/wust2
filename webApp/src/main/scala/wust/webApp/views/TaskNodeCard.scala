package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids.{Feature, _}
import wust.sdk.Colors
import wust.webApp.Icons
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.state.{FeatureState, FocusState, GlobalState, TraverseState}
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, Ownable, UI}

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

    val childStats = Rx { NodeDetails.ChildStats.from(nodeIdx(), GlobalState.graph()) }

    val buttonBar = {
      /// @return a Builder for a menu item which takes a boolean specifying whether it should be compressed or not
      def menuItem(shortName : String,
        longDesc : String,
        icon : VDomModifier,
        modifier : VDomModifier) = {
        div(
          cls := "item",
          span(cls := "icon", icon),
          modifier,
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
        val stageParents = graph.stageParentsIdx.collect(nodeIdx.now) { case p if graph.workspacesForParent(p).contains(focusedIdx) => graph.nodeIds(p) }
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
      val toggleDelete = Rx {
        val text = if (isDeletedNow()) "Recover" else "Archive"
        menuItem(
          text, text, if (isDeletedNow()) Icons.undelete else Icons.delete,
          onClick.stopPropagation foreach toggleDeleteClickAction()
        )
      }

      div(
        cls := "buttonbar",
        VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
        DragComponents.drag(DragItem.DisableDrag),
        Styles.flex,
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
            VDomModifier.ifTrue(inOneLine)(alignItems.center, NodeDetails.tagsPropertiesAssignments(nodeId), marginRight := "40px"), // marginRight to not interfere with button bar...
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

      overflow.visible, // make edit dialogs visible even if going outside of this card

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
      NodeDetails.nestedTaskList(
        nodeId = nodeId,
        isExpanded = isExpanded,
        focusState = focusState,
        traverseState = traverseState,
        isCompact = isCompact || compactChildren,
        inOneLine = inOneLine
      ),

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "3px", right := "3px"), // distance to not interefere with sidebar-focus box-shadow around node
    )
  })

}
