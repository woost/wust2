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
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.util._

// Shows overview over a project:
// - subprojects
// - content
// - activity
object DashboardView {

  private def getProjectList(graph: Graph, focusedId: NodeId): Seq[Node] = {
    val pageParentIdx = graph.idToIdx(focusedId)
    val directSubProjects = graph.projectChildrenIdx(pageParentIdx)
    directSubProjects.map(graph.nodes).sortBy(_.str)
  }

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val widgetStyle = VDomModifier(
      Styles.growFull,
      backgroundColor := "rgba(255,255,255,0.6)",
      borderRadius := "10px",
      margin := "10px",
      padding := "5px",
    )

    div(
      Styles.flex,
      flexDirection.column,
      justifyContent.flexStart,
      padding := "20px",
      overflow.auto,

      //TODO: renderSubprojects mit summary
      renderSubprojects(state, focusState),
      Rx {
        VDomModifier.ifTrue(state.screenSize() != ScreenSize.Small)(
          div(
            Styles.growFull,
            Styles.flex,
            Rx{
              val graph = state.graph()
              for{
                node <- graph.nodesByIdGet(focusState.focusedId)
                viewList <- node.views
              } yield VDomModifier(
                VDomModifier.ifTrue(viewList.exists(view => view == View.List || view == View.Kanban))(
                  ListView(state, focusState).apply(widgetStyle)
                ),
                VDomModifier.ifTrue(viewList.exists(view => view == View.Chat || view == View.Thread))(
                  ChatView(state, focusState).apply(widgetStyle)
                )
              ) 
            },
          )
        )
      }
    )
  }

  /// Render all subprojects as a list
  private def renderSubprojects(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VDomModifier = {
    Rx {
      val graph = state.graph()
      val projectNodes = getProjectList(graph, focusState.focusedId)
      ul(
        Styles.flexStatic,

        display.flex,
        flexWrap.wrap,
        justifyContent.flexStart,

        padding := "0px", // remove ul default padding

        projectNodes map { projectInfo =>
          li(
            Styles.flexStatic,
            listStyle := "none",
            renderSubproject(state, graph, focusState, projectInfo)
          )
        },
        li(
          listStyle := "none",
          Styles.flexStatic,

          newSubProjectButton(state, focusState.focusedId)
        ),
        registerDragContainer(state)
      )
    }
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
        focusState.parentIdAction(project.id)
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


  private def newSubProjectButton(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
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

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add."

    div(
      borderRadius := "3px",
      margin := "0.5em",
      padding := "15px",
      minHeight := "70px",
      backgroundColor := "rgba(158, 158, 158, 0.25)",
      border := "3px solid transparent",
      onClick.stopPropagation(true) --> fieldActive,
      cursor.pointer,
      Rx {
        if(fieldActive()) {
          VDomModifier(
            padding := "0px",
            inputRow(state,
              submitAction,
              autoFocus = true,
              blurAction = Some(blurAction),
              placeHolderMessage = Some(placeHolder),
              submitIcon = freeSolid.faPlus,
              textAreaModifiers = VDomModifier(
                fontSize.larger,
                fontWeight.bold,
                minHeight := "50px"
              )
            ).apply(
              margin := "0px"
            )
          )
        }
        else
          h1(
            "+ Add Sub-Project",
            color := "rgba(0, 0, 0, 0.62)",
            fontSize := "1.5em",
            fontWeight.normal,
            margin := "0 0.5em",
            height := "30px"
          )
      },
    )
  }
}
