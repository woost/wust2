package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{Styles, ZIndex}
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}
import wust.webUtil.StringOps

object FilesView {
  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val files: Rx.Dynamic[Seq[(NodeId, NodeData.File, EpochMilli)]] = Rx {
      val graph = state.graph()

      graph.pageFiles(focusState.focusedId).map { case (id, data) =>
        val creationDate = graph.nodeCreated(graph.idToIdx(id))
        (id, data, creationDate)
      }.sortBy { case (_, _, date) => date }.reverse
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
          files.map { case (id, file, date) =>
            Components.renderUploadedFile(state, id, file).apply(
              cls := "ui bordered medium",
              padding := "10px",
              margin := "10px",
              border := "2px solid grey",
              borderRadius := "10px",
              div(
                color.gray,
                width := "100%",
                Styles.flex,
                justifyContent.flexEnd,
                date.isoDate
              )
            )
          }
        )
      }
    )
  }
}
