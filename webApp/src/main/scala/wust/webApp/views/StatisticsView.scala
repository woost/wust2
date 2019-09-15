package wust.webApp.views

import fontAwesome.IconDefinition
import outwatch.dom.dsl._
import outwatch.dom.{VNode, _}
import rx._
import wust.css.Styles
import wust.graph.SemanticNodeRole
import wust.ids.{NodeRole, View}
import wust.webApp.Icons
import wust.webApp.state.{FocusState, GlobalState, _}
import wust.webApp.views.Components._
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._

object StatisticsView  {

  //FIXME
  // private val nodeRoleVisuals: SemanticNodeRole => (View, IconDefinition) = {
  //   case SemanticNodeRole.Role(NodeRole.Message) => View.Conversation -> Icons.conversation
  //   case SemanticNodeRole.Role(NodeRole.Task) => View.Tasks -> Icons.tasks
  //   case SemanticNodeRole.Role(NodeRole.Project) => View.Dashboard -> Icons.project
  //   case SemanticNodeRole.Role(NodeRole.Note) => View.Content -> Icons.notes
  //   case SemanticNodeRole.File => View.Files -> Icons.files
  // }

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
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
          val graph = GlobalState.graph()
          val stats = graph.topLevelRoleStats(GlobalState.userId(), focusState.focusedId).roles

          div
          // stats.map { stat =>
          //   val (view, icon) = nodeRoleVisuals(stat.role)
          //   a(
          //     VDomModifier.ifTrue(stat.count == 0)(opacity := 0.5),
          //     cls := "item",
          //     if (GlobalState.screenSize() == ScreenSize.Small) fontSize := "10px" else minWidth := "110px", // not much space on mobile, so try to stay as small as possible
          //     Styles.flex,
          //     flexDirection.column,
          //     alignItems.center,

          //     div(Elements.icon(icon), stat.role.toString),
          //     h2(
          //       stat.count,
          //       VDomModifier.ifTrue(GlobalState.screenSize() == ScreenSize.Small)(fontSize.small), // not much space on mobile, so try to stay as small as possible
          //     ),

          //     onClick.stopPropagation.use(view).foreach(focusState.viewAction),
          //     position.relative,
          //   )
          // }
        }
      )
    )
  }
}
