package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import rx._
import wust.webApp.state.GlobalState

object ErrorView {
  def apply(msg: String, state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    padding := "50px",
    b("Oops, an error occurred!"),
    br(),
    br(),
    msg
  )
}
