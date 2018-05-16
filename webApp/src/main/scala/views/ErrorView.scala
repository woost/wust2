package wust.webApp.views

import cats.data.NonEmptyList
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import rx._
import org.scalajs.dom

class ErrorView(msg: String) extends View {
  override val key = "error"
  override val displayName = "Error"
  override def toString = s"ErrorView($msg)"

  override final def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    padding := "50px",
    b("Oops, an error occurred!"),
    br(), br(),
    msg
  )
}
