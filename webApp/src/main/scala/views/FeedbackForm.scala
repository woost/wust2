package wust.webApp.views

import fontAwesome._
import googleAnalytics.Analytics
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph._
import wust.ids
import wust.ids._
import wust.webApp.{BrowserDetect, Client, Icons}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, PageChange, ScreenSize, View, NodePermission}
import wust.webApp.views.Elements._
import wust.util._

import scala.util.{Success, Failure}

object FeedbackForm {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val show = Var(false)
    val activeDisplay = Rx { display := (if(show()) "block" else "none") }

    val feedbackText = Var("")
    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val initialStatus = if(BrowserDetect.isMobile) "" else "(Press Enter to submit)"
    val statusText = Var(initialStatus)

    var timeout:Option[Int] = None
    def submit():Unit = {
      if(feedbackText.now.trim.nonEmpty) {
        Client.api.feedback(feedbackText.now).onComplete {
          case Success(()) =>
            statusText() = "Thank you!"
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
            clear.onNext(())
          case Failure(t) =>
            scribe.warn("Error sending feedback to backend", t)
            statusText() = "Failed to send feedback, please try again!"
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
        }
      }

      Analytics.sendEvent("feedback", "submit")
    }

    val feedbackForm = div(
      width := "240px",
      fontSize.smaller,
      color := "#666",
      cls := "enable-text-selection",
      div(
        cls := "ui form",
        textArea(
          cls := "field",
          valueWithEnter(clearValue = false) foreach { submit() },
          onInput.value --> feedbackText,
          value <-- clear,
          rows := 5, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
          resize := "none",
          placeholder := "Missing features? Suggestions? You found a bug? What do you like? What is annoying?"
        )
      ),
      div(textAlign.right, statusText),
      div("Or send us an email: ", Components.woostTeamEmailLink, ".")
    )

    div(
      button(
        "Feedback ",
        freeSolid.faCaretDown,
        cls := "ui positive tiny compact button",
        onClick.stopPropagation foreach{
          Analytics.sendEvent("feedback", if(show.now) "close" else "open")
          if(BrowserDetect.isMobile && !show.now) state.sidebarOpen() = false // else feedback form is hidden behind sidebar
          show.update(!_)
        },
        onGlobalEscape(false) --> show,
        onGlobalClick(false) --> show,
      ),
      div(
        activeDisplay,
        position.fixed, top := s"${CommonStyles.topBarHeight}px", right <-- Rx{ if(state.screenSize() == ScreenSize.Small) "0px" else "100px" },
        zIndex := ZIndex.overlay,
        padding := "10px", background := "#F8F8F8", border := "1px solid #888",
        feedbackForm,
        div(
          marginTop := "20px",
          Styles.flex,
          justifyContent.flexEnd,
          button(
            Styles.flexStatic,
            tpe := "button",
            cls := "ui tiny compact primary button",
            "Submit",
            onClick foreach { submit() },
            onClick(false) --> show,
          ),
        ),
        onClick.stopPropagation foreach{}, // prevents closing feedback form by global click
      )
    )
  }

}
