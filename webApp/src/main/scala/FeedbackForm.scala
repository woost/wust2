package wust.webApp

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import org.scalajs.dom.window.{clearTimeout, setTimeout}

object FeedbackForm {

  val feedbackNodeId = NodeId(Cuid.fromBase58("15Wooooooooostfeedback"))

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val show = Var(false)
    val activeDisplay = Rx { display := (if(show()) "block" else "none") }

    val initialStatus = "(Press Enter to submit)"
    val statusText = Var(initialStatus)

    var timeout:Option[Int] = None
    val feedbackForm = div(
      div(
        cls := "ui form",
        textArea(
          cls := "field",
          valueWithEnter --> sideEffect { str =>
            val newNode = Node.Content(new ids.NodeData.Markdown(str))
            state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(newNode, feedbackNodeId))
            statusText() = "Thank you!"
            timeout.foreach(clearTimeout)
            timeout = Some(setTimeout(() => statusText() = initialStatus, 3000))
          },
          width := "200px",
          rows := 5, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
          style("resize") := "none", //TODO: add resize style to scala-dom-types
          placeholder := "Missing features? Suggestions? You found a bug? What do you like? What is annoying?"
        )
      ),
      div(textAlign.right, fontSize.smaller, color := "#666", statusText)
    )

    div(
      button(
        "Feedback ",
        freeSolid.faCaretDown,
        cls := "ui positive tiny compact button",
        onClick --> sideEffect{show.update(!_)},
        onGlobalEscape(false) --> show
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
          textAlign.center,
          button(
            tpe := "button",
            cls := "ui tiny compact button",
            "Show all Feedback",
            (freeRegular.faArrowAltCircleRight:VNode)(marginLeft := "5px"),
            onClick(Page(feedbackNodeId)) --> state.page.toSink,
            onClick(false) --> show
          )
        )
      )
    )
  }

}
