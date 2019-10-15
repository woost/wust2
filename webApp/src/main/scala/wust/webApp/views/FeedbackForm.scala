package wust.webApp.views

import wust.facades.crisp._
import scala.util.Try
import scala.scalajs.js
import wust.facades.googleanalytics.GoogleAnalytics
import wust.facades.nolt.{ NoltData, nolt }
import fontAwesome._
import org.scalajs.dom.window
import org.scalajs.dom.window.{ clearTimeout, navigator, setTimeout }
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive.handler._
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.api.ClientInfo
import wust.css.{ Styles, ZIndex }
import wust.sdk.Colors
import wust.webApp.Client
import wust.webApp.state.{ GlobalState, ScreenSize }
import wust.webApp.views.Components._
import wust.ids.Feature
import wust.webApp.state.FeatureState

import scala.scalajs.LinkingInfo
import scala.util.{ Failure, Success }
import wust.webApp.DeployedOnly
import wust.api.AuthUser.Implicit
import wust.api.AuthUser.Real

object FeedbackForm {

  def apply(implicit ctx: Ctx.Owner) = {
    val showPopup = Var(false)
    val activeDisplay = Rx { display := (if (showPopup()) "block" else "none") }

    val feedbackText = Var("")
    val clear = Handler.unsafe[Unit]

    val initialStatus = ""
    val statusText = Var[VDomModifier](initialStatus)

    var timeout: Option[Int] = None
    def submit(): Unit = {
      if (feedbackText.now.trim.nonEmpty) {
        //         Client.api.feedback(ClientInfo(navigator.userAgent), feedbackText.now).onComplete { }
        Try{
          initCrisp()
          if (!GlobalState.crispIsLoaded.now) throw new Exception("Crisp chat not loaded. Is it blocked by a browser extension?")
          crisp.push(js.Array("do", "message:send", js.Array("text", feedbackText.now)))
        } match {
          case Success(()) =>
            statusText() = "Thank you!"
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
            clear.onNext(())
          case Failure(t) =>
            scribe.warn("Error sending feedback to backend", t)
            statusText() = span(s"Failed to send message:", br, b(t.getMessage()), br, br, "Please try again or send us an email... ")
            timeout.foreach(clearTimeout)
          // timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
        }
      }

      FeatureState.use(Feature.SubmitFeedback)
    }

    val feedbackForm = VDomModifier(
      div(
        fontSize.smaller,
        cls := "enable-text-selection",
        h3("How can we help you?"),
        div(
          cls := "ui form",
          textArea(
            cls := "field",
            onInput.value --> feedbackText,
            value <-- clear.map(_ => ""),
            rows := 5, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
            resize := "none",
            placeholder := "Questions? Missing features? Suggestions? Something is not working? What do you like? What is annoying?"
          )
        ),
      ),
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
      )
    )

    val toggleButton = div(
      "Feedback/Support ",
      // Rx {
      //   VDomModifier.ifTrue(GlobalState.crispIsLoaded()) {
      //     color := "blue"
      //   }
      // },
      // like semantic-ui tiny button
      fontSize := "0.85714286rem",
      fontWeight := 700,
      cursor.pointer,

      onClick.stopPropagation foreach {
        showPopup.update(!_)
      },
      onGlobalEscape.use(false) --> showPopup,
      onGlobalClick.use(false) --> showPopup,
    )

    div(
      keyed,
      toggleButton,
      div(
        activeDisplay,
        width := "280px",
        position.fixed, bottom := "35px", left := "20px",
        zIndex := ZIndex.formOverlay,
        padding := "10px",
        borderRadius := "5px",
        cls := "shadow",
        backgroundColor := Colors.sidebarBg,
        color := "#333",
        Rx {
          if (GlobalState.crispIsLoaded())
            feedbackForm
          else
            div(b("Crisp Chat"), " couldn't be started. It might be blocked by a browser extension.", marginBottom := "20px")
        },
        div("You can also write us an email: ", Components.woostEmailLink(prefix = "support"), "."),

        div(cls := "ui divider", marginTop := "30px"),
        supportChatButton(showPopup)(disabled <-- GlobalState.crispIsLoaded.map(!_)),
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

  def supportChatButton(showPopup: Var[Boolean]) = {
    button(
      freeSolid.faComments, " Open Support Chat",
      cls := "ui blue tiny fluid button",
      marginTop := "5px",
      onClick.stopPropagation.foreach { _ =>
        Try{
          DeployedOnly { initCrisp }
          crisp.push(js.Array("do", "chat:show"))
          crisp.push(js.Array("do", "chat:open"))
        }
        showPopup() = false
      }
    )
  }

  def voteOnFeaturesButton = button(
    freeSolid.faSort, " Vote on Features",
    cls := "ui violet tiny fluid button",
    marginTop := "5px",
    cls := "vote-button",
    snabbdom.VNodeProxy.repairDomBeforePatch, // nolt button modifies the dom
    DeployedOnly {
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
