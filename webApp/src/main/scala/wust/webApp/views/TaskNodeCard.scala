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
    showNestedInputFields: Boolean = true,
    isCompact: Boolean = false,
    compactChildren: Boolean = true,
    isProperty: Boolean = false,
    dragTarget: NodeId => DragTarget = DragItem.Task.apply,
    dragPayload: NodeId => DragPayload = DragItem.Task.apply,
  ): VNode = div.thunkStatic(nodeId.toStringFast)(Ownable { implicit ctx =>

    val nodeIdx = GlobalState.graph.map(_.idToIdxOrThrow(nodeId)) // TODO: these actually crash!
    val parentIdx = GlobalState.graph.map(_.idToIdxOrThrow(traverseState.parentId))
    val node = Rx {
      GlobalState.graph().nodes(nodeIdx())
    }
    val isDeletedNow = Rx {
      if( isProperty ) false
      else GlobalState.graph().isDeletedNowIdx(nodeIdx(), parentIdx())
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

      val largerOnMobile = VDomModifier.ifTrue(BrowserDetect.isMobile)(fontSize := "24px", paddingTop := "5px", color := "#D9D9D9", backgroundColor := Colors.nodecardBg)
      def expand = menuItem(
        "Expand", "Expand", Icons.expand,
        VDomModifier(
          onClick.stopPropagation.foreach{
            val changes = GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), GlobalState.user.now.id)
            GlobalState.submitChanges(changes)

            focusState.view match {
              case View.List => FeatureState.use(Feature.ExpandTaskInChecklist)
              case View.Kanban => FeatureState.use(Feature.ExpandTaskInKanban)
              case _ =>
            }
          },
          largerOnMobile
        )
      )

      def collapse = menuItem(
        "Collapse", "Collapse", Icons.collapse,
        VDomModifier(
          onClick.stopPropagation.useLazy(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(false), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes,
          largerOnMobile
        )
      )

      val toggleExpand = Rx {
        if (isExpanded()) collapse else expand
      }

      div(
        cls := "buttonbar",
        VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
        DragComponents.drag(DragItem.DisableDrag),
        Styles.flex,
        toggleExpand,
        VDomModifier.ifTrue(!BrowserDetect.isMobile && !isProperty)(toggleDelete)
      )
    }

    VDomModifier(
      Components.sidebarNodeFocusMod(nodeId, focusState),
      Components.showHoveredNode( nodeId),
      UnreadComponents.readObserver( nodeId, VDomModifier(
        marginTop := "7px",
        marginLeft := "5px",
        marginRight := (if (BrowserDetect.isMobile) "35px" else "5px"), //TODO: better? leave room for buttonbar to not overlay
      )),
      VDomModifier.ifTrue(showCheckbox)(
        node.map(Components.taskCheckbox( _, traverseState.parentId :: Nil).apply(float.left, marginRight := "5px"))
      ),

      node.map { node =>
        Components.nodeCardMod(
          node,
          maxLength = Some(maxLength),
          contentInject = VDomModifier(
            VDomModifier.ifNot(showCheckbox)(marginLeft := "2px"),
            VDomModifier.ifTrue(isDone)(textDecoration.lineThrough),
            VDomModifier.ifTrue(inOneLine)(alignItems.flexStart, NodeDetails.tagsPropertiesAssignments(focusState, traverseState, nodeId), marginRight := "40px"), // marginRight to not interfere with button bar...
          ),
          nodeInject = VDomModifier.ifTrue(inOneLine)(marginRight := "10px"),
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
        if(isCompact) VDomModifier(
          marginLeft := s"${CommonStyles.taskPaddingCompactPx}px",
        )
        else VDomModifier(
          marginLeft := s"${CommonStyles.taskPaddingPx}px"
        ),
        alignItems.center,
        NodeDetails.tagsPropertiesAssignments(focusState, traverseState, nodeId),
      )),
      NodeDetails.cardFooter(nodeId, childStats, isExpanded, focusState, isCompact = isCompact),
      NodeDetails.nestedItemsList(
        nodeId = nodeId,
        isExpanded = isExpanded,
        focusState = focusState,
        traverseState = traverseState,
        isCompact = isCompact || compactChildren,
        inOneLine = inOneLine,
        showNestedInputFields = showNestedInputFields,
      ),

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "0px", right := "0px"), // distance to not interefere with sidebar-focus box-shadow around node
    )
  })

}
