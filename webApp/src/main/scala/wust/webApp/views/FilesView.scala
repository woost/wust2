package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.ids._
import wust.webApp.state.{FocusState, GlobalState}
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._

object FilesView {
  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val files: Rx[Seq[(NodeId, NodeData.File, EpochMilli)]] = Rx {
      val graph = GlobalState.graph()
      val focusedIdx = graph.idToIdxOrThrow(focusState.focusedId)

      graph.pageFilesIdx(focusedIdx).map { fileIdx =>
        val node = graph.nodes(fileIdx)
        val creationDate = graph.nodeCreated(fileIdx)
        (node.id, node.data.as[NodeData.File], creationDate)
      }.sortBy { case (_, _, date) => -date }
    }

    div(
      keyed,
      padding := "20px",
      overflow.auto,
      UploadComponents.uploadFieldRx( UploadComponents.defaultFileUploadHandler( focusState.focusedId)),
      UI.horizontalDivider("Files")(marginTop := "20px", marginBottom := "20px"),
      files.map { files =>
        if (files.isEmpty) p("There are no files in this workspace, yet.", color := "grey")
        else div(
          Styles.flex,
          flexDirection.row,
          flexWrap.wrap,
          files.map { case (id, file, date) =>
            UploadComponents.renderUploadedFile( id, file).apply(
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
