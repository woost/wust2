package wust.frontend.views

import wust.frontend.Color._
import org.scalajs.d3v4._
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.Pipe
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Tag
import scala.math.Ordering

import org.scalajs.dom.{ window, document, console }
import org.scalajs.dom.raw.{ Text, Element, HTMLElement }
import org.scalajs.dom.{ Event }
import org.scalajs.dom.raw.{ HTMLTextAreaElement }
import Elements.{ inlineTextarea, textareaWithEnter }
import scalatags.JsDom.all._
import scala.scalajs.js
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{ Event, KeyboardEvent }
import scala.util.control.NonFatal

object ChatView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {

    val focusedParentIds = state.graphSelection.map(_.parentIds)

    val headLineText = Rx {
      val parents = focusedParentIds().map(state.rawGraph().postsById)
      val parentTitles = parents.map(_.title).mkString(", ")
      parentTitles

    }

    val bgColor = Rx {
      val mixedDirectParentColors = mixColors(focusedParentIds().map(baseColor))
      mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString
    }

    val chatHistory = state.displayGraphWithoutParents.map{ dg =>
      val graph = dg.graph
      graph.posts.toSeq.sortBy(p => Tag.unwrap(p.id))
    }

    val latestPost = Rx { chatHistory().lastOption }

    val chatHistoryDiv = div(
      height := "100%",
      overflow := "auto",
      padding := "20px",
      backgroundColor := "white",

      Rx {
        val w = "60%"
        div(
          chatHistory().map { post =>
            val time = state.postTimes.get(post.id).getOrElse(0)
            val isMine = state.ownPosts(post.id)
            div(
              p(
                maxWidth := w,
                post.title,
                backgroundColor := (if (isMine) "rgb(192, 232, 255)" else "#EEE"),
                float := (if (isMine) "right" else "left"),
                clear.both,
                padding := "5px 10px",
                borderRadius := "7px",
                margin := "5px 0px"
              )
            )
          }
        ).render
      }
    ).render

    def scrollToBottom() {
      //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
      try {
        chatHistoryDiv.scrollTop = chatHistoryDiv.scrollHeight
      } catch { case _:Throwable => } // with NonFatal(_) it fails in the tests
    }

    setTimeout(200) {
      // initial scroll to bottom
      scrollToBottom()
    }

    chatHistory.foreach { _ =>
      // scroll to bottom for each update
      scrollToBottom()
    }

    def submitInsert(field: HTMLTextAreaElement) = {
      val newPost = Post.newId(field.value)
      state.persistence.addChangesEnriched(
        addPosts = Set(newPost),
        addConnections = latestPost.now.map(latest => Connection(latest.id, newPost.id)).toSet
      )
      field.value = ""
      false
    }
    val insertField: HTMLTextAreaElement = textareaWithEnter(submitInsert)(placeholder := "Create new post", width := "100%").render
    val insertForm = form(
      insertField,
      // input(tpe := "submit", "insert"),
      onsubmit := { (e: Event) =>
        submitInsert(insertField)
        e.preventDefault()
      }
    ).render

    div(
      height := "100%",
      backgroundColor := bgColor,

      div(
        margin := "0 auto",
        maxWidth := "48rem",
        width := "48rem",
        height := "100%",

        display.flex,
        flexDirection.column,
        justifyContent.flexStart,
        alignItems.stretch,
        alignContent.stretch,

        h1(headLineText),

        chatHistoryDiv,
        insertForm
      )
    )
  }
}
