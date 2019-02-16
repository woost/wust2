package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{Styles, ZIndex}
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}

object FilesView {
  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val files: Rx.Dynamic[Seq[(NodeId, NodeData.File)]] = Rx {
      val graph = state.graph()

      graph.pageFiles(focusState.focusedId).sortBy { case (id, _) => id }.reverse
    }

    div(
      padding := "20px",
      overflow.auto,
      Components.uploadField(state, Components.defaultFileUploadHandler(state), tooltipDirection = "bottom left"),
      UI.horizontalDivider("Files")(marginTop := "20px", marginBottom := "20px"),
      files.map { files =>
        if (files.isEmpty) p("There are no files in this workspace, yet.", color := "grey")
        else div(
          Styles.flex,
          flexDirection.row,
          flexWrap.wrap,
          files.map { case (id, file) =>
            Components.renderUploadedFile(state, id, file).apply(cls := "ui bordered medium", padding := "10px", margin := "10px", border := "2px solid grey", borderRadius := "10px")
          }
        )
      }
    )
  }
}
