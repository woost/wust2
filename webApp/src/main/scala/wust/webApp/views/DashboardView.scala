package wust.webApp.views

import fontAwesome.freeSolid
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, Elements, UI }
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util.collection._
import wust.webApp.Permission
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{ FocusState, GlobalState, Placeholder }
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer

// Shows overview over a project:
// - subprojects
// - content
// - activity
object DashboardView {

  final case class Settings(
    val AlwaysShowNewSubprojectButton: Boolean = false,
    val ForceEditModeOnEmptySubprojects: Boolean = true
  )
  val settings = Settings()

  val editModeState = Var(false)

  private def getProjectList(graph: Graph, focusedId: NodeId): Seq[Node] = {
    val pageParentIdx = graph.idToIdxOrThrow(focusedId)
    val directSubProjects = graph.projectChildrenIdx(pageParentIdx)
    directSubProjects.viewMap(graph.nodes).sortBy(_.str)
  }

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val segmentMod = VDomModifier(
      margin := "10px"
    )

    val configWidgets = VDomModifier(
      Styles.flex,
      UI.segment("Views", ViewModificationMenu.selectForm(focusState.focusedId)).apply(Styles.flexStatic, segmentMod),
    )

    val projectNodes = Rx { getProjectList(GlobalState.graph(), focusState.focusedId) }
    val forceEditMode = Rx { settings.ForceEditModeOnEmptySubprojects && projectNodes().length == 0 }
    val editMode = Rx { editModeState() || forceEditMode() }
    val editModeSwitcher = Rx {
      Elements.icon(wust.webApp.Icons.edit)(
        VDomModifier.ifTrue(forceEditMode())(cls := "disabled"),
        onClick.stopPropagation foreach {
          editModeState() = !editModeState()
        },
        cursor.pointer,
        UI.tooltip("top center") := (editMode() match {
          case false => "Activate edit mode"
          case true  => "Disable edit mode"
        })
      )
    }

    val detailWidgets = VDomModifier(
      Styles.flex,
      div(StatisticsView(focusState).apply(padding := "0px"), Styles.flexStatic, segmentMod),

      //TODO: renderSubprojects mit summary
      UI.segment(
        div(
          "Subprojects ",
          editModeSwitcher
        ),
        VDomModifier(renderSubprojects(focusState, editMode), overflowX.auto)
      ).apply(Styles.flexStatic, segmentMod),
      UI.segment("Tasks", AssignedTasksView(focusState).apply(padding := "0px")).apply(Styles.flexStatic, segmentMod),
    )

    val dashboard = if (BrowserDetect.isMobile) VDomModifier(
      Styles.flex,
      flexDirection.column,

      detailWidgets,
      configWidgets
    )
    else VDomModifier(
      padding := "20px",
      Styles.flex,

      div(
        flexDirection.column,
        flex := "1",
        minWidth := "500px",
        detailWidgets
      ),

      div(
        minWidth := "250px",
        flexDirection.column,
        configWidgets
      )
    )

    div(
      Styles.growFull,
      overflow.auto,

      dashboard
    )
  }

  /// Render all subprojects as a list
  private def renderSubprojects(focusState: FocusState, editMode: Rx.Dynamic[Boolean])(implicit ctx: Ctx.Owner): VDomModifier = {

    div(
      Styles.flex,
      justifyContent.spaceBetween,
      alignItems.flexEnd,
      ul(
        Styles.flexStatic,
        flexDirection.column,
        Styles.flex,
        flexWrap.wrap,
        justifyContent.flexStart,

        padding := "0px", // remove ul default padding

        Rx {
          val projectNodes = getProjectList(GlobalState.graph(), focusState.focusedId)
          projectNodes map { projectInfo =>
            li(
              Styles.flexStatic,
              listStyle := "none",
              renderSubproject(GlobalState.graph(), focusState, projectInfo, editMode)
            )
          }
        },
        registerDragContainer
      ),
      Rx{
        VDomModifier.ifTrue(settings.AlwaysShowNewSubprojectButton || editMode())(
          newSubProjectButton(focusState)
        )
      }
    )
  }

  /// Render the overview of a single (sub-) project
  private def renderSubproject(graph: Graph, focusState: FocusState, project: Node,
    editMode: Rx.Dynamic[Boolean])(implicit ctx: Ctx.Owner): VNode = {
    val isDeleted = graph.isDeletedNow(project.id, focusState.focusedId)
    val dispatch = GlobalState.submitChanges _
    val deletionBtn = if (isDeleted) {
      Components.unremovableTagMod(() =>
        dispatch(GraphChanges.connect(Edge.Child)(ParentId(focusState.focusedId), ChildId(project.id))))
    } else {
      Components.removableTagMod(() =>
        dispatch(GraphChanges.delete(ChildId(project.id), ParentId(focusState.focusedId))))
    }

    val permissionLevel = Rx {
      Permission.resolveInherited(GlobalState.rawGraph(), project.id)
    }

    div(
      marginLeft := "10px",
      cls := "node channel-line",

      DragComponents.drag(DragItem.Project(project.id)),
      renderProject(project, renderNode = node => renderAsOneLineText(node).apply(cls := "channel-name"), withIcon = true),

      cursor.pointer,
      onClick foreach {
        focusState.contextParentIdAction(project.id)
      },

      permissionLevel.map(Permission.permissionIndicatorIfPublic(_, fontSize := "0.7em")),

      Rx{ VDomModifier.ifTrue(editMode())(deletionBtn) },

      VDomModifier.ifTrue(isDeleted)(cls := "node-deleted"),
    )
  }

  private def newSubProjectButton(focusState: FocusState)(implicit ctx: Ctx.Owner): VDomModifier = {
    val fieldActive = Var(false)
    def submitAction(sub: InputRow.Submission) = {
      val change = {
        // -- stay in edit mode --
        editModeState() = true
        val newProjectNode = Node.MarkdownProject(sub.text)
        GraphChanges.addNodeWithParent(newProjectNode, ParentId(focusState.focusedId)) merge sub.changes(newProjectNode.id)
      }
      GlobalState.submitChanges(change)
    }

    def blurAction(v: String) = {
      if (v.isEmpty) fieldActive() = false
    }

    VDomModifier(
      Rx {
        if (fieldActive()) {
          VDomModifier(
            InputRow(
              Some(focusState),
              submitAction,
              autoFocus = true,
              blurAction = Some(blurAction),
              placeholder = Placeholder.newProject,
              submitIcon = freeSolid.faPlus,
              textAreaModifiers = VDomModifier(
                fontWeight.bold
              )
            ).apply(
                margin := "0.5em"
              )
          )
        } else button(
          margin := "0.5em",
          onClick.stopPropagation(true) --> fieldActive,
          cursor.pointer,
          cls := "ui mini basic button",
          "+ Add Subproject",
        )
      },
    )
  }
}
