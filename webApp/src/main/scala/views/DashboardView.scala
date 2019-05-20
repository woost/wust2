package wust.webApp.views

import wust.webApp.dragdrop.{DragContainer, DragItem}
import fontAwesome.freeSolid
import SharedViewElements._
import wust.webApp.{BrowserDetect, Icons, ItemProperties}
import wust.webApp.Icons
import outwatch.dom._
import wust.sdk.{BaseColors, NodeColor}
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.views.Elements._
import monix.reactive.subjects.{BehaviorSubject, PublishSubject}
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.util.collection._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState, ScreenSize, Placeholder}
import wust.webApp.views.Components._
import wust.util._

// Shows overview over a project:
// - subprojects
// - content
// - activity
object DashboardView {

  private def getProjectList(graph: Graph, focusedId: NodeId): Seq[Node] = {
    val pageParentIdx = graph.idToIdxOrThrow(focusedId)
    val directSubProjects = graph.projectChildrenIdx(pageParentIdx)
    directSubProjects.viewMap(graph.nodes).sortBy(_.str)
  }

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val segmentMod = VDomModifier(
      margin := "10px"
    )

    val configWidgets = VDomModifier(
      Styles.flex,
      UI.segment("Views", ViewSwitcher.selectForm(state, focusState.focusedId)).apply(Styles.flexStatic, segmentMod),
    )

    val detailWidgets = VDomModifier(
      Styles.flex,
      //TODO: renderSubprojects mit summary
      UI.segment("Sub-Projects", VDomModifier(renderSubprojects(state, focusState), overflowX.auto)).apply(Styles.flexStatic, segmentMod),

      UI.segment("Notifications", NotificationView(state, focusState).apply(padding := "0px")).apply(Styles.flexStatic, segmentMod),
    )

    val dashboard = if (BrowserDetect.isMobile) VDomModifier(
      Styles.flex,
      flexDirection.column,

      detailWidgets,
      configWidgets
    ) else VDomModifier(
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
  private def renderSubprojects(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VDomModifier = {

    ul(
      Styles.flexStatic,

      display.flex,
      flexWrap.wrap,
      justifyContent.flexStart,

      padding := "0px", // remove ul default padding

      Rx {
        val projectNodes = getProjectList(state.graph(), focusState.focusedId)
        projectNodes map { projectInfo =>
          li(
            Styles.flexStatic,
            listStyle := "none",
            renderSubproject(state, state.graph(), focusState, projectInfo)
          )
        }
      },
      li(
        listStyle := "none",
        Styles.flexStatic,

        newSubProjectButton(state, focusState.focusedId)
      ),
      registerDragContainer(state)
    )
  }

  /// Render the overview of a single (sub-) project
  private def renderSubproject(state: GlobalState, graph: Graph, focusState: FocusState, project: Node): VNode = {
    div(
      border := "3px solid",
      borderRadius := "3px",
      margin := "0.5em",
      padding := "10px",

      drag(DragItem.Project(project.id)),
      cls := "node", // for draghighlight

      Styles.flex,
      alignItems.flexStart,

      nodeAvatar(project, size = 30)(marginRight := "5px", flexShrink := 0),
      h1(
        renderAsOneLineText(project),
        fontSize := "1.5em",
        margin := "0 0.5em",
      ),

      onClick foreach {
        focusState.contextParentIdAction(project.id)
      },
      cursor.pointer,

      if(graph.isDeletedNow(project.id, focusState.focusedId)) {
        VDomModifier(
          backgroundColor := "darkgrey",
          borderColor := "grey",
          cls := "node-deleted",
          Components.unremovableTagMod(() => state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Child)(ParentId(focusState.focusedId), ChildId(project.id))))
        )
      } else {
        VDomModifier(
          backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(project.id)).toHex,
          borderColor := BaseColors.pageBorder.copy(h = NodeColor.hue(project.id)).toHex,
          Components.removableTagMod(() => state.eventProcessor.changes.onNext(GraphChanges.delete(ChildId(project.id), ParentId(focusState.focusedId))))
        )
      },

    )
  }


  private def newSubProjectButton(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val fieldActive = Var(false)
    def submitAction(str:String) = {
      val change = {
        val newProjectNode = Node.MarkdownProject(str)
        GraphChanges.addNodeWithParent(newProjectNode, ParentId(focusedId))
      }
      state.eventProcessor.changes.onNext(change)
    }

    def blurAction(v:String) = {
      if(v.isEmpty) fieldActive() = false
    }

    VDomModifier(
      Rx {
        if(fieldActive()) {
          VDomModifier(
            inputRow(state,
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
        }
        else button(
          margin := "0.5em",
          onClick.stopPropagation(true) --> fieldActive,
          cursor.pointer,
          cls := "ui big button",
          "+ Add Sub-Project",
        )
      },
    )
  }
}
