package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph.Node
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.util._

object FilesView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val files: Rx.Dynamic[Seq[(NodeId, NodeData.File)]] = Rx {
      val graph = state.graph()
      val page = state.page()

      page.parentId.fold(Seq.empty[(NodeId, NodeData.File)])(parentId => graph.pageFiles(parentId))

    }

    div(
      padding := "20px",
      overflow.auto,
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
