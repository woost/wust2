package wust.webApp.views

import outwatch._
import outwatch.dsl._
import fontAwesome.freeSolid
import colibri.ext.rx._
import rx.Ctx
import org.scalajs.dom
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

    val liStyle = VDomModifier(
      fontSize.larger,
      marginTop := "20px",
    )

    div(
      Styles.growFull, // this view needs to be grow-full, because it is used to OVERLAY any view behind it.

      Topbar.apply,
      div(
        padding := "80px 20px",

        Styles.flex,
        justifyContent.center,
        alignItems.flexStart,

        div(
          padding := "30px",
          cls := "pagenotfound ui segment",
          maxWidth := "80ex",

          div(replaceEmojiUnified(shrugEmoji), fontSize := "50px", textAlign.center),
          h2(
            "We're sorry. The requested content does not exist or you don't have sufficient permissions."
          ),

          h2("Things that might help: "),
          ul(
            li(
              liStyle,
              button(span(freeSolid.faRedo, marginRight := "0.5em"), "Reload Page", cls := "ui button positive", onClick.foreach { dom.window.location.reload() }),
            ),
            li(
              liStyle,
              "If you came here via a link, ask the sender if the project is actually accessible via a link.",
            ),
            li(
              liStyle,
              p("Maybe you already have the right permissions, but you just aren't logged in?"),
              AuthControls.authStatusOnLightBackground(showLogin = true),
            ),
            li (
              liStyle,
              p("If something seems to be wrong, please contact us. We are happy to help you."),
              FeedbackForm.supportChatButton,
              span(marginLeft := "20px", Components.woostEmailLink("support")),
            ),
          ),

        ),
      ),
    )
  }
}
