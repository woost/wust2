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
import wust.webApp.state.{ EmojiReplacer, FeatureState, FocusState, GlobalState, ScreenSize }
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webApp.{ Icons, Permission }
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, UI }

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

    val showTasksOfSubprojects = Var(false)
    val selectedUserId: Var[Option[UserId]] = Var(Some(GlobalState.userId.now))

    val detailWidgets = VDomModifier(
      Styles.flex,
      alignItems.flexStart,
      Rx{ if(GlobalState.screenSize() == ScreenSize.Small) flexDirection.column else flexDirection.row },

      //TODO: renderSubprojects mit summary
      div(
        width := "100%",
        div(
          Styles.flex,
          alignItems.flexStart,
          h2("Your tasks", cls := "tasklist-header", marginRight.auto, Styles.flexStatic),
          UI.toggle("Show tasks of sub-projects", isChecked = showTasksOfSubprojects).apply(marginLeft := "10px", marginRight := "10px"),
          marginBottom := "15px"
        ),
        Rx {
          AssignedTasksView(focusState, deepSearch = showTasksOfSubprojects(), selectedUserId).apply(padding := "0px")
        },
      ),
      UI.segment("Sub-projects", VDomModifier(renderSubprojects(focusState), overflowX.auto)).apply(Styles.flexStatic, segmentMod)
    )

    val dashboard = if (BrowserDetect.isMobile) VDomModifier(
      detailWidgets
    )
    else VDomModifier(
      padding := "20px",
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
      padding := "10px",

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
              h4("Deleted Sub-projects", color.gray),
              deletedProjectNodes.map(renderSubproject(GlobalState.graph(), focusState, _, isDeleted = true))
            )
          )
        },
        registerDragContainer,
        marginBottom := "20px",
      ),

      div(
        textAlign.right,
        NewProjectPrompt.newProjectButton(
          label = "+ Add Sub-project",
          focusNewProject = false,
          buttonClass = "basic tiny compact",
          extraChanges = nodeId => GraphChanges.connect(Edge.Child)(ParentId(focusState.focusedId), ChildId(nodeId))
        )
      )
    )
  }

  /// Render the overview of a single (sub-) project
  private def renderSubproject(graph: Graph, focusState: FocusState, project: Node, isDeleted: Boolean)(implicit ctx: Ctx.Owner): VNode = {
    val isDeleted = graph.isDeletedNow(project.id, focusState.focusedId)
    val assigned = graph.idToIdxFold(project.id)(Seq.empty[Node.User]) { idx =>
      graph.assignedUsersIdx.map(idx) { idx => graph.nodes(idx).as[Node.User] }
    }

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
            case true  => Icons.bookmark
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

      div(
        Styles.flex,
        alignItems.center,
        marginLeft := "10px",
        assigned.map { user =>
          removableUserAvatar(user, project.id, size = "16px")
        },
      ),

      tailButton,

      VDomModifier.ifTrue(isDeleted)(cls := "node-deleted"),
    )
  }
}
