package wust.webApp.views

import wust.sdk.Colors
import wust.webApp.DevOnly
import fontAwesome._
import googleAnalytics.Analytics
import org.scalajs.dom.window.{ clearTimeout, setTimeout, navigator }
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.ClientInfo
import wust.css.{ CommonStyles, Styles, ZIndex }
import wust.graph._
import wust.ids
import wust.ids._
import wust.webApp.{ BrowserDetect, Client, Icons }
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{ GlobalState, PageChange, ScreenSize, NodePermission }
import wust.webApp.views.Elements._
import wust.util._
import scala.scalajs.LinkingInfo

import scala.util.{ Success, Failure }

object FeedbackForm {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val show = Var(false)
    val activeDisplay = Rx { display := (if (show()) "block" else "none") }

    val feedbackText = Var("")
    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val initialStatus = ""
    val statusText = Var(initialStatus)

    var timeout: Option[Int] = None
    def submit(): Unit = {
      if (feedbackText.now.trim.nonEmpty) {
        Client.api.feedback(ClientInfo(navigator.userAgent), feedbackText.now).onComplete {
          case Success(()) =>
            statusText() = "Thank you!"
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
            clear.onNext(())
          case Failure(t) =>
            scribe.warn("Error sending feedback to backend", t)
            statusText() = "Failed to send feedback, please try again or via email..."
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
        }
      }

      Analytics.sendEvent("feedback", "submit")
    }

    val feedbackForm = div(
      fontSize.smaller,
      cls := "enable-text-selection",
      div(
        cls := "ui form",
        textArea(
          cls := "field",
          onInput.value --> feedbackText,
          value <-- clear,
          rows := 5, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
          resize := "none",
          placeholder := "Missing features? Suggestions? You found a bug? What do you like? What is annoying?"
        )
      ),
    )

    div(
      div(
        "Feedback ", freeSolid.faCaretDown,
        // like semantic-ui tiny button
        fontSize := "0.85714286rem", 
        fontWeight := 700,
        padding := ".58928571em 1.125em .58928571em",
        cursor.pointer,

        onClick.stopPropagation foreach {
          Analytics.sendEvent("feedback", if (show.now) "close" else "open")
          show.update(!_)
        },
        onGlobalEscape(false) --> show,
        onGlobalClick(false) --> show,
      ),
      div(
        activeDisplay,
        position.fixed, top := "35px", right <-- Rx{ if (state.screenSize() == ScreenSize.Small) "0px" else "100px" },
        zIndex := ZIndex.formOverlay,
        padding := "10px",
        borderRadius := "5px",
        cls := "shadow",
        backgroundColor := Colors.sidebarBg,
        color := "#333",
        feedbackForm,
        div(
          marginTop := "5px",
          marginBottom := "20px",
          Styles.flex,
          alignItems.center,
          justifyContent.spaceBetween,

          div(statusText, marginLeft.auto, marginRight.auto),
          button(
            Styles.flexStatic,
            tpe := "button",
            cls := "ui tiny violet button",
            marginRight := "0",
            "Submit",
            onClick foreach { submit() },
            // onClick(false) --> show,
          ),
        ),
        div("Or send us an email: ", Components.woostTeamEmailLink, "."),

        div(cls := "ui divider", marginTop := "30px"),
        div(width := "200px", "You can also vote on features and suggest new ones:"),
        div(voteOnFeatures),
        onClick.stopPropagation foreach {}, // prevents closing feedback form by global click
      )
    )
  }

  def voteOnFeatures = button(
    "Vote on features",
    cls := "ui violet tiny button",
    marginTop := "5px",
    cls := "vote-button",
    snabbdom.VNodeProxy.repairDomBeforePatch, // draggable modifies the dom, but snabbdom assumes that the dom corresponds to its last vdom representation. So Before patch
    VDomModifier.ifNot(LinkingInfo.developmentMode)(
      onDomMount.foreach { _ =>
        try {
          nolt.nolt("init", new nolt.NoltData { 
            var url = "https://woost.nolt.io"
            var selector = ".vote-button"
          });
        } catch { case e: Throwable =>
          scribe.error("Failed to init nolt", e)
        }
      }
    )
  )

}
