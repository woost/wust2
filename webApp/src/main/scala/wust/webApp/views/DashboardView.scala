package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util.collection._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{EmojiReplacer, FeatureState, FocusState, GlobalState}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webApp.{Icons, Permission}
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, UI}

// Shows overview over a project:
// - subprojects
// - content
// - activity
object DashboardView {

  private def getProjectList(graph: Graph, focusedId: NodeId): Seq[Node] = {
    val pageParentIdx = graph.idToIdxOrThrow(focusedId)
    val directSubProjects = graph.projectChildrenIdx(pageParentIdx)
    directSubProjects.viewMap(graph.nodes).sortBy(n => EmojiReplacer.emojiAtBeginningRegex.replaceFirstIn(n.str, ""))
  }

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val segmentMod = VDomModifier(
      margin := "10px"
    )

    val projectNodes = Rx { getProjectList(GlobalState.graph(), focusState.focusedId) }

    val detailWidgets = VDomModifier(
      Styles.flex,

      //TODO: renderSubprojects mit summary
      UI.segment("Subprojects", VDomModifier(renderSubprojects(focusState), overflowX.auto)).apply(Styles.flexStatic, segmentMod),
      UI.segment("Tasks", AssignedTasksView(focusState).apply(padding := "0px")).apply(Styles.flexStatic, segmentMod),
    )

    val dashboard = if (BrowserDetect.isMobile) VDomModifier(
      flexDirection.column,
      detailWidgets
    )
    else VDomModifier(
      padding := "20px",
      minWidth := "500px",
      flexDirection.column,
      detailWidgets
    )

    div(
      Styles.growFull,
      overflow.auto,

      dashboard
    )
  }

  /// Render all subprojects as a list
  private def renderSubprojects(focusState: FocusState)(implicit ctx: Ctx.Owner): VDomModifier = {

    val projectNodes = Rx {
      val graph = GlobalState.graph()
      getProjectList(graph, focusState.focusedId).partition(node => graph.isDeletedNow(node.id, focusState.focusedId))
    }

    div(
      Styles.flex,
      justifyContent.spaceBetween,
      padding := "10px",
      alignItems.flexEnd,

      div(
        Styles.flex,
        flexDirection.column,
        flexWrap.wrap,

        Rx {
          val bothProjectNodes = projectNodes()
          val (deletedProjectNodes, undeletedProjectNodes) = bothProjectNodes
          VDomModifier(
            undeletedProjectNodes.map(renderSubproject(GlobalState.graph(), focusState, _, isDeleted = false)),
            VDomModifier.ifTrue(deletedProjectNodes.nonEmpty)(
              h4("Deleted Subprojects", color.gray),
              deletedProjectNodes.map(renderSubproject(GlobalState.graph(), focusState, _, isDeleted = true))
            )
          )
        },
        registerDragContainer
      ),

      NewProjectPrompt.newProjectButton(
        label = "+ Add Subproject",
        focusNewProject = false,
        buttonClass = "basic",
        extraChanges = nodeId => GraphChanges.connect(Edge.Child)(ParentId(focusState.focusedId), ChildId(nodeId))
      )
    )
  }

  /// Render the overview of a single (sub-) project
  private def renderSubproject(graph: Graph, focusState: FocusState, project: Node, isDeleted: Boolean)(implicit ctx: Ctx.Owner): VNode = {
    val isDeleted = graph.isDeletedNow(project.id, focusState.focusedId)
    val dispatch = GlobalState.submitChanges _
    val (headButton, tailButton) = if (isDeleted) {
      (
        VDomModifier.empty,
          button(
          marginLeft := "10px",
          "Restore",
          cls := "ui button mini compact basic",
          cursor.pointer,
          onClick.stopPropagation.useLazy(GraphChanges.connect(Edge.Child)(ParentId(focusState.focusedId), ChildId(project.id))) --> GlobalState.eventProcessor.changes
        )
      )
    } else {
      val isPinned = Rx { graph.idToIdxFold(project.id)(false)(graph.isPinned(_, userIdx = graph.idToIdxOrThrow(GlobalState.userId()))) }

      (
        div(
          marginRight := "15px",
          cursor.pointer,
          fontSize.small,
          isPinned.map[VDomModifier] {
            case true => Icons.bookmark
            case false => Icons.unbookmark
          },
          onClick.stopPropagation.useLazy(
            if (isPinned.now) GraphChanges.unpin(project.id, GlobalState.userId.now) else GraphChanges.pin(project.id, GlobalState.userId.now)
          ) --> GlobalState.eventProcessor.changes
        ),
        VDomModifier.empty
      )
    }

    val permissionLevel = Rx {
      Permission.resolveInherited(GlobalState.rawGraph(), project.id)
    }

    div(
      padding := "5px 0px 5px 0px",
      marginLeft := "10px",
      cls := "node channel-line",

      DragComponents.drag(DragItem.Project(project.id)),

      headButton,

      renderProject(project, renderNode = node => renderAsOneLineText(node).apply(cls := "channel-name"), withIcon = true),

      cursor.pointer,
      onClick foreach {
        focusState.contextParentIdAction(project.id)
        FeatureState.use(Feature.ZoomIntoProject)
      },

      permissionLevel.map(Permission.permissionIndicatorIfPublic(_, VDomModifier(fontSize := "0.7em", color.gray))),

      tailButton,

      VDomModifier.ifTrue(isDeleted)(cls := "node-deleted"),
    )
  }
}
