package wust.webApp.views

import monix.reactive.Observer
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids._
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

  private val renderMessageCount = {
    div(
      cls := "childstat",
      Styles.flex,
      Styles.flexStatic,
      margin := "5px 5px 5px 0px",
      div(Icons.conversation, marginLeft := "5px", marginRight := "5px"),
    )
  }

  private val renderProjectsCount = {
    div(
      cls := "childstat",
      Styles.flex,
      Styles.flexStatic,
      margin := "5px 5px 5px 0px",
      div(Icons.projects, marginLeft := "5px", marginRight := "5px"),
    )
  }

  private val renderNotesCount = {
    div(
      cls := "childstat",
      Styles.flex,
      Styles.flexStatic,
      margin := "5px 5px 5px 0px",
      div(Icons.notes, marginLeft := "5px", marginRight := "5px"),
    )
  }

  private val renderTaskCount = {
    div(
      cls := "childstat",
      Styles.flex,
      Styles.flexStatic,
      margin := "5px",
      div(Icons.tasks, marginRight := "5px"),
    )
  }

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

    final case class TaskStats(messageChildrenCount: Int, taskChildrenCount: Int, noteChildrenCount: Int, taskDoneCount: Int, propertiesCount: Int, projectChildrenCount: Int) {
      @inline def progress = (100 * taskDoneCount) / taskChildrenCount
      @inline def isEmpty = messageChildrenCount == 0 && taskChildrenCount == 0 && noteChildrenCount == 0 && projectChildrenCount == 0 //&& propertiesCount == 0
      @inline def nonEmpty = !isEmpty
    }
    val taskStats = Rx {
      val graph = GlobalState.graph()

      val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx())

      val taskChildren = graph.taskChildrenIdx(nodeIdx())
      val taskChildrenCount = taskChildren.length

      val taskDoneCount = taskChildren.fold(0) { (count, childIdx) =>
        if (graph.isDone(childIdx)) count + 1 //TODO done inside this node...
        else count
      }

      val noteChildrenCount = graph.noteChildrenIdx.sliceLength(nodeIdx())

      val propertiesCount = graph.propertiesEdgeIdx.sliceLength(nodeIdx())

      val projectChildrenCount = graph.projectChildrenIdx.sliceLength(nodeIdx())

      TaskStats(messageChildrenCount, taskChildrenCount, noteChildrenCount, taskDoneCount, propertiesCount, projectChildrenCount)
    }

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
        } else {
          Elements.confirm("Delete this task?") {
            GlobalState.submitChanges(changes)
            selectedNodeIds.update(_ - nodeId)
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
        onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes)
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

    def renderTaskProgress(taskStats: TaskStats) = {
      val progress = taskStats.progress
      div(
        cls := "childstat",
        Styles.flex,
        flexGrow := 1,
        alignItems.flexEnd,
        minWidth := "40px",
        backgroundColor := "#eee",
        borderRadius := "2px",
        margin := "3px 5px",
        div(
          height := "3px",
          padding := "0",
          width := s"${math.max(progress, 0)}%",
          backgroundColor := s"${if(progress < 100) "#ccc" else "#32CD32"}",
          UI.tooltip("top right") := s"$progress% Progress. ${taskStats.taskDoneCount} / ${taskStats.taskChildrenCount} done."
        ),
      )
    }

    val propertySingle = Rx {
      val graph = GlobalState.graph()
      PropertyData.Single(graph, graph.idToIdxOrThrow(nodeId))
    }
    val propertySingleEmpty = Rx {
      propertySingle().isEmpty // optimize for empty property because properties are array and are therefore never equal
    }

    val tagsPropertiesAssignments = VDomModifier(
      Styles.flex,
      flexWrap.wrap,

      Rx {
        if (propertySingleEmpty()) VDomModifier.empty
        else VDomModifier(
          VDomModifier.ifTrue(!inOneLine)(
            Rx {
              VDomModifier.ifTrue(taskStats().isEmpty)(marginBottom := "3px")
            },
          ),

          propertySingle().info.tags.map { tag =>
            Components.removableNodeTag( tag, taggedNodeId = nodeId)
          },

          propertySingle().properties.map { property =>
            property.values.map { value =>
              VDomModifier.ifTrue(value.edge.data.showOnCard) {
                Components.removableNodeCardProperty( value.edge, value.node)
              }
            }
          },

          div(
            marginLeft.auto,
            Styles.flex,
            justifyContent.flexEnd,
            flexWrap.wrap,
            propertySingle().info.assignedUsers.map(userNode =>
              Components.removableUserAvatar( userNode, targetNodeId = nodeId)
            ),
          ),
        )
      }
    )

    val cardFooter = Rx {
      VDomModifier.ifTrue(taskStats().nonEmpty)(
        div(
          cls := "childstats",
          Styles.flex,
          alignItems.center,
          justifyContent.flexEnd,
          VDomModifier.ifTrue(taskStats().taskChildrenCount > 0)(
            div(
              flexGrow := 1,

              Styles.flex,
              renderTaskCount(
                s"${taskStats().taskDoneCount}/${taskStats().taskChildrenCount}",
              ),
              renderTaskProgress(taskStats()).apply(alignSelf.center),

              onClick.stopPropagation.mapTo {
                val edge = Edge.Expanded(nodeId, EdgeData.Expanded(!isExpanded()), GlobalState.user.now.id)
                GraphChanges(addEdges = Array(edge))
              } --> GlobalState.eventProcessor.changes,
              cursor.pointer,
            )
          ),

          VDomModifier.ifTrue(taskStats().noteChildrenCount > 0)(
            renderNotesCount(
              taskStats().noteChildrenCount,
              UI.tooltip("left center") := "Show notes",
              onClick.stopPropagation(Some(FocusPreference(nodeId, Some(View.Content)))) --> GlobalState.rightSidebarNode,
              cursor.pointer,
            ),
          ),
          VDomModifier.ifTrue(taskStats().messageChildrenCount > 0)(
            renderMessageCount(
              taskStats().messageChildrenCount,
              UI.tooltip("left center") := "Show comments",
              onClick.stopPropagation(Some(FocusPreference(nodeId, Some(View.Conversation)))) --> GlobalState.rightSidebarNode,
              cursor.pointer,
            ),
          ),
          VDomModifier.ifTrue(taskStats().projectChildrenCount > 0)(
            renderProjectsCount(
              taskStats().projectChildrenCount,
              UI.tooltip("left center") := "Show Projects",
              onClick.stopPropagation(Some(FocusPreference(nodeId, Some(View.Dashboard)))) --> GlobalState.rightSidebarNode,
              cursor.pointer,
            ),
          ),
        )
      )
    }

    VDomModifier(
      Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, nodeId),
      onDblClick.stopPropagation.foreach{ _ =>
        GlobalState.focus(nodeId)
      },
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
            VDomModifier.ifTrue(inOneLine)(alignItems.flexStart, tagsPropertiesAssignments, marginRight := "40px"), // marginRight to not interfere with button bar...
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
        alignItems.center, tagsPropertiesAssignments
      )),
      cardFooter,

      Rx {
        val graph = GlobalState.graph()
        VDomModifier.ifTrue(isExpanded())(
          ListView.fieldAndList( focusState.copy(isNested = true, focusedId = nodeId), traverseState.step(nodeId), inOneLine = inOneLine, isCompact = isCompact || compactChildren).apply(
            paddingBottom := "3px",
            onClick.stopPropagation --> Observer.empty,
            DragComponents.drag(DragItem.DisableDrag),
          ).apply(paddingLeft := "15px"),
          paddingBottom := "0px",
        )
      },

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "3px", right := "3px"), // distance to not interefere with sidebar-focus box-shadow around node
    )
  })

  //TODO: this is a less performant duplicate of renderThunk. rewrite all usages to renderThunk.
  @deprecated("Use the new thunk version instead", "")
  def render(
    
    node: Node,
    parentId: NodeId, // is either a column (stage), a parent card, or else (if the card is in inbox) equal to focusState.focusedId
    focusState: FocusState,
    selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty),
    showCheckbox:Boolean = false,
    isDone:Boolean = false,
    inOneLine: Boolean = false,
    isCompact: Boolean = false,
    compactChildren: Boolean = false,
    dragTarget: NodeId => DragTarget = DragItem.Task.apply,
    dragPayload: NodeId => DragPayload = DragItem.Task.apply,
  )(implicit ctx: Ctx.Owner): VNode = {

    val nodeIdx = GlobalState.graph.map(_.idToIdxOrThrow(node.id))
    val parentIdx = GlobalState.graph.map(_.idToIdxOrThrow(parentId))
    val isDeletedNow = Rx {
      GlobalState.graph().isDeletedNowIdx(nodeIdx(), parentIdx())
    }
    val isExpanded = Rx {
      GlobalState.graph().isExpanded(GlobalState.userId(), nodeIdx()).getOrElse(false)
    }

    final case class TaskStats(messageChildrenCount: Int, taskChildrenCount: Int, noteChildrenCount: Int, taskDoneCount: Int, propertiesCount: Int, projectChildrenCount: Int) {
      @inline def progress = (100 * taskDoneCount) / taskChildrenCount
      @inline def isEmpty = messageChildrenCount == 0 && taskChildrenCount == 0 && noteChildrenCount == 0 && projectChildrenCount == 0 //&& propertiesCount == 0
      @inline def nonEmpty = !isEmpty
    }
    val taskStats = Rx {
      val graph = GlobalState.graph()

      val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx())

      val taskChildren = graph.taskChildrenIdx(nodeIdx())
      val taskChildrenCount = taskChildren.length

      val taskDoneCount = taskChildren.fold(0) { (count, childIdx) =>
        if (graph.isDone(childIdx)) count + 1 //TODO done inside this node...
        else count
      }

      val noteChildrenCount = graph.noteChildrenIdx.sliceLength(nodeIdx())

      val propertiesCount = graph.propertiesEdgeIdx.sliceLength(nodeIdx())

      val projectChildrenCount = graph.projectChildrenIdx.sliceLength(nodeIdx())

      TaskStats(messageChildrenCount, taskChildrenCount, noteChildrenCount, taskDoneCount, propertiesCount, projectChildrenCount)
    }

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
        val hasMultipleStagesInFocusedNode = stageParents.exists(_ != parentId)
        val removeFromWorkspaces = if (hasMultipleStagesInFocusedNode) GraphChanges.empty else deleteOrUndelete(ChildId(node.id), ParentId(focusState.focusedId))

        val changes = removeFromWorkspaces merge deleteOrUndelete(ChildId(node.id), ParentId(parentId))
        GlobalState.submitChanges(changes)
        selectedNodeIds.update(_ - node.id)
      }
      def toggleDelete = {
        Rx {
          menuItem(
            if (isDeletedNow()) "Recover" else "Archive", if (isDeletedNow()) "Recover" else "Archive", if (isDeletedNow()) Icons.undelete else Icons.delete,
            onClick.stopPropagation foreach toggleDeleteClickAction()
          )
        }
      }
      def expand = menuItem(
        "Expand", "Expand", Icons.expand,
        onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(node.id, EdgeData.Expanded(true), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes)
      def collapse = menuItem(
        "Collapse", "Collapse", Icons.collapse,
        onClick.stopPropagation(GraphChanges.connect(Edge.Expanded)(node.id, EdgeData.Expanded(false), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes)
      def toggle = Rx {
        if (isExpanded()) collapse else expand
      }

      div(
        cls := "buttonbar",
        VDomModifier.ifTrue(!BrowserDetect.isMobile)(cls := "autohide"),
        DragComponents.drag(DragItem.DisableDrag),
        Styles.flex,
        toggle,
        toggleDelete
      )
    }

    def renderTaskProgress(taskStats: TaskStats) = {
      val progress = taskStats.progress
      div(
        cls := "childstat",
        Styles.flex,
        flexGrow := 1,
        alignItems.flexEnd,
        minWidth := "40px",
        backgroundColor := "#eee",
        borderRadius := "2px",
        margin := "3px 5px",
        div(
          height := "3px",
          padding := "0",
          width := s"${math.max(progress, 0)}%",
          backgroundColor := s"${if(progress < 100) "#ccc" else "#32CD32"}",
          UI.tooltip("top right") := s"$progress% Progress. ${taskStats.taskDoneCount} / ${taskStats.taskChildrenCount} done."
        ),
      )
    }

    val propertySingle = Rx {
      val graph = GlobalState.graph()
      PropertyData.Single(graph, graph.idToIdxOrThrow(node.id))
    }

    val cardDescription = VDomModifier(
      Styles.flex,
      flexWrap.wrap,

      propertySingle.map { propertySingle =>
        VDomModifier(
          VDomModifier.ifTrue(!inOneLine)(
            Rx {
              VDomModifier.ifTrue(taskStats().isEmpty)(marginBottom := "3px")
            },
          ),

          propertySingle.info.tags.map { tag =>
            Components.removableNodeTag( tag, taggedNodeId = node.id)
          },

          propertySingle.properties.map { property =>
            property.values.map { value =>
              VDomModifier.ifTrue(value.edge.data.showOnCard) {
                Components.removableNodeCardProperty( value.edge, value.node)
              }
            }
          },

          div(
            marginLeft.auto,
            Styles.flex,
            justifyContent.flexEnd,
            flexWrap.wrap,
            propertySingle.info.assignedUsers.map(userNode =>
              Components.removableUserAvatar( userNode, targetNodeId = node.id)
            ),
          ),
        )
      }
    )

    val cardFooter = div(
      cls := "childstats",
      Styles.flex,
      alignItems.center,
      justifyContent.flexEnd,
      Rx{
        VDomModifier(
          VDomModifier.ifTrue(taskStats().taskChildrenCount > 0)(
            div(
              flexGrow := 1,

              Styles.flex,
              renderTaskCount(
                s"${taskStats().taskDoneCount}/${taskStats().taskChildrenCount}",
              ),
              renderTaskProgress(taskStats()).apply(alignSelf.center),

              onClick.stopPropagation.mapTo {
                val edge = Edge.Expanded(node.id, EdgeData.Expanded(!isExpanded()), GlobalState.user.now.id)
                GraphChanges(addEdges = Array(edge))
              } --> GlobalState.eventProcessor.changes,
              cursor.pointer,
            )
          ),

          VDomModifier.ifTrue(taskStats().noteChildrenCount > 0)(
            renderNotesCount(
              taskStats().noteChildrenCount,
              UI.tooltip("left center") := "Show Notes",
              onClick.stopPropagation(Some(FocusPreference(node.id, Some(View.Content)))) --> GlobalState.rightSidebarNode,
              cursor.pointer,
            ),
          ),
          VDomModifier.ifTrue(taskStats().messageChildrenCount > 0)(
            renderMessageCount(
              taskStats().messageChildrenCount,
              UI.tooltip("left center") := "Show Comments",
              onClick.stopPropagation(Some(FocusPreference(node.id, Some(View.Conversation)))) --> GlobalState.rightSidebarNode,
              cursor.pointer,
            ),
          ),
          VDomModifier.ifTrue(taskStats().projectChildrenCount > 0)(
            renderProjectsCount(
              taskStats().projectChildrenCount,
              UI.tooltip("left center") := "Show Projects",
              onClick.stopPropagation(Some(FocusPreference(node.id, Some(View.Dashboard)))) --> GlobalState.rightSidebarNode,
              cursor.pointer,
            ),
          ),
        )
      },
    )

    Components.nodeCard(
      node,
      maxLength = Some(maxLength),
      contentInject = VDomModifier(
        VDomModifier.ifTrue(isDone)(textDecoration.lineThrough),
        VDomModifier.ifTrue(inOneLine)(alignItems.flexStart, cardDescription, marginRight := "40px"), // marginRight to not interfere with button bar...
      ),
      nodeInject = VDomModifier.ifTrue(inOneLine)(marginRight := "10px")
    ).prepend(
      Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, node.id),
      Components.showHoveredNode( node.id),
      UnreadComponents.readObserver( node.id, marginTop := "7px"),
      VDomModifier.ifTrue(showCheckbox)(
        Components.taskCheckbox( node, parentId :: Nil).apply(float.left, marginRight := "5px")
      )
    ).apply(
      Rx {
        VDomModifier.ifTrue(isDeletedNow())(cls := "node-deleted")
      },
      DragComponents.drag(payload = dragPayload(node.id), target = dragTarget(node.id)),

      keyed(node.id.toStringFast),
      // fixes unecessary scrollbar, when card has assignment
      overflow.hidden,

      VDomModifier.ifNot(inOneLine)(div(margin := "0 3px", alignItems.center, cardDescription)),
      cardFooter,

      Rx {
        val graph = GlobalState.graph()
        VDomModifier.ifTrue(isExpanded())(
          ListView.fieldAndList( focusState = focusState.copy(isNested = true, focusedId = node.id), TraverseState(node.id), inOneLine = inOneLine, isCompact = isCompact || compactChildren).apply( // TODO: proper traverstate
            paddingBottom := "3px",
            onClick.stopPropagation --> Observer.empty,
            DragComponents.drag(DragItem.DisableDrag),
          ),
          paddingBottom := "0px",
        )
      },

      position.relative, // for buttonbar
      buttonBar(position.absolute, top := "3px", right := "3px"), // distance to not interefere with sidebar-focus box-shadow around node
    )
  }
}
