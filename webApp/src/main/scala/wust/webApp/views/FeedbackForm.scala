package wust.webApp.views

import wust.facades.crisp._
import scala.util.Try
import scala.scalajs.js
import wust.facades.googleanalytics.Analytics
import wust.facades.nolt.{ NoltData, nolt }
import fontAwesome._
import org.scalajs.dom.window.{ clearTimeout, navigator, setTimeout }
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.api.ClientInfo
import wust.css.{ Styles, ZIndex }
import wust.sdk.Colors
import wust.webApp.Client
import wust.webApp.state.{ GlobalState, ScreenSize }
import wust.webApp.views.Components._

import scala.scalajs.LinkingInfo
import scala.util.{ Failure, Success }
import wust.webApp.ProductionOnly
import wust.api.AuthUser.Implicit
import wust.api.AuthUser.Real

object FeedbackForm {

  def apply(implicit ctx: Ctx.Owner) = {
    val showPopup = Var(false)
    val activeDisplay = Rx { display := (if (showPopup()) "block" else "none") }

    val feedbackText = Var("")
    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val initialStatus = ""
    val statusText = Var(initialStatus)

    var timeout: Option[Int] = None
    def submit(): Unit = {
      if (feedbackText.now.trim.nonEmpty) {
        //         Client.api.feedback(ClientInfo(navigator.userAgent), feedbackText.now).onComplete { }
        Try{
          initCrisp
          crisp.push(js.Array("do", "message:send", js.Array("text", feedbackText.now)))
        } match {
          case Success(()) =>
            statusText() = "Thank you!"
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
            clear.onNext(())
          case Failure(t) =>
            scribe.warn("Error sending feedback to backend", t)
            statusText() = "Failed to send. Please try again or via email... "
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
        }
      }

      Analytics.sendEvent("feedback", "submit")
    }

    val feedbackForm = div(
      fontSize.smaller,
      cls := "enable-text-selection",
      h3("How can we help you?"),
      div(
        cls := "ui form",
        textArea(
          cls := "field",
          onInput.value --> feedbackText,
          value <-- clear,
          rows := 5, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
          resize := "none",
          placeholder := "Questions? Missing features? Suggestions? Something is not working? What do you like? What is annoying?"
        )
      ),
    )

    div(
      div(
        "Feedback/Support ", freeSolid.faCaretDown,
        // like semantic-ui tiny button
        fontSize := "0.85714286rem",
        fontWeight := 700,
        padding := ".58928571em 1.125em .58928571em",
        cursor.pointer,

        onClick.stopPropagation foreach {
          Analytics.sendEvent("feedback", if (showPopup.now) "close" else "open")
          showPopup.update(!_)
        },
        onGlobalEscape(false) --> showPopup,
        onGlobalClick(false) --> showPopup,
      ),
      div(
        activeDisplay,
        position.fixed, top := "35px", right <-- Rx{ if (GlobalState.screenSize() == ScreenSize.Small) "0px" else "100px" },
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
            cls := "ui tiny blue button",
            marginRight := "0",
            "Send",
            onClick foreach { submit() },
          // onClick(false) --> showPopup,
          ),
        ),
        div("Or write us an email: ", Components.woostTeamEmailLink, "."),

        div(cls := "ui divider", marginTop := "30px"),
        supportChatButton( showPopup),
        voteOnFeaturesButton,
        onClick.stopPropagation foreach {}, // prevents closing feedback form by global click
      )
    )
  }

  def initCrisp(): Unit = {
    GlobalState.user.now match {
      case user: Implicit =>
        crisp.push(js.Array("set", "user:nickname", js.Array(user.name)))

      case user: Real =>
        Client.auth.getUserDetail(user.id).onComplete {
          case Success(detail) =>
            detail.foreach(_.email.foreach{ email =>
              crisp.push(js.Array("set", "user:email", js.Array(email)))
            })
          case Failure(err) =>
            scribe.info("Cannot get UserDetail", err)
        }
        crisp.push(js.Array("set", "user:nickname", js.Array(user.name)))

      case _ =>
    }
  }

  def supportChatButton( showPopup: Var[Boolean]) = {
    button(
      freeSolid.faComments, " Open Support Chat",
      cls := "ui blue tiny fluid button",
      marginTop := "5px",
      ProductionOnly{
        onClick.stopPropagation.foreach { _ =>
          Try{
            initCrisp
            crisp.push(js.Array("do", "chat:show"))
            crisp.push(js.Array("do", "chat:open"))
          }
          showPopup() = false
        }
      }
    )
  }

  def voteOnFeaturesButton = button(
    freeSolid.faSort, " Vote on Features",
    cls := "ui violet tiny fluid button",
    marginTop := "5px",
    cls := "vote-button",
    snabbdom.VNodeProxy.repairDomBeforePatch, // nolt button modifies the dom
    ProductionOnly {
      onDomMount.foreach { _ =>
        try {
          nolt("init", new NoltData {
            var url = "https://woost.nolt.io"
            var selector = ".vote-button"
          });
        } catch {
          case e: Throwable =>
            scribe.error("Failed to init nolt", e)
        }
      }
    }
  )

}
