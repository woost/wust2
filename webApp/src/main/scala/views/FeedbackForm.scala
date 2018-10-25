package wust.webApp.views

import fontAwesome._
import googleAnalytics.Analytics
import org.scalajs.dom.window.{clearTimeout, setTimeout}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids
import wust.ids._
import wust.webApp.Icons
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, View}
import wust.webApp.views.Elements._

object FeedbackForm {

  val feedbackNodeId = NodeId(Cuid.fromBase58("15Wooooooooostfeedback"))

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val show = Var(false)
    val activeDisplay = Rx { display := (if(show()) "block" else "none") }

    val feedbackText = Var("")
    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val initialStatus = "(Press Enter to submit)"
    val statusText = Var(initialStatus)

    var timeout:Option[Int] = None
    def submit():Unit = {
      val newNode = Node.MarkdownMessage(feedbackText.now)
      state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(newNode, feedbackNodeId))
      statusText() = "Thank you!"
      timeout.foreach(clearTimeout)
      timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
            Analytics.sendEvent("feedback", "submit")
    }

    val feedbackForm = div(
      div(
        cls := "ui form",
        textArea(
          cls := "field",
          valueWithEnter foreach { submit() },
          onInput.value --> feedbackText,
          value <-- clear,
          width := "220px",
          rows := 5, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
          resize := "none",
          placeholder := "Missing features? Suggestions? You found a bug? What do you like? What is annoying?"
        )
      ),
      div(textAlign.right, fontSize.smaller, color := "#666", statusText),
    )

    div(
      button(
        "Feedback ",
        freeSolid.faCaretDown,
        cls := "ui positive tiny compact button",
        onClick.stopPropagation foreach{
          Analytics.sendEvent("feedback", if(show.now) "close" else "open")
          show.update(!_)
        },
        onGlobalEscape(false) --> show,
        onGlobalClick(false) --> show,
      ),
      div(
        activeDisplay,
        position.fixed, top := "35px", right := "100px",
        zIndex := ZIndex.overlay,
        padding := "10px", background := "#F8F8F8", border := "1px solid #888",
        div(
          Styles.flex,
          justifyContent.spaceBetween,
          alignItems.center,

          color := "#666",
        ),
        feedbackForm,
        div(
          marginTop := "20px",
          Styles.flex,
          justifyContent.spaceBetween,
          button(
            Styles.flexStatic,
            tpe := "button",
            cls := "ui tiny compact button",
            "Show all Feedback",
            (Icons.zoom:VNode)(marginLeft := "5px"),
            onClick foreach {
              val nextPage = Page(feedbackNodeId)
              if (state.view.now.isContent) state.page() = nextPage
              else state.viewConfig.update(_.copy(page = nextPage, view = View.default))
              show() = false
              Analytics.sendEvent("feedback", "show")
            }
          ),
          button(
            Styles.flexStatic,
            tpe := "button",
            cls := "ui tiny compact primary button",
            "Submit",
            onClick foreach { submit(); clear.onNext(Unit); () },
            onClick(false) --> show,
          ),
        ),
        onClick.stopPropagation foreach{}, // prevents closing feedback form by global click
      )
    )
  }

}
