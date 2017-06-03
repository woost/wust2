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

  def editLabel(graph: Graph, state: GlobalState, postId: PostId) = {
    div(
      Views.parents(state, postId, graph),
      Views.post(graph.postsById(postId)),
      "Edit Post", button("cancel", onclick := { (_: Event) => state.editedPostId() = None; inputfield.value = "" })
    )
  }

  def responseLabel(graph: Graph, state: GlobalState, postId: PostId) = {
    div(
      width := "10em",
      "Responding to:",
      Views.post(graph.postsById(postId)),
      Views.parents(state, postId, graph)
    )
  }

  val sendButton = input(`type` := "submit", value := "send")

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import state.{displayGraph => rxDisplayGraph, editedPostId => rxEditedPostId, mode => rxMode}

    rxMode.foreach { mode =>
      mode match {
        case EditMode(postId) => inputfield.value = rxDisplayGraph.now.graph.postsById(postId).title
        case _                =>
      }
      inputfield.focus()
    }

    def label(mode: InteractionMode, graph: Graph) = mode match {
      case EditMode(postId)  => editLabel(graph, state, postId)
      case FocusMode(postId) => responseLabel(graph, state, postId)
      case _                 => span()
    }

    def action(text: String, selection: GraphSelection, groupIdOpt: Option[GroupId], graph: Graph, mode: InteractionMode): Unit = mode match {
      case EditMode(postId) =>
        DevPrintln(s"\nUpdating Post $postId: $text")
        rxEditedPostId() = None
        state.persistence.addChanges(updatePosts = Set(graph.postsById(postId).copy(title = text)))
        sendEvent("post", "update", "api")
      case FocusMode(postId) =>
        DevPrintln(s"\nRepsonding to $postId: $text")
        state.persistence.addChanges(updatePosts = Set(graph.postsById(postId).copy(title = text)))
        val newPost = Post.newId(text)
        val containments = GraphSelection.toContainments(selection, newPost.id)
        val connection = Connection(newPost.id, postId)
        state.persistence.addChanges(addPosts = Set(newPost), addContainments = containments, addConnections = Set(connection))
        sendEvent("post", "respond", "api")
      case _ =>
        DevPrintln(s"\nCreating Post: $text")
        val newPost = Post.newId(text)
        val containments = GraphSelection.toContainments(selection, newPost.id)
        state.persistence.addChanges(addPosts = Set(newPost), addContainments = containments)
        sendEvent("post", "create", "api")
    }

    div(
      div(
        display.flex, justifyContent.spaceBetween,
        Rx { label(rxMode(), rxDisplayGraph().graph).render },
        form(
          width := "100%",
          display.flex,
          inputfield,
          sendButton,
          onsubmit := { () =>
            val text = inputfield.value
            if (text.trim.nonEmpty) {
              action(text, state.graphSelection.now, state.selectedGroupId.now, rxDisplayGraph.now.graph, rxMode.now)
              inputfield.value = ""
            }
            false
          }
        )
      )
    )
  }
}
