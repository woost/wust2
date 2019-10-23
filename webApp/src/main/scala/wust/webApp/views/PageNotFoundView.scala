package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.api.AuthUser
import wust.css.Styles
import wust.ids.View
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._
import wust.facades.segment.Segment

object PageNotFoundView {
  def apply(implicit ctx: Ctx.Owner) = {
    val shrugEmoji = "🤷"
    div(
      padding := "80px 20px",
      Styles.growFull, // this view needs to be grow-full, because it is used to OVERLAY any view behind it.

      Styles.flex,
      justifyContent.center,
      alignItems.flexStart,

      div(
        cls := "pagenotfound ui segment",
        maxWidth := "80ex",

        div(replaceEmojiUnified(shrugEmoji), fontSize := "50px", textAlign.center),
        h2(
          "We're sorry. The content you're looking for does not exist or you don't have sufficient permissions."
        ),

        GlobalState.user.map {
          case _: AuthUser.Real => VDomModifier.empty
          case _ => div(
            padding := "10px",
            span("Maybe you just need to login?", fontSize.larger),
            button(
              marginLeft := "20px",
              cls := "ui primary button",
              "Login",
              onClick.stopPropagation.foreach(GlobalState.urlConfig.update(_.focusWithRedirect(View.Login)))
            )
          )
        }
      ),
    )
  }
}
