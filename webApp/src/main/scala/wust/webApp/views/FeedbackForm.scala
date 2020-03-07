package wust.webApp.views

import fontAwesome._
import org.scalajs.dom
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import outwatch._
import outwatch.dsl._
import outwatch.reactive.handler._
import colibri.ext.rx._
import rx._
import wust.api.AuthUser.{Implicit, Real}
import wust.css.{Styles, ZIndex}
import wust.facades.crisp._
import wust.facades.nolt.{NoltData, nolt}
import wust.ids.Feature
import wust.sdk.Colors
import wust.webApp.{Client, DeployedOnly}
import wust.webApp.state.{FeatureState, GlobalState}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

import scala.scalajs.js
import scala.util.{Failure, Success, Try}
import wust.facades.segment.Segment

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
          onClickDefault foreach { submit() },
        // onClick(false) --> showPopup,
        ),
      )
    )

    val toggleButton = div(
      "Feedback/Support ",
      // like semantic-ui tiny button
      fontSize := "0.85714286rem",
      fontWeight := 700,
      cursor.pointer,

      onClickDefault foreach {
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
          VDomModifier.ifTrue(GlobalState.crispIsLoaded())(
            feedbackForm
          )
        },
        div(
          supportChatButton.apply(cls := "fluid tiny", marginTop := "5px"),
          onClickDefault.foreach {
            showPopup() = false
          }
        ),
        voteOnFeaturesButton,
        div("You can also send us an e-mail: ", Components.woostEmailLink(prefix = "support"), margin := "20px 2px"),
        onClick.stopPropagation.discard, // prevents closing feedback form by global click
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

  def openCrispChat(): Unit = {
    Try{
      Segment.trackEvent("Open Support Chat", js.Dynamic.literal(loaded = GlobalState.crispIsLoaded.now))
      if(GlobalState.crispIsLoaded.now) {
        DeployedOnly { initCrisp }
        crisp.push(js.Array("do", "chat:show"))
        crisp.push(js.Array("do", "chat:open"))
      } else {
        val username = GlobalState.user.now.name
        dom.window.open(s"https://go.crisp.chat/chat/embed/?website_id=5ab5c700-31d3-490f-917c-83dfa10b8205&user_nickname=$username", "_blank")
      }
    }
  }

  val supportChatButton = {
    button(
      span(freeSolid.faComments, marginRight := "0.5em"), "Open Support Chat",
      cls := "ui blue button",
      onClickDefault.foreach { _ =>
        openCrispChat()
      }
    )
  }

  def voteOnFeaturesButton = button(
    span(freeSolid.faSort, marginRight := "0.5em"), " Vote on Features",
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
    },
    onMouseDown.stopPropagation.discard, // prevent rightsidebar from closing
  )

}
