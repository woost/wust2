package wust.frontend.views

import autowire._
import boopickle.Default._
import org.scalajs.dom
import org.scalajs.dom.{Event, document}
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}
import scala.scalajs.js.timers.setTimeout
import rx._
import wust.frontend._
import wust.ids._
import wust.api._
import wust.graph._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try
import scalatags.JsDom.all._
import scalatags.rx.all._
import collection.breakOut
import wust.util.EventTracker.sendEvent

object AddPostForm {
  val inputfield = Elements.textareaWithEnterSubmit(rows := 3, cols := 80, width := "100%").render

  val sendButton = input(`type` := "submit", value := "send")

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import state.{displayGraph => rxDisplayGraph}

    def action(text: String, selection: GraphSelection, groupIdOpt: Option[GroupId], graph: Graph): Unit = {
      DevPrintln(s"\nCreating Post: $text")
      val newPost = Post.newId(text)
      val containments = GraphSelection.toContainments(selection, newPost.id)
      state.persistence.addChanges(addPosts = Set(newPost), addContainments = containments)
      sendEvent("post", "create", "api")
    }

    div(
      div(
        display.flex, justifyContent.spaceBetween,
        form(
          width := "100%",
          display.flex,
          inputfield,
          sendButton,
          onsubmit := { () =>
            val text = inputfield.value
            if (text.trim.nonEmpty) {
              action(text, state.graphSelection.now, state.selectedGroupId.now, rxDisplayGraph.now.graph)
              inputfield.value = ""
            }
            false
          }
        )
      )
    )
  }
}
