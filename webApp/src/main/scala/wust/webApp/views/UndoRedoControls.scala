package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import wust.css.Styles
import wust.webApp.state._
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._
import wust.sdk.UndoManagement
import fontAwesome.freeSolid
import rx._

object UndoRedoControls {
  GlobalState.eventProcessor.undoState.foreach { s =>
    println("FOR " + s)
  }

  def controls(implicit ctx: Ctx.Owner): VNode = {
    def control(action: UndoManagement.Action): VNode = div(
      paddingLeft := "2px",
      paddingRight := "2px",

      GlobalState.eventProcessor.undoState.map(_.canApply(action)).prepend(false).map {
        case true => VDomModifier(
          cursor.pointer,
          onClick.stopPropagation(action) --> GlobalState.eventProcessor.undoActions
        )
        case false => VDomModifier(
          color := "lightgrey"
        )
      }
    )

    div(
      Styles.flex,
      alignItems.center,

      div(
        "Undo:",
        paddingRight := "4px"
      ),
      control(UndoManagement.Action.Undo).apply(
        freeSolid.faUndo
      ),
      control(UndoManagement.Action.Redo).apply(
        freeSolid.faRedo
      ),
    )
  }
}
