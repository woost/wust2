package wust.webApp.views

import wust.webApp.state._
import fontAwesome.IconDefinition
import outwatch.dom.{VNode, _}
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph.SemanticNodeRole
import wust.ids.{EpochMilli, NodeRole, View}
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState, TraverseState}
import wust.webApp.views.AssignedTasksData.AssignedTask

import scala.scalajs.js

object StatisticsView  {

  private val nodeRoleVisuals: SemanticNodeRole => (View, IconDefinition) = {
    case SemanticNodeRole.Role(NodeRole.Message) => View.Conversation -> Icons.conversation
    case SemanticNodeRole.Role(NodeRole.Task) => View.Tasks -> Icons.tasks
    case SemanticNodeRole.Role(NodeRole.Project) => View.Dashboard -> Icons.project
    case SemanticNodeRole.Role(NodeRole.Note) => View.Content -> Icons.notes
    case SemanticNodeRole.File => View.Files -> Icons.files
  }

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      keyed,
      width := "100%",
      Styles.flex,
      flexWrap.wrap,
      padding := "20px",

      div(
        margin.auto,
        overflow.auto,
        cls := "ui compact menu",
        Rx {
          val graph = state.graph()
          val stats = graph.topLevelRoleStats(state.userId(), focusState.focusedId).roles

          stats.map { stat =>
            val (view, icon) = nodeRoleVisuals(stat.role)
            a(
              VDomModifier.ifTrue(stat.count == 0)(opacity := 0.5),
              cls := "item",
              if (state.screenSize() == ScreenSize.Small) fontSize := "10px" else minWidth := "110px", // not much space on mobile, so try to stay as small as possible
              Styles.flex,
              flexDirection.column,
              alignItems.center,

              div(Elements.icon(icon), stat.role.toString),
              h2(
                stat.count,
                VDomModifier.ifTrue(state.screenSize() == ScreenSize.Small)(fontSize.small), // not much space on mobile, so try to stay as small as possible
              ),

              onClick.stopPropagation(view).foreach(focusState.viewAction),
              position.relative,
            )
          }
        }
      )
    )
  }
}
