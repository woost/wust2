package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.webUtil.outwatchHelpers._
import wust.api.AuthUser
import wust.css.Styles
import wust.ids.View
import wust.webApp.state.GlobalState

object PageNotFoundView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      padding := "80px 20px",
      Styles.growFull, // this view needs to be grow-full, because it is used to OVERLAY any view behind it.

      Styles.flex,
      justifyContent.center,
      alignItems.flexStart,

      div(
        cls := "pagenotfound ui segment red",
        maxWidth := "80ex",

        h2(
          "We're sorry. The page you're looking for does not exist or you don't have sufficient permissions."
        ),

        state.user.map {
          case _: AuthUser.Real => VDomModifier.empty
          case _ => div(
            padding := "10px",
            span("Maybe you just need to login?", fontSize.larger),
            button(
              marginLeft := "20px",
              cls := "ui primary button",
              "Login",
              onClick.stopPropagation.foreach(state.urlConfig.update(_.focusWithRedirect(View.Login)))
            )
          )
        }
      ),
    )
  }
}
