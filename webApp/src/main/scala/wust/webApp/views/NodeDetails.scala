package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.Icons
import wust.webApp.state.{FocusPreference, GlobalState, FocusState, TraverseState}
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._
import wust.webApp.state.FeatureState

object NodeDetails {

  def tagsPropertiesAssignments(focusState: FocusState, traverseState: TraverseState, nodeId: NodeId)(implicit ctx: Ctx.Owner) = {
    val propertySingle = Rx {
      val graph = GlobalState.graph()
      PropertyData.Single(graph, graph.idToIdxOrThrow(nodeId))
    }
    val propertySingleEmpty = Rx {
      propertySingle().isEmpty // optimize for empty property because properties are array and are therefore never equal
    }

    VDomModifier(
      Styles.flex,
      flexWrap.wrap,

      Rx {
        if (propertySingleEmpty()) VDomModifier.empty
        else VDomModifier(

          propertySingle().info.tags.map { tag =>
            Components.removableNodeTag(tag, taggedNodeId = nodeId)
          },

          propertySingle().properties.map { property =>
            property.values.map { value =>
              VDomModifier.ifTrue(value.edge.data.showOnCard) {
                Components.nodeCardProperty(focusState, traverseState, value.edge, value.node)
              }
            }
          },

          div(
            marginLeft.auto,
            Styles.flex,
            justifyContent.flexEnd,
            flexWrap.wrap,
            propertySingle().info.assignedUsers.map(userNode =>
              Components.removableUserAvatar(userNode, targetNodeId = nodeId)),
          ),
        )
      }
    )
  }

  final case class ChildStats(messageChildrenCount: Int, taskChildrenCount: Int, noteChildrenCount: Int, taskDoneCount: Int, propertiesCount: Int, projectChildrenCount: Int) {
    @inline def progress = (100 * taskDoneCount) / taskChildrenCount
    @inline def isEmpty = messageChildrenCount == 0 && taskChildrenCount == 0 && noteChildrenCount == 0 && projectChildrenCount == 0 //&& propertiesCount == 0
    @inline def nonEmpty = !isEmpty
  }

  object ChildStats {
    def from(nodeIdx: Int, graph: Graph): ChildStats = {
      val messageChildrenCount = graph.messageChildrenIdx.sliceLength(nodeIdx)

      val taskChildren = graph.taskChildrenIdx(nodeIdx)
      val taskChildrenCount = taskChildren.length

      val taskDoneCount = taskChildren.fold(0) { (count, childIdx) =>
        if (graph.isDone(childIdx)) count + 1 //TODO done inside this node...
        else count
      }

      val noteChildrenCount = graph.noteChildrenIdx.sliceLength(nodeIdx)
      val propertiesCount = graph.propertiesEdgeIdx.sliceLength(nodeIdx)
      val projectChildrenCount = graph.projectChildrenIdx.sliceLength(nodeIdx)

      ChildStats(messageChildrenCount, taskChildrenCount, noteChildrenCount, taskDoneCount, propertiesCount, projectChildrenCount)
    }
  }

  def renderTaskProgress(taskStats: ChildStats) = {
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
        backgroundColor := s"${if (progress < 100) "#ccc" else "#32CD32"}",
        UI.tooltip("top right") := s"$progress% Progress. ${taskStats.taskDoneCount} / ${taskStats.taskChildrenCount} done."
      ),
    )
  }

  def cardFooter(nodeId: NodeId, taskStats: Rx[NodeDetails.ChildStats], isExpanded: Rx[Boolean], focusState: FocusState)(implicit ctx: Ctx.Owner) = Rx {
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
            NodeDetails.renderTaskProgress(taskStats()).apply(alignSelf.center),

            onClick.stopPropagation.useLazy {
              val edge = Edge.Expanded(nodeId, EdgeData.Expanded(!isExpanded()), GlobalState.user.now.id)
              GraphChanges(addEdges = Array(edge))
            } --> GlobalState.eventProcessor.changes,
            onClick.stopPropagation.foreach {
              if(isExpanded.now) {
                focusState.view match {
                  case View.List => FeatureState.use(Feature.ExpandTaskInChecklist)
                  case View.Kanban => FeatureState.use(Feature.ExpandTaskInKanban)
                  case _ =>
                }
              }
            },
            cursor.pointer,
          )
        ),

        VDomModifier.ifTrue(taskStats().noteChildrenCount > 0)(
          renderNotesCount(
            taskStats().noteChildrenCount,
            UI.tooltip("left center") := "Show notes",
            onClick.stopPropagation.useLazy(Some(FocusPreference(nodeId, Some(View.Content)))) --> GlobalState.rightSidebarNode,
            cursor.pointer,
          ),
        ),
        VDomModifier.ifTrue(taskStats().messageChildrenCount > 0)(
          renderMessageCount(
            taskStats().messageChildrenCount,
            UI.tooltip("left center") := "Show comments",
            onClick.stopPropagation.useLazy(Some(FocusPreference(nodeId, Some(View.Conversation)))) --> GlobalState.rightSidebarNode,
            cursor.pointer,
          ),
        ),
        VDomModifier.ifTrue(taskStats().projectChildrenCount > 0)(
          renderProjectsCount(
            taskStats().projectChildrenCount,
            UI.tooltip("left center") := "Show Projects",
            onClick.stopPropagation.useLazy(Some(FocusPreference(nodeId, Some(View.Dashboard)))) --> GlobalState.rightSidebarNode,
            cursor.pointer,
          ),
        ),
      )
    )
  }

  def nestedTaskList(nodeId: NodeId, isExpanded:Rx[Boolean], focusState:FocusState, traverseState:TraverseState, isCompact:Boolean = false, inOneLine:Boolean = false)(implicit ctx: Ctx.Owner) = Rx {
    val graph = GlobalState.graph()
    VDomModifier.ifTrue(isExpanded())(
      ListView.fieldAndList( focusState.copy(isNested = true, focusedId = nodeId),  traverseState.step(nodeId), inOneLine = inOneLine, isCompact = isCompact, showInputField = false).apply(
        paddingBottom := "3px",
        onClick.stopPropagation.discard,
        DragComponents.drag(DragItem.DisableDrag),
      ).apply(paddingLeft := "15px"),
      paddingBottom := "0px",
    )
  }

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
}
