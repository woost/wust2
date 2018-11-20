package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.css.{Styles, ZIndex}
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.UI._
import wust.util._

// Combines linear chat and thread-view into one view with a thread-switch
object ConversationView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val threaded = Var(false)
    div(
      Styles.flex,
      flexDirection.column,
      keyed,

      threadSwitchBar(threaded),

      Rx{
        if(threaded()) ThreadView(state).apply(
          Styles.growFull,
          flexGrow := 1
        )
        else ChatView(state).apply(
          Styles.growFull,
          flexGrow := 1
        )
      }
    )
  }

  private def threadSwitchBar(threaded:Var[Boolean]):VDomModifier = {
    VDomModifier(
      position.relative, // for absolute positioning of the bar
      div(
        position.absolute,
        zIndex := ZIndex.overlayLow-1, // like selectednodes, but still below

        right := "0px",
        padding := "5px",
        paddingRight := "10px",
        textAlign.right,

        toggle("Threaded") --> threaded,
      ),
    )
  }
}
