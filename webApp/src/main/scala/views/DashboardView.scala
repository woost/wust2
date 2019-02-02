package wust.webApp.views

import wust.webApp.dragdrop.{DragContainer, DragItem}
import fontAwesome.freeSolid
import SharedViewElements._
import wust.webApp.{BrowserDetect, Icons, ItemProperties}
import wust.webApp.state.View
import wust.webApp.Icons
import outwatch.dom._
import wust.sdk.{BaseColors, NodeColor}
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.views.Elements._
import monix.reactive.subjects.{ BehaviorSubject, PublishSubject }
import rx._
import wust.css.{ Styles, ZIndex }
import wust.graph.{ Graph, Page, Node, GraphChanges }
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.util._

// Shows overview over a project:
// - subprojects
// - content
// - activity
object DashboardView {

  def getProjectList(graph: Graph, pageParentId: NodeId): Seq[Node] = {
    val pageParentIdx = graph.idToIdx(pageParentId)
    val directSubProjects = graph.projectChildrenIdx(pageParentIdx)
    directSubProjects.map(graph.nodes)
  }

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      flexDirection.column,
      justifyContent.flexStart,
      padding := "20px",

      Rx {
        state.page().parentId.map { pageParentId =>
          //TODO: renderSubprojects mit summary
          VDomModifier(
            renderSubprojects(state, pageParentId),
            renderProjectStats(state, pageParentId)
          )
        }
      }
    )
  }

  /// Render all subprojects as a list
  def renderSubprojects(state: GlobalState, pageParentId:NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    Rx {
      val graph = state.graph()
      val projectNodes = getProjectList(graph, pageParentId)
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
            renderSubproject(state, projectInfo, graph)
          )
        },
        li(
          listStyle := "none",
          Styles.flexStatic,

          newSubProjectButton(state, pageParentId)
        ),
        registerDragContainer(state)
      )
    }
  }

  /// Render the overview of a single (sub-) project
  def renderSubproject(state: GlobalState, project: Node, graph:Graph): VNode = {
    div(
      border := "3px solid",
      borderRadius := "3px",
      margin := "0.5em",
      padding := "10px",
      backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(project.id)).toHex,
      borderColor := BaseColors.pageBorder.copy(h = NodeColor.hue(project.id)).toHex,

      drag(DragItem.Project(project.id)),
      cls := "node", // for draghighlight

      Styles.flex,
      alignItems.flexStart,

      nodeAvatar(project, size = 30)(marginRight := "5px", flexShrink := 0),
      h1(
        renderNodeData(project.data),
        fontSize := "1.5em",
        margin := "0 0.5em",
      ),

      onClick foreach {
        state.urlConfig.update(_.focus(page = Page(project.id), View.Dashboard))
      },
      cursor.pointer,
    )
  }


  def newSubProjectButton(state: GlobalState, pageParentId:NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val fieldActive = Var(false)
    def submitAction(str:String) = {
      val change = {
        val newProjectNode = Node.MarkdownProject(str)
        GraphChanges.addNodeWithParent(newProjectNode, pageParentId)
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

  def renderProjectStats(state: GlobalState, pageParentId: NodeId)(implicit ctx:Ctx.Owner):VNode = {
    //TODO: move styling into css classes
    val commonStyles = VDomModifier(
      fontSize := "2em",
      alignItems.center,
      backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(pageParentId)).toHex,
      margin := "10px 10px 10px 0px",
      padding := "10px",
      borderRadius := "3px",
    )

    div(
      Styles.flex,
      flexDirection.row,
      justifyContent.flexStart,
      alignItems.flexStart,

      marginTop := "30px",

      Rx {
        val graph = state.graph()
        val stats = graph.topLevelRoleStats(pageParentId :: Nil)
        stats.roleCounts.collect{
          case (NodeRole.Message, count) => 
            div(
              Styles.flex,
              commonStyles,
              div(cls := "fa-fw", Icons.conversation),
              div(count, marginLeft := "0.5em"),
              onClick foreach {
                state.urlConfig.update(_.focus(View.Conversation))
              },
              cursor.pointer,
            )
          case (NodeRole.Task, count) =>
            div(
              Styles.flex,
              commonStyles,
              div(cls := "fa-fw", Icons.tasks),
              div(count, marginLeft := "0.5em"),
              onClick foreach {
                state.urlConfig.update(_.focus(View.Tasks))
              },
              cursor.pointer,
            )
        }
      }
    )
  }
}
